package app.lumentv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lumentv.di.AppGraph
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenTheme

/**
 * Activity de la versión móvil. Fase 0 del plan (docs/PLAN-MOVIL.md): solo comprueba que el
 * núcleo compartido (AppGraph, Room, ajustes) arranca en el teléfono. La UI llega en la fase 2.
 */
class MobileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LumenTheme { PlaceholderScreen(graph) } }
    }
}

@Composable
private fun PlaceholderScreen(graph: AppGraph) {
    val playlist by graph.playlists.active.collectAsState(initial = null)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lumen.colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("IPTV", style = Lumen.type.largeTitle, color = Lumen.colors.textPrimary)
        Text(
            "Versión móvil en construcción",
            style = Lumen.type.body,
            color = Lumen.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            playlist?.let { "Lista activa: ${it.name}" } ?: "Sin lista configurada",
            style = Lumen.type.callout,
            color = Lumen.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
