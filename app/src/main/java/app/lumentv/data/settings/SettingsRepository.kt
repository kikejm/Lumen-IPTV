package app.lumentv.data.settings

import android.content.Context
import app.lumentv.data.catalog.QualityFilter
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ResizeMode(val label: String) { FIT("Ajustar"), FILL("Estirar"), ZOOM("Zoom") }
enum class LiveFormat(val ext: String, val label: String) { TS("ts", "MPEG-TS (.ts)"), HLS("m3u8", "HLS (.m3u8)") }

data class AppSettings(
    val use24h: Boolean = true,
    val epgOffsetHours: Int = 0,
    val liveFormat: LiveFormat = LiveFormat.TS,
    val userAgent: String = DEFAULT_USER_AGENT,
    val bufferSeconds: Int = 15,
    val decoderFallback: Boolean = true,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val externalPlayerPackage: String = "",
    val autoRefreshHours: Int = 24,
    val vpnAutoConnect: Boolean = false,
    val vpnDisconnectOnExit: Boolean = true,
    val vpnAppOnly: Boolean = true,
    val vpnLastServerId: Long = -1,
    val parentalPinHash: String = "",
    val lockedCategories: Set<String> = emptySet(),
    val externalEpgUrls: List<String> = emptyList(),
    val activePlaylistId: Long = -1,
    val lastUpdateCheck: Long = 0,
    val liveQuality: QualityFilter = QualityFilter.ALL,
) {
    val hasParentalPin: Boolean get() = parentalPinHash.isNotEmpty()

    companion object {
        // Muchos proveedores IPTV filtran por User-Agent; este es el más aceptado.
        const val DEFAULT_USER_AGENT = "IPTVSmartersPro"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lumen_settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.dataStore

    val settings: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** Valor actual (puede ser el valor por defecto si DataStore aún no ha cargado). */
    val current: AppSettings get() = settings.value

    suspend fun load(): AppSettings = store.data.first().toSettings()

    suspend fun update(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    suspend fun setUse24h(v: Boolean) = update { it[K.use24h] = v }
    suspend fun setEpgOffset(v: Int) = update { it[K.epgOffset] = v }
    suspend fun setLiveFormat(v: LiveFormat) = update { it[K.liveFormat] = v.name }
    suspend fun setUserAgent(v: String) = update { it[K.userAgent] = v }
    suspend fun setBufferSeconds(v: Int) = update { it[K.buffer] = v }
    suspend fun setDecoderFallback(v: Boolean) = update { it[K.decoderFallback] = v }
    suspend fun setResizeMode(v: ResizeMode) = update { it[K.resize] = v.name }
    suspend fun setExternalPlayer(pkg: String) = update { it[K.externalPlayer] = pkg }
    suspend fun setAutoRefreshHours(v: Int) = update { it[K.autoRefresh] = v }
    suspend fun setVpnAutoConnect(v: Boolean) = update { it[K.vpnAuto] = v }
    suspend fun setVpnDisconnectOnExit(v: Boolean) = update { it[K.vpnDisconnectOnExit] = v }
    suspend fun setVpnAppOnly(v: Boolean) = update { it[K.vpnAppOnly] = v }
    suspend fun setVpnLastServer(id: Long) = update { it[K.vpnLastServer] = id }
    suspend fun setParentalPinHash(hash: String) = update { it[K.pin] = hash }
    suspend fun setLockedCategories(v: Set<String>) = update { it[K.locked] = v }
    suspend fun setExternalEpgUrls(v: List<String>) = update { it[K.externalEpg] = v.joinToString("\n") }
    suspend fun setActivePlaylist(id: Long) = update { it[K.activePlaylist] = id }
    suspend fun setLastUpdateCheck(v: Long) = update { it[K.lastUpdateCheck] = v }
    suspend fun setLiveQuality(v: QualityFilter) = update { it[K.liveQuality] = v.name }

    private fun Preferences.toSettings() = AppSettings(
        use24h = this[K.use24h] ?: true,
        epgOffsetHours = this[K.epgOffset] ?: 0,
        liveFormat = enumOr(this[K.liveFormat], LiveFormat.TS),
        userAgent = this[K.userAgent]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_USER_AGENT,
        bufferSeconds = this[K.buffer] ?: 15,
        decoderFallback = this[K.decoderFallback] ?: true,
        resizeMode = enumOr(this[K.resize], ResizeMode.FIT),
        externalPlayerPackage = this[K.externalPlayer] ?: "",
        autoRefreshHours = this[K.autoRefresh] ?: 24,
        vpnAutoConnect = this[K.vpnAuto] ?: false,
        vpnDisconnectOnExit = this[K.vpnDisconnectOnExit] ?: true,
        vpnAppOnly = this[K.vpnAppOnly] ?: true,
        vpnLastServerId = this[K.vpnLastServer] ?: -1,
        parentalPinHash = this[K.pin] ?: "",
        lockedCategories = this[K.locked] ?: emptySet(),
        externalEpgUrls = this[K.externalEpg]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        activePlaylistId = this[K.activePlaylist] ?: -1,
        lastUpdateCheck = this[K.lastUpdateCheck] ?: 0,
        liveQuality = enumOr(this[K.liveQuality], QualityFilter.ALL),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private object K {
        val use24h = booleanPreferencesKey("use24h")
        val epgOffset = intPreferencesKey("epg_offset")
        val liveFormat = stringPreferencesKey("live_format")
        val userAgent = stringPreferencesKey("user_agent")
        val buffer = intPreferencesKey("buffer_seconds")
        val decoderFallback = booleanPreferencesKey("decoder_fallback")
        val resize = stringPreferencesKey("resize_mode")
        val externalPlayer = stringPreferencesKey("external_player")
        val autoRefresh = intPreferencesKey("auto_refresh_hours")
        val vpnAuto = booleanPreferencesKey("vpn_auto_connect")
        val vpnDisconnectOnExit = booleanPreferencesKey("vpn_disconnect_on_exit")
        val vpnAppOnly = booleanPreferencesKey("vpn_app_only")
        val vpnLastServer = longPreferencesKey("vpn_last_server")
        val pin = stringPreferencesKey("parental_pin")
        val locked = stringSetPreferencesKey("locked_categories")
        val externalEpg = stringPreferencesKey("external_epg")
        val activePlaylist = longPreferencesKey("active_playlist")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val liveQuality = stringPreferencesKey("live_quality")
    }
}
