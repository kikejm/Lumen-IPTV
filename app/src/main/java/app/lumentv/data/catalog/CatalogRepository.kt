package app.lumentv.data.catalog

import app.lumentv.data.db.CategoryEntity
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.LumenDatabase
import app.lumentv.data.db.MovieEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.db.SeriesEntity
import app.lumentv.data.m3u.M3uParser
import app.lumentv.data.playlist.PlaylistRepository
import app.lumentv.data.xtream.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import app.lumentv.core.net.withDownloadedFile
import java.io.File

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val step: String, val count: Int = 0) : SyncState
    data class Done(val at: Long) : SyncState
    data class Failed(val message: String) : SyncState
}

/** Item genérico para la búsqueda maestra. */
data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/**
 * Descarga el catálogo (canales, películas, series y sus categorías) y lo guarda en Room.
 * Room es la única fuente de verdad para la UI: las pantallas observan Flows de los DAOs.
 */
class CatalogRepository(
    private val db: LumenDatabase,
    private val xtream: XtreamClient,
    private val playlists: PlaylistRepository,
    private val http: OkHttpClient,
    private val cacheDir: File,
) {
    private val dao = db.catalog()
    private val mutex = Mutex()
    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _sync.asStateFlow()

    fun categories(pid: Long, kind: ContentKind): Flow<List<CategoryEntity>> = dao.observeCategories(pid, kind)
    fun channels(pid: Long, categoryId: String?): Flow<List<ChannelEntity>> =
        if (categoryId == null) dao.observeAllChannels(pid) else dao.observeChannels(pid, categoryId)
    fun movies(pid: Long, categoryId: String?): Flow<List<MovieEntity>> =
        if (categoryId == null) dao.observeAllMovies(pid) else dao.observeMovies(pid, categoryId)
    fun series(pid: Long, categoryId: String?): Flow<List<SeriesEntity>> =
        if (categoryId == null) dao.observeAllSeries(pid) else dao.observeSeries(pid, categoryId)
    fun archiveChannels(pid: Long) = dao.observeArchiveChannels(pid)
    fun channelCount(pid: Long) = dao.observeChannelCount(pid)
    fun movieCount(pid: Long) = dao.observeMovieCount(pid)
    fun seriesCount(pid: Long) = dao.observeSeriesCount(pid)

    suspend fun channel(pid: Long, id: String) = dao.channel(pid, id)
    suspend fun channelsByIds(pid: Long, ids: List<String>) = dao.channelsByIds(pid, ids)
    suspend fun channelsIn(pid: Long, categoryId: String?) =
        if (categoryId == null) dao.allChannels(pid) else dao.channels(pid, categoryId)
    suspend fun movie(pid: Long, id: String) = dao.movie(pid, id)
    suspend fun moviesByIds(pid: Long, ids: List<String>) = dao.moviesByIds(pid, ids)
    suspend fun seriesById(pid: Long, id: String) = dao.series(pid, id)
    suspend fun seriesByIds(pid: Long, ids: List<String>) = dao.seriesByIds(pid, ids)

    suspend fun search(pid: Long, query: String, limit: Int = 40): SearchResults {
        val q = query.trim()
        if (q.length < 2) return SearchResults()
        return SearchResults(
            channels = dao.searchChannels(pid, q, limit),
            movies = dao.searchMovies(pid, q, limit),
            series = dao.searchSeries(pid, q, limit),
        )
    }

    fun needsSync(p: PlaylistEntity, maxAgeHours: Int): Boolean =
        p.lastSyncAt == 0L || (maxAgeHours > 0 && System.currentTimeMillis() - p.lastSyncAt > maxAgeHours * 3_600_000L)

    suspend fun sync(p: PlaylistEntity) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                when (p.type) {
                    PlaylistType.XTREAM -> syncXtream(p)
                    PlaylistType.M3U -> syncM3u(p)
                }
                val now = System.currentTimeMillis()
                playlists.byId(p.id)?.let { playlists.update(it.copy(lastSyncAt = now)) }
                _sync.value = SyncState.Done(now)
            } catch (e: Exception) {
                _sync.value = SyncState.Failed(e.message ?: e.javaClass.simpleName)
                throw e
            }
        }
    }

    private suspend fun syncXtream(p: PlaylistEntity) {
        val c = playlists.credentials(p)
        val pid = p.id

        _sync.value = SyncState.Running("Categorías")
        val kinds = listOf(
            ContentKind.LIVE to "get_live_categories",
            ContentKind.VOD to "get_vod_categories",
            ContentKind.SERIES to "get_series_categories",
        )
        for ((kind, action) in kinds) {
            val cats = xtream.categories(c, action).mapIndexedNotNull { i, m ->
                val id = m["category_id"] ?: return@mapIndexedNotNull null
                CategoryEntity(pid, kind, id, m["category_name"].orEmpty().trim(), i)
            }
            dao.clearCategories(pid, kind)
            dao.insertCategories(cats)
        }

        _sync.value = SyncState.Running("Canales")
        dao.clearChannels(pid)
        val channels = ArrayList<ChannelEntity>(BATCH)
        var order = 0
        xtream.streamEach(xtream.apiUrl(c, "get_live_streams")) { m ->
            val id = m["stream_id"] ?: return@streamEach
            channels += ChannelEntity(
                playlistId = pid,
                streamId = id,
                num = m["num"]?.toIntOrNull() ?: order,
                name = m["name"].orEmpty().trim(),
                logo = m["stream_icon"].orEmpty(),
                epgChannelId = m["epg_channel_id"].orEmpty(),
                categoryId = m["category_id"].orEmpty(),
                tvArchive = m["tv_archive"] == "1",
                archiveDays = m["tv_archive_duration"]?.toIntOrNull() ?: 0,
                sortOrder = order++,
            )
            if (channels.size >= BATCH) {
                dao.insertChannels(channels.toList()); channels.clear()
                _sync.value = SyncState.Running("Canales", order)
            }
        }
        if (channels.isNotEmpty()) dao.insertChannels(channels)

        _sync.value = SyncState.Running("Películas")
        dao.clearMovies(pid)
        val movies = ArrayList<MovieEntity>(BATCH)
        order = 0
        xtream.streamEach(xtream.apiUrl(c, "get_vod_streams")) { m ->
            val id = m["stream_id"] ?: return@streamEach
            movies += MovieEntity(
                playlistId = pid,
                streamId = id,
                name = m["name"].orEmpty().trim(),
                poster = m["stream_icon"].orEmpty(),
                rating = m["rating"].orEmpty(),
                categoryId = m["category_id"].orEmpty(),
                containerExt = m["container_extension"].orEmpty().ifEmpty { "mp4" },
                addedAt = m["added"]?.toLongOrNull()?.times(1000) ?: 0,
                sortOrder = order++,
            )
            if (movies.size >= BATCH) {
                dao.insertMovies(movies.toList()); movies.clear()
                _sync.value = SyncState.Running("Películas", order)
            }
        }
        if (movies.isNotEmpty()) dao.insertMovies(movies)

        _sync.value = SyncState.Running("Series")
        dao.clearSeries(pid)
        val series = ArrayList<SeriesEntity>(BATCH)
        order = 0
        xtream.streamEach(xtream.apiUrl(c, "get_series")) { m ->
            val id = m["series_id"] ?: return@streamEach
            series += SeriesEntity(
                playlistId = pid,
                seriesId = id,
                name = m["name"].orEmpty().trim(),
                cover = m["cover"].orEmpty(),
                plot = m["plot"].orEmpty(),
                genre = m["genre"].orEmpty(),
                releaseDate = m["releaseDate"] ?: m["release_date"].orEmpty(),
                rating = m["rating"].orEmpty(),
                backdrop = m["backdrop_path"].orEmpty(),
                categoryId = m["category_id"].orEmpty(),
                sortOrder = order++,
            )
            if (series.size >= BATCH) {
                dao.insertSeries(series.toList()); series.clear()
                _sync.value = SyncState.Running("Series", order)
            }
        }
        if (series.isNotEmpty()) dao.insertSeries(series)

        runCatching { playlists.refreshAccount(playlists.byId(pid) ?: p) }
    }

    private suspend fun syncM3u(p: PlaylistEntity) {
        val pid = p.id
        _sync.value = SyncState.Running("Descargando lista")
        http.withDownloadedFile(p.m3uUrl, cacheDir, "m3u-") { file ->
            val reader = file.bufferedReader()

            dao.clearChannels(pid)
            dao.clearMovies(pid)
            dao.clearSeries(pid)
            val liveCats = LinkedHashMap<String, String>()
            val vodCats = LinkedHashMap<String, String>()
            val channels = ArrayList<ChannelEntity>(BATCH)
            val movies = ArrayList<MovieEntity>(BATCH)
            var liveOrder = 0
            var vodOrder = 0

            M3uParser.parse(reader) { e ->
                if (e.isMovie || e.isSeries) {
                    val catId = vodCats.getOrPut(e.group) { "m3u-vod-${vodCats.size}" }
                    movies += MovieEntity(
                        playlistId = pid,
                        streamId = "m3u-v-$vodOrder",
                        name = e.name,
                        poster = e.logo,
                        rating = "",
                        categoryId = catId,
                        containerExt = e.url.substringBefore('?').substringAfterLast('.', "mp4"),
                        addedAt = 0,
                        directUrl = e.url,
                        sortOrder = vodOrder++,
                    )
                } else {
                    val catId = liveCats.getOrPut(e.group) { "m3u-live-${liveCats.size}" }
                    channels += ChannelEntity(
                        playlistId = pid,
                        streamId = "m3u-l-$liveOrder",
                        num = liveOrder + 1,
                        name = e.name,
                        logo = e.logo,
                        epgChannelId = e.tvgId,
                        categoryId = catId,
                        tvArchive = false,
                        archiveDays = e.catchupDays,
                        directUrl = e.url,
                        sortOrder = liveOrder++,
                    )
                }
                if (channels.size >= BATCH) { dao.insertChannels(channels.toList()); channels.clear() }
                if (movies.size >= BATCH) { dao.insertMovies(movies.toList()); movies.clear() }
                _sync.value = SyncState.Running("Procesando lista", liveOrder + vodOrder)
            }
            if (channels.isNotEmpty()) dao.insertChannels(channels)
            if (movies.isNotEmpty()) dao.insertMovies(movies)

            dao.clearCategories(pid, ContentKind.LIVE)
            dao.insertCategories(liveCats.entries.mapIndexed { i, (name, id) -> CategoryEntity(pid, ContentKind.LIVE, id, name, i) })
            dao.clearCategories(pid, ContentKind.VOD)
            dao.insertCategories(vodCats.entries.mapIndexed { i, (name, id) -> CategoryEntity(pid, ContentKind.VOD, id, name, i) })
        }
    }

    private companion object {
        const val BATCH = 500
    }
}
