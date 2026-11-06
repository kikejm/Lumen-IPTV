package app.lumentv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumentv.di.AppGraph
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.theme.Lumen
import app.lumentv.update.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Descarga e instala una actualización OTA. Lo comparten el aviso de Inicio y Ajustes → Acerca de.
 * La primera vez Android pide permitir a IPTV instalar apps: se abre ese ajuste y hay que volver a tocar.
 */
@Stable
class UpdateInstaller internal constructor(private val graph: AppGraph, private val scope: CoroutineScope) {
    /** Último mensaje para el usuario (permiso, error…), o `null`. */
    var status by mutableStateOf<String?>(null)

    /** Progreso de la descarga (0–1) mientras se baja el APK. */
    var progress by mutableStateOf<Float?>(null)
        private set

    fun install(info: UpdateInfo) {
        if (progress != null) return
        if (!graph.updater.canInstall()) {
            status = "Permite a IPTV instalar apps y vuelve a tocar"
            graph.updater.openInstallPermissionSettings()
            return
        }
        status = null
        progress = 0f
        scope.launch {
            runCatching { graph.updater.download(info) { progress = it } }
                .onSuccess { graph.updater.install(it); progress = null }
                .onFailure { status = "Error: ${it.message}"; progress = null }
        }
    }
}

@Composable
fun rememberUpdateInstaller(): UpdateInstaller {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    return remember(graph, scope) { UpdateInstaller(graph, scope) }
}

/** Aviso de Inicio cuando hay una versión nueva (como en la TV): tocarlo la descarga e instala. */
@Composable
fun UpdateBanner(info: UpdateInfo, installer: UpdateInstaller, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    val progress = installer.progress
    TapSurface(
        onClick = { installer.install(info) },
        modifier = modifier.fillMaxWidth(),
        color = c.accent.copy(alpha = 0.18f),
        enabled = progress == null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = c.accent)
                Column(Modifier.weight(1f)) {
                    LText("Actualización disponible: IPTV ${info.versionName}", style = Lumen.type.headline)
                    LText(
                        installer.status ?: if (progress != null) "Descargando…" else "Toca para instalarla",
                        style = Lumen.type.callout,
                        color = c.textSecondary,
                    )
                }
            }
            progress?.let { ProgressLine(it) }
        }
    }
}
