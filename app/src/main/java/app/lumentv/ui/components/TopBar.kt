package app.lumentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.MainTabs
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.theme.Lumen
import app.lumentv.vpn.VpnState

/**
 * Barra superior común: marca, pestañas tipo segmented control, estado VPN, búsqueda, ajustes y reloj.
 * Si [title] no es null se muestra un título en lugar de las pestañas (pantallas secundarias).
 */
@Composable
fun TopBar(nav: Navigator, currentRoute: String, modifier: Modifier = Modifier, title: String? = null) {
    val graph = LocalGraph.current
    val settings by graph.settings.settings.collectAsState()
    val c = Lumen.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LText("IPTV", style = Lumen.type.title, color = c.textPrimary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (title != null) {
                LText(title, style = Lumen.type.headline, color = c.textSecondary)
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.surface.copy(alpha = 0.7f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MainTabs.forEach { (route, label) ->
                        val selected = route == currentRoute
                        FocusSurface(
                            onClick = { if (!selected) nav.replaceRoot(route) },
                            radius = 50.dp,
                            containerColor = if (selected) c.surfaceFocused else Color.Transparent,
                            focusedContainerColor = Color.White,
                            contentColor = if (selected) c.textPrimary else c.textSecondary,
                            focusedContentColor = Color.Black,
                            focusedScale = 1.04f,
                            showRing = false,
                        ) {
                            LText(label, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
        VpnPill(onClick = { nav.go(Routes.VPN) })
        IconCircleButton(Icons.Rounded.Search, "Buscar", onClick = { nav.go(Routes.SEARCH) })
        IconCircleButton(Icons.Rounded.Settings, "Ajustes", onClick = { nav.go(Routes.SETTINGS) })
        Clock(settings.use24h)
    }
}

/** Píldora de estado de la VPN; al pulsarla se abre la pantalla VPN. */
@Composable
fun VpnPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val state by graph.vpn.state.collectAsState()
    val stats by graph.vpn.stats.collectAsState()
    val c = Lumen.colors
    val (dot, label) = when (val s = state) {
        // Naranja hasta el primer handshake: el túnel existe pero el servidor aún no ha respondido.
        is VpnState.Connected -> (if ((stats?.lastHandshakeMs ?: 0) > 0) c.success else c.warning) to "VPN · ${s.server.name}"
        is VpnState.Connecting -> c.warning to "Conectando…"
        is VpnState.Failed -> c.danger to "VPN · error"
        VpnState.Disconnected -> c.textTertiary to "VPN desactivada"
    }
    FocusSurface(onClick = onClick, modifier = modifier, radius = 50.dp, containerColor = c.surface, focusedScale = 1.05f) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state is VpnState.Disconnected) {
                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(14.dp), tint = c.textSecondary)
            } else {
                StatusDot(dot)
            }
            LText(label, style = Lumen.type.callout, maxLines = 1)
        }
    }
}
