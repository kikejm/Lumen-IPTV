package app.lumentv.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import app.lumentv.data.db.VpnServerEntity
import app.lumentv.data.settings.SettingsRepository
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

sealed interface VpnState {
    data object Disconnected : VpnState
    data class Connecting(val server: VpnServerEntity) : VpnState
    data class Connected(val server: VpnServerEntity, val since: Long, val appOnly: Boolean) : VpnState
    data class Failed(val message: String, val server: VpnServerEntity?) : VpnState
}

data class VpnStats(val rxBytes: Long, val txBytes: Long, val lastHandshakeMs: Long)

data class PublicIp(val ip: String, val country: String, val countryCode: String, val city: String)

/**
 * Túnel WireGuard embebido (librería oficial `com.wireguard.android:tunnel`, backend Go en userspace).
 *
 * Solo puede haber un túnel activo (`lumen`). El permiso de VPN del sistema se pide una vez con
 * [permissionIntent]; después conectar y desconectar es instantáneo desde la propia app.
 */
class VpnManager(
    private val context: Context,
    private val servers: VpnServerRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    http: OkHttpClient,
    private val json: Json,
) {
    private val ipClient = http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private var backend: GoBackend? = null
    private var statsJob: Job? = null

    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<VpnStats?>(null)
    val stats: StateFlow<VpnStats?> = _stats.asStateFlow()

    private val _publicIp = MutableStateFlow<PublicIp?>(null)
    val publicIp: StateFlow<PublicIp?> = _publicIp.asStateFlow()

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.DOWN && _state.value is VpnState.Connected) {
                _state.value = VpnState.Disconnected
                stopStats()
            }
        }
    }

    /** `null` si ya hay permiso; si no, el Intent del diálogo del sistema que hay que lanzar. */
    fun permissionIntent(): Intent? = VpnService.prepare(context)

    val isConnected: Boolean get() = _state.value is VpnState.Connected

    suspend fun connect(server: VpnServerEntity): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            _state.value = VpnState.Connecting(server)
            runCatching {
                if (permissionIntent() != null) error("Falta el permiso de VPN del sistema")
                val appOnly = settings.load().vpnAppOnly
                val text = ProtonConfigParser.withScope(servers.configText(server), if (appOnly) context.packageName else null)
                val config = Config.parse(ByteArrayInputStream(text.toByteArray()))
                val be = backend()
                // Si había otro servidor activo, GoBackend lo baja antes de subir el nuevo.
                be.setState(tunnel, Tunnel.State.UP, config)
                servers.markConnected(server.id)
                settings.setVpnLastServer(server.id)
                _state.value = VpnState.Connected(server, System.currentTimeMillis(), appOnly)
                startStats()
                refreshPublicIp()
            }.onFailure { e ->
                Log.e(TAG, "Error conectando a ${server.name}", e)
                _state.value = VpnState.Failed(humanize(e), server)
            }
        }
    }

    suspend fun disconnect() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { backend?.setState(tunnel, Tunnel.State.DOWN, null) }
                .onFailure { Log.w(TAG, "Error al desconectar", it) }
            _state.value = VpnState.Disconnected
            stopStats()
            refreshPublicIp()
        }
    }

    /** Conexión rápida: último servidor usado, o el primer favorito, o el primero de la lista. */
    suspend fun quickConnect(): Result<Unit> {
        val all = servers.all()
        if (all.isEmpty()) return Result.failure(IllegalStateException("No hay servidores importados"))
        val lastId = settings.load().vpnLastServerId
        val target = all.firstOrNull { it.id == lastId } ?: all.firstOrNull { it.favorite } ?: all.first()
        return connect(target)
    }

    /** Re-aplica el ámbito (solo app / todo el dispositivo) reconectando si hace falta. */
    suspend fun reapplyScope() {
        val current = _state.value as? VpnState.Connected ?: return
        connect(current.server)
    }

    fun refreshPublicIp() {
        scope.launch(Dispatchers.IO) {
            _publicIp.value = runCatching {
                ipClient.newCall(Request.Builder().url("https://ipwho.is/").build()).execute().use { resp ->
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()) as JsonObject
                    fun s(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    PublicIp(s("ip"), s("country"), s("country_code"), s("city"))
                }
            }.getOrNull()
        }
    }

    private fun backend(): GoBackend = backend ?: GoBackend(context.applicationContext).also { backend = it }

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching {
                    val s = backend?.getStatistics(tunnel) ?: return@runCatching
                    val handshake = s.peers().maxOfOrNull { key -> s.peer(key)?.latestHandshakeEpochMillis ?: 0L } ?: 0L
                    _stats.value = VpnStats(s.totalRx(), s.totalTx(), handshake)
                }
                delay(2_000)
            }
        }
    }

    private fun stopStats() {
        statsJob?.cancel()
        statsJob = null
        _stats.value = null
    }

    private fun humanize(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is com.wireguard.android.backend.BackendException -> when (e.reason) {
                com.wireguard.android.backend.BackendException.Reason.VPN_NOT_AUTHORIZED -> "Android no ha autorizado la VPN"
                com.wireguard.android.backend.BackendException.Reason.UNABLE_TO_START_VPN -> "No se pudo iniciar el servicio VPN"
                com.wireguard.android.backend.BackendException.Reason.DNS_RESOLUTION_FAILURE -> "No se pudo resolver el servidor"
                else -> "Error de WireGuard: ${e.reason}"
            }
            msg.isNotBlank() -> msg
            else -> e.javaClass.simpleName
        }
    }

    private companion object {
        const val TAG = "VpnManager"
        const val TUNNEL_NAME = "lumen"
    }
}
