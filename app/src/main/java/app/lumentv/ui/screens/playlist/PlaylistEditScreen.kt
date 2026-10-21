package app.lumentv.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.lumentv.BuildConfig
import app.lumentv.data.catalog.SyncState
import app.lumentv.data.db.PlaylistType
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.TvTextField
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.launch

@Composable
fun PlaylistEditScreen(nav: Navigator, playlistId: Long?, isOnboarding: Boolean) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val sync by graph.catalog.syncState.collectAsState()
    val c = Lumen.colors

    var type by remember { mutableStateOf(PlaylistType.XTREAM) }
    var name by remember { mutableStateOf(BuildConfig.DEFAULT_PLAYLIST_NAME) }
    var server by remember { mutableStateOf(BuildConfig.DEFAULT_XTREAM_SERVER) }
    var user by remember { mutableStateOf(BuildConfig.DEFAULT_XTREAM_USERNAME) }
    var pass by remember { mutableStateOf(BuildConfig.DEFAULT_XTREAM_PASSWORD) }
    var m3u by remember { mutableStateOf(BuildConfig.DEFAULT_M3U_URL) }
    var epg by remember { mutableStateOf(BuildConfig.DEFAULT_EPG_URL) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }
    if (isOnboarding) {
        // Si la lista llega desde el móvil mientras estamos aquí, pasamos directamente a Inicio.
        val lists by graph.playlists.playlists.collectAsState(initial = emptyList())
        LaunchedEffect(lists.isNotEmpty()) { if (lists.isNotEmpty()) nav.replaceRoot(Routes.HOME) }
    }

    LaunchedEffect(playlistId) {
        val p = playlistId?.let { graph.playlists.byId(it) } ?: return@LaunchedEffect
        type = p.type
        name = p.name
        server = p.serverUrl
        user = p.username
        pass = runCatching { graph.playlists.credentials(p).password }.getOrDefault("")
        m3u = p.m3uUrl
        epg = p.epgUrl
    }
    if (BuildConfig.DEFAULT_M3U_URL.isNotBlank() && BuildConfig.DEFAULT_XTREAM_SERVER.isBlank()) {
        LaunchedEffect(Unit) { if (playlistId == null) type = PlaylistType.M3U }
    }

    fun save() {
        busy = true
        error = null
        scope.launch {
            runCatching {
                val id = when (type) {
                    PlaylistType.XTREAM -> graph.playlists.saveXtream(name, server, user, pass, playlistId).also { pid ->
                        if (epg.isNotBlank()) graph.playlists.byId(pid)?.let { graph.playlists.setEpgUrl(it, epg) }
                    }
                    PlaylistType.M3U -> graph.playlists.saveM3u(name, m3u, epg, playlistId)
                }
                // La carga del catálogo sigue en segundo plano; Inicio muestra el progreso.
                graph.appScope.launch { graph.syncPlaylist(id) }
            }.onSuccess {
                busy = false
                nav.replaceRoot(Routes.HOME)
            }.onFailure {
                busy = false
                error = it.message ?: "No se pudo guardar la lista"
            }
        }
    }

    Row(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal + 24.dp, vertical = LumenDimens.safeVertical + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LText("Lumen", style = Lumen.type.largeTitle)
            LText(
                if (isOnboarding) "Añade tu lista IPTV para empezar. Es la misma que usas en IPTV Smarters (servidor, usuario y contraseña)."
                else "Edita los datos de la lista.",
                style = Lumen.type.body, color = c.textSecondary,
            )
            Gap(height = 8.dp)
            LText("¿Te da pereza escribir con el mando?", style = Lumen.type.headline)
            LText("Abre la configuración en el móvil escaneando un QR.", style = Lumen.type.body, color = c.textSecondary)
            PillButton("Configurar desde el móvil", onClick = { nav.go(Routes.REMOTE_SETUP) }, icon = Icons.Rounded.QrCode2)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(c.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(PlaylistType.XTREAM to "Xtream Codes", PlaylistType.M3U to "Lista M3U").forEach { (t, label) ->
                    val sel = t == type
                    FocusSurface(
                        onClick = { type = t },
                        radius = 50.dp,
                        containerColor = if (sel) c.accent else Color.Transparent,
                        focusedContainerColor = if (sel) c.accent else c.surfaceFocused,
                        contentColor = if (sel) c.onAccent else c.textPrimary,
                        focusedContentColor = if (sel) c.onAccent else c.textPrimary,
                        focusedScale = 1.04f,
                    ) { LText(label, style = Lumen.type.callout, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
                }
            }
            TvTextField(name, { name = it }, "Nombre", placeholder = "Mi lista", focusRequester = firstField, modifier = Modifier.fillMaxWidth())
            if (type == PlaylistType.XTREAM) {
                TvTextField(server, { server = it }, "Servidor", placeholder = "http://servidor.com:8080", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvTextField(user, { user = it }, "Usuario", modifier = Modifier.weight(1f))
                    TvTextField(pass, { pass = it }, "Contraseña", password = true, modifier = Modifier.weight(1f))
                }
                TvTextField(epg, { epg = it }, "EPG XMLTV propio (opcional)", placeholder = "Vacío = EPG del proveedor", keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, modifier = Modifier.fillMaxWidth())
            } else {
                TvTextField(m3u, { m3u = it }, "URL de la lista M3U", placeholder = "http://…/get.php?…&type=m3u_plus", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                TvTextField(epg, { epg = it }, "URL EPG XMLTV (opcional)", keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(if (busy) "Comprobando…" else "Guardar y cargar", onClick = { if (!busy) save() }, icon = Icons.Rounded.Save, primary = true)
                if (!isOnboarding) PillButton("Cancelar", onClick = { nav.back() })
                val status = (sync as? SyncState.Running)?.let { "Cargando ${it.step.lowercase()}… ${if (it.count > 0) it.count else ""}" }
                if (busy && status != null) LText(status, style = Lumen.type.callout, color = c.textSecondary)
            }
            error?.let { LText(it, style = Lumen.type.callout, color = c.danger) }
        }
    }
}
