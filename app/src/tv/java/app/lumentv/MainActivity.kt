package app.lumentv

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.lumentv.ui.LumenRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Lo activa PlayerScreen para permitir PiP al pulsar Inicio durante la reproducción. */
    var playerActive: Boolean = false

    var isInPip by mutableStateOf(false)
        private set

    val supportsPip: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private var vpnPermissionCallback: ((Boolean) -> Unit)? = null
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = graph.vpn.permissionIntent() == null
        vpnPermissionCallback?.invoke(granted)
        vpnPermissionCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LumenRoot(graph) }
        if (savedInstanceState == null) autoConnectVpn()
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

    fun enterPip() {
        if (!supportsPip || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerActive && graph.settings.current.pipEnabled) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        if (isFinishing && graph.settings.current.vpnDisconnectOnExit && graph.vpn.isConnected) {
            graph.appScope.launch { graph.vpn.disconnect() }
        }
        super.onDestroy()
    }
}
