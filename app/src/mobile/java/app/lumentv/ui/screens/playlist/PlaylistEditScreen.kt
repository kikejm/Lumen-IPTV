package app.lumentv.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.lumentv.BuildConfig
import app.lumentv.data.catalog.SyncState
import app.lumentv.data.db.PlaylistType
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.LumenTextField
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.theme.Lumen
import kotlinx.coroutines.launch

/**
 * Alta y edición de lista (Stitch 09-alta-lista / L09-alta-lista). Misma lógica que la TV
 * (`PlaylistRepository.saveXtream`/`saveM3u` + sincronización en `appScope`), con formulario
 * táctil. En vertical, una columna; en horizontal, explicación a la izquierda y formulario a la
 * derecha. Los campos sobreviven a la rotación (`rememberSaveable`).
 */
@Composable
fun PlaylistEditScreen(nav: Navigator, insets: PaddingValues, playlistId: Long?, isOnboarding: Boolean) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val sync by graph.catalog.syncState.collectAsState()
    val wide = LocalWidthClass.current != WidthClass.Compact
    val c = Lumen.colors

    val defaultType = if (BuildConfig.DEFAULT_M3U_URL.isNotBlank() && BuildConfig.DEFAULT_XTREAM_SERVER.isBlank()) PlaylistType.M3U else PlaylistType.XTREAM
    var type by rememberSaveable { mutableStateOf(defaultType) }
    var name by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_PLAYLIST_NAME) }
    var server by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_XTREAM_SERVER) }
    var user by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_XTREAM_USERNAME) }
    var pass by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_XTREAM_PASSWORD) }
    var m3u by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_M3U_URL) }
    var epg by rememberSaveable { mutableStateOf(BuildConfig.DEFAULT_EPG_URL) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        if (loaded) return@LaunchedEffect
        val p = playlistId?.let { graph.playlists.byId(it) } ?: return@LaunchedEffect
        type = p.type
        name = p.name
        server = p.serverUrl
        user = p.username
        pass = runCatching { graph.playlists.credentials(p).password }.getOrDefault("")
        m3u = p.m3uUrl
        epg = p.epgUrl
        loaded = true
    }

    fun save() {
        if (busy) return
        focus.clearFocus()
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

    val intro: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = c.accent)
            }
            LText(if (isOnboarding) "Añade tu lista IPTV" else "Editar lista", style = Lumen.type.largeTitle)
            LText(
                "Es la misma que usas en IPTV Smarters u otra app: servidor, usuario y contraseña, o el enlace de la lista M3U.",
                style = Lumen.type.body,
                color = c.textSecondary,
            )
        }
    }

    val form: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.surface).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip("Xtream Codes", selected = type == PlaylistType.XTREAM, onClick = { type = PlaylistType.XTREAM }, modifier = Modifier.weight(1f))
                FilterChip("Lista M3U", selected = type == PlaylistType.M3U, onClick = { type = PlaylistType.M3U }, modifier = Modifier.weight(1f))
            }
            LumenTextField(name, { name = it }, "Nombre de la lista", placeholder = "Casa", modifier = Modifier.fillMaxWidth())
            if (type == PlaylistType.XTREAM) {
                LumenTextField(server, { server = it }, "Servidor", placeholder = "http://servidor.com:8080", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                LumenTextField(user, { user = it }, "Usuario", modifier = Modifier.fillMaxWidth())
                LumenTextField(pass, { pass = it }, "Contraseña", password = true, modifier = Modifier.fillMaxWidth())
                LumenTextField(
                    epg, { epg = it }, "EPG XMLTV propio (opcional)", placeholder = "Vacío = EPG del proveedor",
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, onDone = { save() }, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LumenTextField(m3u, { m3u = it }, "URL de la lista M3U", placeholder = "http://…/get.php?…&type=m3u_plus", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                LumenTextField(
                    epg, { epg = it }, "URL EPG XMLTV (opcional)", keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done, onDone = { save() }, modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { LText(it, style = Lumen.type.callout, color = c.danger) }
            PillButton(if (busy) "Comprobando…" else "Conectar", onClick = { save() }, primary = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (!isOnboarding) PillButton("Cancelar", onClick = { nav.back() }, modifier = Modifier.fillMaxWidth())
            val status = (sync as? SyncState.Running)?.let { "Cargando ${it.step.lowercase()}… ${if (it.count > 0) it.count else ""}" }
            if (busy && status != null) LText(status, style = Lumen.type.callout, color = c.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                LText("Las credenciales se guardan cifradas en este dispositivo.", style = Lumen.type.caption, color = c.textTertiary)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
        if (!isOnboarding) Box(Modifier.padding(horizontal = 16.dp)) { TopBar(nav, "", showBack = true, showActions = false) }
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) { intro() }
                Column(Modifier.weight(1f).widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) { form() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
                intro()
                Gap(height = 24.dp)
                form()
            }
        }
    }
}
