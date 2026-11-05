package app.lumentv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.lumentv.ui.LumenRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** La propia app está abriendo otra pantalla (permiso de VPN, reproductor externo, tráiler…): no es salir de ella. */
    private var openingOtherActivity = false

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

    /** Toda apertura de actividades desde esta Activity (`startActivity` incluido) pasa por aquí. */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        openingOtherActivity = true
        try {
            super.startActivityForResult(intent, requestCode, options)
        } catch (e: RuntimeException) {
            openingOtherActivity = false
            throw e
        }
    }

    override fun onResume() {
        super.onResume()
        openingOtherActivity = false
        graph.updater.openedSystemScreen = false
    }

    /** El usuario se va al menú del Fire TV con la tecla Inicio: la app se cierra, igual que al salir con Atrás. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!openingOtherActivity && !graph.updater.openedSystemScreen) finish()
    }

    override fun onDestroy() {
        if (isFinishing && graph.settings.current.vpnDisconnectOnExit && graph.vpn.isConnected) {
            graph.appScope.launch { graph.vpn.disconnect() }
        }
        super.onDestroy()
    }
}
