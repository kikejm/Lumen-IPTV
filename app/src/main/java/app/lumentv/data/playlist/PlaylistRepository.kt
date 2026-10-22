package app.lumentv.data.playlist

import app.lumentv.core.crypto.SecretBox
import app.lumentv.data.db.LumenDatabase
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.settings.SettingsRepository
import app.lumentv.data.xtream.XtreamClient
import app.lumentv.data.xtream.XtreamCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Gestiona las listas IPTV del usuario (Xtream Codes o M3U) y cuál está activa. */
class PlaylistRepository(
    private val db: LumenDatabase,
    private val secretBox: SecretBox,
    private val settings: SettingsRepository,
    private val xtream: XtreamClient,
) {
    val playlists: Flow<List<PlaylistEntity>> = db.playlists().observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    val active: Flow<PlaylistEntity?> = settings.settings
        .map { it.activePlaylistId }
        .distinctUntilChanged()
        .flatMapLatest { id -> db.playlists().observe(id) }

    suspend fun activeNow(): PlaylistEntity? {
        val id = settings.load().activePlaylistId
        return db.playlists().byId(id) ?: db.playlists().all().firstOrNull()?.also { settings.setActivePlaylist(it.id) }
    }

    suspend fun byId(id: Long) = db.playlists().byId(id)

    fun credentials(p: PlaylistEntity): XtreamCredentials =
        XtreamCredentials(p.serverUrl, p.username, secretBox.decrypt(p.passwordEnc))

    /** Valida las credenciales contra el servidor y guarda la lista. Devuelve su id. */
    suspend fun saveXtream(name: String, server: String, username: String, password: String, existingId: Long? = null): Long {
        val creds = XtreamCredentials(server, username.trim(), password)
        val account = xtream.authenticate(creds)
        if (!account.authenticated) throw IOException(account.message.ifBlank { "Usuario o contraseña incorrectos" })
        val base = existingId?.let { db.playlists().byId(it) }
        val entity = PlaylistEntity(
            id = base?.id ?: 0,
            name = name.ifBlank { creds.baseUrl.substringAfter("://").substringBefore(':') },
            type = PlaylistType.XTREAM,
            serverUrl = creds.baseUrl,
            username = creds.username,
            passwordEnc = secretBox.encrypt(password),
            epgUrl = base?.epgUrl.orEmpty(),
            serverTimezone = account.serverTimezone,
            expiresAt = account.expiresAt,
            maxConnections = account.maxConnections,
            status = account.status,
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
        )
        return persist(entity, base != null)
    }

    suspend fun saveM3u(name: String, url: String, epgUrl: String, existingId: Long? = null): Long {
        require(url.startsWith("http", ignoreCase = true)) { "La URL M3U debe empezar por http(s)://" }
        // Un get.php de Xtream da mucho más como Xtream (fichas, series, EPG): se guarda así.
        XtreamCredentials.fromM3uUrl(url)?.let { c ->
            val id = saveXtream(name, c.server, c.username, c.password, existingId)
            if (epgUrl.isNotBlank()) db.playlists().byId(id)?.let { setEpgUrl(it, epgUrl) }
            return id
        }
        val base = existingId?.let { db.playlists().byId(it) }
        val entity = PlaylistEntity(
            id = base?.id ?: 0,
            name = name.ifBlank { "Lista M3U" },
            type = PlaylistType.M3U,
            m3uUrl = url.trim(),
            epgUrl = epgUrl.trim(),
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
        )
        return persist(entity, base != null)
    }

    /**
     * Convierte en Xtream las listas M3U guardadas con un enlace get.php (ver [saveM3u]) sin cambiar
     * la lista activa. Devuelve los ids convertidos, que necesitan sincronizarse de nuevo.
     */
    suspend fun upgradeXtreamM3uLists(): List<Long> {
        val m3u = db.playlists().all().filter { it.type == PlaylistType.M3U }
        val candidates = m3u.mapNotNull { p -> XtreamCredentials.fromM3uUrl(p.m3uUrl)?.let { p to it } }
        if (candidates.isEmpty()) return emptyList()
        val active = settings.load().activePlaylistId
        val upgraded = candidates.mapNotNull { (p, c) ->
            runCatching { saveXtream(p.name, c.server, c.username, c.password, existingId = p.id) }.getOrNull()
        }
        settings.setActivePlaylist(active)
        return upgraded
    }

    /** Refresca estado de la cuenta (caducidad, conexiones) sin tocar el catálogo. */
    suspend fun refreshAccount(p: PlaylistEntity): PlaylistEntity {
        if (p.type != PlaylistType.XTREAM) return p
        val account = xtream.authenticate(credentials(p))
        val updated = p.copy(
            expiresAt = account.expiresAt,
            maxConnections = account.maxConnections,
            status = account.status,
            serverTimezone = account.serverTimezone.ifBlank { p.serverTimezone },
        )
        db.playlists().update(updated)
        return updated
    }

    suspend fun update(p: PlaylistEntity) = db.playlists().update(p)

    suspend fun setEpgUrl(p: PlaylistEntity, url: String) = db.playlists().update(p.copy(epgUrl = url.trim()))

    suspend fun delete(id: Long) {
        db.catalog().clearAllCategories(id)
        db.catalog().clearChannels(id)
        db.catalog().clearMovies(id)
        db.catalog().clearSeries(id)
        db.programs().clear(id)
        db.library().clearHistory(id)
        db.playlists().delete(id)
        if (settings.load().activePlaylistId == id) {
            settings.setActivePlaylist(db.playlists().all().firstOrNull()?.id ?: -1)
        }
    }

    suspend fun setActive(id: Long) = settings.setActivePlaylist(id)

    private suspend fun persist(entity: PlaylistEntity, isUpdate: Boolean): Long {
        val id = if (isUpdate) {
            db.playlists().update(entity); entity.id
        } else {
            db.playlists().insert(entity)
        }
        setActive(id)
        return id
    }
}
