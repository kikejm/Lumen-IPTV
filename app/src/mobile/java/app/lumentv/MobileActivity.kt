package app.lumentv

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import app.lumentv.ui.MobileRoot
import app.lumentv.ui.screens.vpn.ConfImport
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Única Activity de la versión móvil. Sin orientación fija (vertical y horizontal) y con
 * `configChanges` en el manifiesto: girar no la recrea, Compose recompone con el nuevo tamaño.
 * Dibuja de borde a borde con las barras del sistema siempre claras sobre fondo negro.
 */
class MobileActivity : ComponentActivity() {

    /**
     * Lo activa el reproductor mientras está abierto: al salir de la app (Inicio, gesto o cambio de
     * app) el vídeo pasa a imagen en imagen si está activado en Ajustes. En Android 12+ el sistema
     * entra solo (`setAutoEnterEnabled`), con una animación más suave.
     */
    var playerActive: Boolean = false
        set(value) {
            field = value
            updateAutoPip()
        }

    var isInPip by mutableStateOf(false)
        private set

    val supportsPip: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Reproductor a pantalla completa (lo pone `PlayerScreen`): la ventana PiP toma la proporción de
     * su vídeo y lleva un botón de pausa/reproducir que actúa sobre él.
     */
    var pipPlayer: Player? = null
        set(value) {
            field?.removeListener(pipListener)
            field = value
            value?.addListener(pipListener)
            updateAutoPip()
        }

    private val pipListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_VIDEO_SIZE_CHANGED)) updateAutoPip()
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            pipPlayer?.let { if (it.playWhenReady) it.pause() else it.play() }
        }
    }

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(pipAspect()).setActions(pipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        return b.build()
    }

    /** Proporción del vídeo, dentro de los límites de Android para PiP (entre 1:2,39 y 2,39:1). */
    private fun pipAspect(): Rational {
        val size = pipPlayer?.videoSize
        if (size == null || size.width == 0 || size.height == 0) return Rational(16, 9)
        val ratio = (size.width * size.pixelWidthHeightRatio / size.height).coerceIn(0.42f, 2.39f)
        return Rational((ratio * 1000).roundToInt(), 1000)
    }

    private fun pipActions(): List<RemoteAction> {
        val player = pipPlayer ?: return emptyList()
        val playing = player.playWhenReady
        val intent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = if (playing) "Pausa" else "Reproducir"
        val icon = Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        return listOf(RemoteAction(icon, label, label, intent))
    }

    private fun updateAutoPip() {
        if (!supportsPip) return
        // Antes de Android 12 los parámetros solo cuentan al entrar (`enterPip`) o ya dentro de PiP.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isInPip) return
        runCatching { setPictureInPictureParams(pipParams(playerActive && graph.settings.current.pipEnabled)) }
    }

    fun enterPip() {
        if (!supportsPip) return
        runCatching { enterPictureInPictureMode(pipParams(autoEnter = false)) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // En Android 12+ ya lo hace el sistema con autoEnter.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && playerActive && graph.settings.current.pipEnabled) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        // Sale de PiP estando parada: el usuario ha cerrado la ventana (X o arrastrándola), no la ha ampliado.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) pipClosed = true
    }

    /** Se cerró la ventana PiP: al volver a la app, el vídeo sigue en pausa. */
    private var pipClosed = false

    /**
     * Sin `MediaSession` ni servicio no hay controles fuera de la app: si no se ve (Inicio sin PiP,
     * otra app, pantalla apagada o ventana PiP cerrada), nada debe sonar. Entrar en PiP no llega
     * aquí (la Activity sigue visible); cerrar la ventana PiP, sí.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) graph.players.enterBackground()
    }

    override fun onStart() {
        super.onStart()
        graph.players.enterForeground(resume = !pipClosed)
        pipClosed = false
    }

    private var vpnPermissionCallback: ((Boolean) -> Unit)? = null
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = graph.vpn.permissionIntent() == null
        vpnPermissionCallback?.invoke(granted)
        vpnPermissionCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { MobileRoot(graph) }
        if (savedInstanceState == null) {
            autoConnectVpn()
            importShared(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importShared(intent)
    }

    /**
     * Un `.conf` abierto con IPTV («Abrir con») o compartido desde otra app (Archivos, navegador,
     * correo). Se importa en appScope y `MobileRoot` lleva a la pantalla VPN, que muestra el resultado.
     */
    private fun importShared(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        val text = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        // Que no se vuelva a importar si la Activity se recrea con el mismo Intent.
        setIntent(Intent(Intent.ACTION_MAIN))
        if (uris.isEmpty() && text.isNullOrBlank()) return
        // Se lee ya, mientras dura el permiso temporal sobre los ficheros que da la otra app.
        graph.appScope.launch { ConfImport.post(ConfImport.import(applicationContext, graph, uris, text).copy(fromOtherApp = true)) }
    }

    /** Pide (una sola vez) el permiso de VPN de Android y devuelve si está concedido. */
    fun ensureVpnPermission(callback: (Boolean) -> Unit) {
        val intent = graph.vpn.permissionIntent()
        if (intent == null) {
            callback(true)
            return
        }
        vpnPermissionCallback = callback
        runCatching { vpnPermissionLauncher.launch(intent) }.onFailure { callback(false) }
    }

    private fun autoConnectVpn() {
        lifecycleScope.launch {
            val s = graph.settings.load()
            if (s.vpnAutoConnect && !graph.vpn.isConnected && graph.vpnServers.all().isNotEmpty()) {
                ensureVpnPermission { ok -> if (ok) lifecycleScope.launch { graph.vpn.quickConnect() } }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        pipPlayer = null
        if (isFinishing && graph.settings.current.vpnDisconnectOnExit && graph.vpn.isConnected) {
            graph.appScope.launch { graph.vpn.disconnect() }
        }
        super.onDestroy()
    }

    private companion object {
        const val ACTION_PIP_TOGGLE = "app.lumentv.action.PIP_TOGGLE"
    }
}
