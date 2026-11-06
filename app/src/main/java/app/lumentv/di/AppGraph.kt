package app.lumentv.di

import android.app.Application
import android.util.Log
import app.lumentv.BuildConfig
import app.lumentv.core.crypto.SecretBox
import app.lumentv.core.net.isMeteredNetwork
import app.lumentv.core.util.countryName
import app.lumentv.data.catalog.CatalogRepository
import app.lumentv.data.db.LumenDatabase
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.epg.EpgRepository
import app.lumentv.data.library.LibraryRepository
import app.lumentv.data.playlist.PlaylistRepository
import app.lumentv.data.settings.ParentalControl
import app.lumentv.data.settings.SettingsRepository
import app.lumentv.data.xtream.XtreamClient
import app.lumentv.player.PlaybackSession
import app.lumentv.player.PlayerFactory
import app.lumentv.player.StreamResolver
import app.lumentv.remote.ImportHandler
import app.lumentv.remote.ImportServer
import app.lumentv.tools.SpeedTest
import app.lumentv.update.UpdateInfo
import app.lumentv.update.UpdateManager
import app.lumentv.vpn.ProtonConfigParser
import app.lumentv.vpn.VpnManager
import app.lumentv.vpn.VpnServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Inyección de dependencias manual: un único grafo creado en [app.lumentv.LumenApp].
 * Sin Hilt/Koin a propósito: la app es pequeña y así el arranque en el Fire Stick es más rápido.
 */
class AppGraph(val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val settings = SettingsRepository(app, appScope)
    val secretBox = SecretBox()
    val db = LumenDatabase.build(app)

    /** Cliente HTTP compartido; el User-Agent sale de Ajustes en cada petición. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request()
            val withUa = if (req.header("User-Agent") == null) {
                req.newBuilder().header("User-Agent", settings.current.userAgent).build()
            } else req
            chain.proceed(withUa)
        }
        .build()

    val xtream = XtreamClient(http, json, app.cacheDir)
    val playlists = PlaylistRepository(db, secretBox, settings, xtream)
    val catalog = CatalogRepository(db, xtream, playlists, http, app.cacheDir)
    val epg = EpgRepository(db, http, xtream, playlists, settings, app.cacheDir)
    val library = LibraryRepository(db)
    val parental = ParentalControl(settings)

    val playback = PlaybackSession()
    val streams = StreamResolver(playlists, xtream, settings)
    val players = PlayerFactory(app, http, settings)

    val vpnServers = VpnServerRepository(app, db, secretBox, http)
    val vpn = VpnManager(app, vpnServers, settings, appScope, http, json)

    val updater = UpdateManager(app, http, json)
    val speedTest = SpeedTest(http)
    val importServer = ImportServer(appScope, json)

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    /** Lo que hace el servidor de importación cuando el móvil envía datos. */
    val importHandler = object : ImportHandler {
        override suspend fun onVpnConfigs(files: List<Pair<String?, String>>): String {
            val result = vpnServers.importMany(files.flatMap { (n, t) -> VpnServerRepository.splitBundle(n, t) }, "phone")
            // Resumen corto: con 20 servidores, la lista nombre a nombre no cabe en la tele.
            return buildString {
                val n = result.imported.size
                if (n > 0) {
                    val countries = result.imported.map { countryName(ProtonConfigParser.countryOf(it)) }.distinct()
                    append(if (n == 1) "1 servidor importado" else "$n servidores importados")
                    append(if (countries.size == 1) " de ${countries[0]}" else " de ${countries.size} países: ${countries.joinToString()}")
                }
                if (result.errors.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    val shown = result.errors.take(2).joinToString()
                    append("${result.errors.size} con error: $shown")
                    if (result.errors.size > 2) append("…")
                }
                if (result.imported.isEmpty() && result.errors.isNotEmpty()) throw IllegalArgumentException(toString())
            }
        }

        override suspend fun onXtream(name: String, server: String, username: String, password: String): String {
            val id = playlists.saveXtream(name, server, username, password)
            appScope.launch { syncPlaylist(id) }
            return "Lista guardada. Cargando canales en la tele…"
        }

        override suspend fun onM3u(name: String, url: String, epgUrl: String): String {
            val id = playlists.saveM3u(name, url, epgUrl)
            appScope.launch { syncPlaylist(id) }
            return if (playlists.byId(id)?.type == PlaylistType.XTREAM) {
                "Es un enlace de Xtream Codes: guardada como Xtream (con fichas, series y guía). Cargando canales en la tele…"
            } else {
                "Lista M3U guardada. Cargando canales en la tele…"
            }
        }
    }

    fun onAppStart() {
        appScope.launch {
            runCatching { vpnServers.importBundled() }.onFailure { Log.w(TAG, "Configs VPN empaquetadas", it) }
            db.programs().runCatching { deleteOlderThan(System.currentTimeMillis() - 2 * 24 * 3_600_000L) }
            val s = settings.load()
            val upgraded = runCatching { playlists.upgradeXtreamM3uLists() }
                .onFailure { Log.w(TAG, "Convertir listas M3U de Xtream", it) }
                .getOrDefault(emptyList())
            // En el móvil, con datos móviles no se descargan solos catálogo y guía (pueden ser cientos de MB).
            val onMobileData = BuildConfig.FLAVOR == "mobile" && !s.syncOnMobileData && app.isMeteredNetwork()
            if (onMobileData) Log.i(TAG, "Datos móviles: sin actualización automática del catálogo")
            playlists.activeNow()?.takeIf { !onMobileData }?.let { p ->
                if (p.id in upgraded || catalog.needsSync(p, s.autoRefreshHours)) syncPlaylist(p.id)
                else if (epg.needsSync(p)) runCatching { epg.sync(p) }
            }
            checkForUpdates(force = false)
        }
    }

    suspend fun syncPlaylist(id: Long) {
        val p = playlists.byId(id) ?: return
        runCatching { catalog.sync(p) }.onFailure { Log.w(TAG, "Sync catálogo", it); return }
        playlists.byId(id)?.let { fresh -> runCatching { epg.sync(fresh) }.onFailure { Log.w(TAG, "Sync EPG", it) } }
    }

    suspend fun checkForUpdates(force: Boolean): Result<UpdateInfo?> {
        if (!updater.isConfigured) return Result.success(null)
        val s = settings.load()
        if (!force && System.currentTimeMillis() - s.lastUpdateCheck < 12 * 3_600_000L) return Result.success(_availableUpdate.value)
        return runCatching { updater.check() }
            .onSuccess { info ->
                settings.setLastUpdateCheck(System.currentTimeMillis())
                _availableUpdate.value = info?.takeIf { it.isNewer }
            }
    }

    private companion object {
        const val TAG = "AppGraph"
    }
}
