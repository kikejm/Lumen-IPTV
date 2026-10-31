package app.lumentv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.lumentv.ui.MobileRoot
import kotlinx.coroutines.launch

/**
 * Única Activity de la versión móvil. Sin orientación fija (vertical y horizontal) y con
 * `configChanges` en el manifiesto: girar no la recrea, Compose recompone con el nuevo tamaño.
 * Dibuja de borde a borde con las barras del sistema siempre claras sobre fondo negro.
 */
class MobileActivity : ComponentActivity() {

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
        setContent { MobileRoot(graph) }
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

    override fun onDestroy() {
        if (isFinishing && graph.settings.current.vpnDisconnectOnExit && graph.vpn.isConnected) {
            graph.appScope.launch { graph.vpn.disconnect() }
        }
        super.onDestroy()
    }
}
