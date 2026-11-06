package app.lumentv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.theme.Lumen
import app.lumentv.vpn.VpnState

/**
 * Barra superior del móvil: título (o flecha atrás en pantallas de detalle), píldora VPN,
 * buscar y ajustes. Igual en vertical y en horizontal; en horizontal la navegación principal
 * está en el raíl lateral.
 */
@Composable
fun TopBar(
    nav: Navigator,
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    showActions: Boolean = true,
    /** Qué hace la flecha atrás; por defecto, volver a la pantalla anterior. */
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showBack) IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onClick = { onBack?.invoke() ?: nav.back() })
        LText(title, style = Lumen.type.largeTitle, maxLines = 1, modifier = Modifier.weight(1f))
        if (showActions) {
            VpnPill(onClick = { nav.go(Routes.VPN) })
            IconCircleButton(Icons.Rounded.Search, "Buscar", onClick = { nav.go(Routes.SEARCH) })
            IconCircleButton(Icons.Rounded.Settings, "Ajustes", onClick = { nav.go(Routes.SETTINGS) })
        }
    }
}

/**
 * Píldora de estado de la VPN; abre la pantalla VPN. Verde solo tras el primer handshake
 * (ARCHITECTURE.md → invariantes); antes, naranja.
 */
@Composable
fun VpnPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val state by graph.vpn.state.collectAsState()
    val stats by graph.vpn.stats.collectAsState()
    val c = Lumen.colors
    val (dot, label) = when (val s = state) {
        is VpnState.Connected -> (if ((stats?.lastHandshakeMs ?: 0) > 0) c.success else c.warning) to "VPN · ${s.server.name}"
        is VpnState.Connecting -> c.warning to "Conectando…"
        is VpnState.Failed -> c.danger to "VPN · error"
        VpnState.Disconnected -> c.textTertiary to "VPN"
    }
    TapSurface(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 40.dp).widthIn(max = 170.dp), radius = 50.dp) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state is VpnState.Disconnected) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
            } else {
                StatusDot(dot)
            }
            // En vertical el ancho es escaso: el nombre del servidor se corta con elipsis.
            LText(label, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.padding(end = 2.dp))
        }
    }
}
