package app.lumentv.di

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val players = PlayerFactory(app, http, settings, handleAudioFocus = BuildConfig.FLAVOR == "mobile")

    val vpnServers = VpnServerRepository(app, db, secretBox, http)
    val vpn = VpnManager(app, vpnServers, settings, appScope, http, json)

    val updater = UpdateManager(app, http, json)
    val speedTest = SpeedTest(http)
    val importServer = ImportServer(appScope, json)

    /** Versión nueva publicada y aún sin instalar. Se guarda en Ajustes: sobrevive a cerrar la app. */
    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    /** versionCode cuyo aviso se ha ocultado en esta visita (0 = ninguno). Vuelve a 0 al entrar en la app. */
    private val dismissedUpdate = MutableStateFlow(0)

    /** Lo que muestra el aviso de Inicio: la versión pendiente, salvo que se haya ocultado en esta visita. */
    val updateNotice: StateFlow<UpdateInfo?> = combine(_availableUpdate, dismissedUpdate) { info, dismissed ->
        info?.takeIf { it.versionCode != dismissed }
    }.stateIn(appScope, SharingStarted.Eagerly, null)

    /** Oculta el aviso hasta la próxima vez que se entre en la app (Ajustes sigue ofreciendo instalarla). */
    fun dismissUpdateNotice() {
        dismissedUpdate.value = _availableUpdate.value?.versionCode ?: 0
    }

    private var foregroundUpdateChecks: Job? = null

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
        watchForegroundForUpdates()
        appScope.launch { restorePendingUpdate() }
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
        }
    }

    /**
     * Mientras la app está a la vista busca versiones nuevas al entrar y cada [UPDATE_POLL_MS]; así una
     * versión publicada aparece al poco rato. Cada vez que se vuelve a la app el aviso reaparece aunque
     * se hubiera ocultado. Debe llamarse desde el hilo principal.
     */
    private fun watchForegroundForUpdates() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                dismissedUpdate.value = 0
                foregroundUpdateChecks?.cancel()
                foregroundUpdateChecks = appScope.launch {
                    while (true) {
                        checkForUpdates(force = false)
                        delay(UPDATE_POLL_MS)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                foregroundUpdateChecks?.cancel()
                foregroundUpdateChecks = null
            }
        })
    }

    /** Recupera el aviso guardado (sin red), salvo que esa versión ya esté instalada. */
    private suspend fun restorePendingUpdate() {
        val saved = settings.load().pendingUpdate
        if (saved.isBlank()) return
        val info = runCatching { json.decodeFromString(UpdateInfo.serializer(), saved) }.getOrNull()
        if (info == null || !info.isNewer) {
            settings.setPendingUpdate("")
            return
        }
        // Si la comprobación por red ya ha contestado, su resultado manda.
        _availableUpdate.compareAndSet(null, info)
    }

    suspend fun syncPlaylist(id: Long) {
        val p = playlists.byId(id) ?: return
        runCatching { catalog.sync(p) }.onFailure { Log.w(TAG, "Sync catálogo", it); return }
        playlists.byId(id)?.let { fresh -> runCatching { epg.sync(fresh) }.onFailure { Log.w(TAG, "Sync EPG", it) } }
    }

    suspend fun checkForUpdates(force: Boolean): Result<UpdateInfo?> {
        if (!updater.isConfigured) return Result.success(null)
        val s = settings.load()
        if (!force && System.currentTimeMillis() - s.lastUpdateCheck < UPDATE_MIN_GAP_MS) return Result.success(_availableUpdate.value)
        // Sin red se conserva el aviso que hubiera: solo una respuesta correcta lo quita.
        return runCatching { updater.check() }
            .onSuccess { info ->
                settings.setLastUpdateCheck(System.currentTimeMillis())
                val pending = info?.takeIf { it.isNewer }
                _availableUpdate.value = pending
                settings.setPendingUpdate(pending?.let { json.encodeToString(UpdateInfo.serializer(), it) }.orEmpty())
            }
            .onFailure { Log.w(TAG, "Buscar actualizaciones", it) }
    }

    private companion object {
        const val TAG = "AppGraph"

        // GitHub permite 60 consultas por hora y red sin token, y cada comprobación gasta 2: con la TV y
        // el móvil en la misma casa, cada 15 min (y no más de una cada 10 al entrar y salir) cabe de sobra.
        const val UPDATE_POLL_MS = 15 * 60_000L
        const val UPDATE_MIN_GAP_MS = 10 * 60_000L
    }
}
