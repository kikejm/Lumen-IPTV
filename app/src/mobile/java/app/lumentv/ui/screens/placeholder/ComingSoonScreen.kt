package app.lumentv.ui.screens.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes

/**
 * Hueco provisional para las pantallas que llegan en fases posteriores de docs/PLAN-MOVIL.md.
 * Así la navegación completa ya funciona y se puede probar en el móvil.
 */
@Composable
fun ComingSoonScreen(nav: Navigator, insets: PaddingValues, title: String, phase: Int, detail: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) {
        TopBar(nav, title, showBack = detail, showActions = !detail)
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Construction,
                title = "En construcción",
                message = "«$title» llega en la fase $phase de la versión móvil. Mientras tanto, puedes usarlo en la app del Fire TV.",
                actions = { PillButton("Volver a Inicio", onClick = { nav.replaceRoot(Routes.HOME) }, primary = true) },
            )
        }
    }
}
