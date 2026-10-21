package app.lumentv.data.epg

import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.LumenDatabase
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.db.ProgramEntity
import app.lumentv.data.playlist.PlaylistRepository
import app.lumentv.data.settings.SettingsRepository
import app.lumentv.data.xtream.XtreamClient
import app.lumentv.data.xtream.XtreamEpgEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import app.lumentv.core.net.withDownloadedFile
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class Program(val title: String, val description: String, val startAt: Long, val stopAt: Long, val hasArchive: Boolean = false) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in startAt until stopAt
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (stopAt <= startAt) 0f else ((now - startAt).toFloat() / (stopAt - startAt)).coerceIn(0f, 1f)
}

sealed interface EpgSyncState {
    data object Idle : EpgSyncState
    data class Running(val source: String, val programs: Int) : EpgSyncState
    data class Done(val programs: Int) : EpgSyncState
    data class Failed(val message: String) : EpgSyncState
}

/**
 * Guía de programación. Fuentes, por orden:
 *  1. XMLTV del propio proveedor (`xmltv.php` en Xtream o la URL EPG de la lista M3U).
 *  2. Fuentes XMLTV externas configuradas en Ajustes (función "premium" de Smarters).
 *  3. Fallback bajo demanda: `get_short_epg` de Xtream, cacheado en memoria 10 min.
 */
class EpgRepository(
    private val db: LumenDatabase,
    private val http: OkHttpClient,
    private val xtream: XtreamClient,
    private val playlists: PlaylistRepository,
    private val settings: SettingsRepository,
    private val cacheDir: File,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<EpgSyncState>(EpgSyncState.Idle)
    val state: StateFlow<EpgSyncState> = _state.asStateFlow()

    private data class CacheEntry(val at: Long, val programs: List<Program>)
    private val shortCache = ConcurrentHashMap<String, CacheEntry>()

    fun needsSync(p: PlaylistEntity): Boolean = System.currentTimeMillis() - p.lastEpgSyncAt > 12 * 3_600_000L

    suspend fun sync(p: PlaylistEntity) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val sources = buildList {
                when {
                    p.epgUrl.isNotBlank() -> add(p.epgUrl)
                    p.type == PlaylistType.XTREAM -> add(xtream.xmltvUrl(playlists.credentials(p)))
                }
                addAll(settings.load().externalEpgUrls)
            }.distinct()
            if (sources.isEmpty()) return@withContext

            val keys = db.catalog().epgChannelKeys(p.id).toHashSet()
            if (keys.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            val windowStart = now - 6 * HOUR
            val windowEnd = now + 48 * HOUR
            val seen = HashSet<String>() // evita duplicados entre fuentes: canal+inicio
            var total = 0
            var firstError: String? = null

            db.programs().clear(p.id)
            for (url in sources) {
                _state.value = EpgSyncState.Running(url.substringAfter("://").substringBefore('/'), total)
                try {
                    val batch = ArrayList<ProgramEntity>(BATCH)
                    http.withDownloadedFile(url, cacheDir, "epg-") { file ->
                        XmltvParser.parse(file.inputStream(), { it in keys }, windowStart, windowEnd) { prog ->
                            if (seen.add(prog.channel + prog.start)) {
                                batch += ProgramEntity(
                                    playlistId = p.id,
                                    channelKey = prog.channel,
                                    startAt = prog.start,
                                    stopAt = prog.stop,
                                    title = prog.title,
                                    description = prog.description,
                                )
                                total++
                                if (batch.size >= BATCH) {
                                    db.programs().insert(batch.toList()); batch.clear()
                                    _state.value = EpgSyncState.Running(url.substringAfter("://").substringBefore('/'), total)
                                }
                            }
                        }
                    }
                    if (batch.isNotEmpty()) db.programs().insert(batch)
                } catch (e: Exception) {
                    if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                }
            }
            playlists.byId(p.id)?.let { playlists.update(it.copy(lastEpgSyncAt = System.currentTimeMillis())) }
            _state.value = if (total == 0 && firstError != null) EpgSyncState.Failed(firstError!!) else EpgSyncState.Done(total)
        }
    }

    /** Programas de un canal en una ventana temporal (DB, o short EPG si no hay datos). */
    suspend fun programs(p: PlaylistEntity, channel: ChannelEntity, from: Long, to: Long): List<Program> {
        val key = channel.epgChannelId.lowercase(Locale.ROOT)
        if (key.isNotEmpty()) {
            val stored = db.programs().programs(p.id, key, from, to)
            if (stored.isNotEmpty()) return stored.map { Program(it.title, it.description, it.startAt, it.stopAt) }
        }
        if (p.type != PlaylistType.XTREAM) return emptyList()
        return shortEpg(p, channel).filter { it.stopAt > from && it.startAt < to }
    }

    /** Programa actual y siguientes (para listas de canales y overlay del reproductor). */
    suspend fun nowAndNext(p: PlaylistEntity, channel: ChannelEntity, count: Int = 4): List<Program> {
        val now = System.currentTimeMillis()
        return programs(p, channel, now, now + 12 * HOUR).take(count)
    }

    /** Programación pasada con archivo disponible para catch-up. */
    suspend fun archive(p: PlaylistEntity, channel: ChannelEntity): List<Program> = withContext(Dispatchers.IO) {
        if (p.type != PlaylistType.XTREAM) return@withContext emptyList()
        val now = System.currentTimeMillis()
        runCatching { xtream.archiveEpg(playlists.credentials(p), channel.streamId) }
            .getOrDefault(emptyList())
            .filter { it.stopAt < now && (it.hasArchive || channel.tvArchive) }
            .map { it.toProgram() }
            .sortedByDescending { it.startAt }
    }

    private suspend fun shortEpg(p: PlaylistEntity, channel: ChannelEntity): List<Program> {
        val cacheKey = "${p.id}:${channel.streamId}"
        shortCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.at < 10 * 60_000 }?.let { return it.programs }
        val list = runCatching { xtream.shortEpg(playlists.credentials(p), channel.streamId, 8) }
            .getOrDefault(emptyList())
            .map { it.toProgram() }
        shortCache[cacheKey] = CacheEntry(System.currentTimeMillis(), list)
        return list
    }

    private fun XtreamEpgEntry.toProgram() = Program(title, description, startAt, stopAt, hasArchive)

    private companion object {
        const val HOUR = 3_600_000L
        const val BATCH = 1000
    }
}
