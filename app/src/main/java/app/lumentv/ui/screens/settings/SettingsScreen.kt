package app.lumentv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import app.lumentv.BuildConfig
import app.lumentv.core.util.TimeFormatter
import app.lumentv.core.util.relativeAge
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.epg.EpgSyncState
import app.lumentv.data.settings.AppSettings
import app.lumentv.data.settings.LiveFormat
import app.lumentv.data.settings.ResizeMode
import app.lumentv.data.settings.ThemeMode
import app.lumentv.tools.SpeedResult
import app.lumentv.ui.components.ConfirmDialog
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.GlassPanel
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.MenuOption
import app.lumentv.ui.components.OptionsDialog
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.PinDialog
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.TvTextField
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.launch

private enum class Section(val label: String, val icon: ImageVector) {
    PLAYLISTS("Listas IPTV", Icons.AutoMirrored.Rounded.List),
    APPEARANCE("Apariencia", Icons.Rounded.Palette),
    PLAYER("Reproductor", Icons.Rounded.PlayCircle),
    EPG("Guía EPG", Icons.Rounded.Schedule),
    VPN("VPN", Icons.Rounded.Shield),
    PARENTAL("Control parental", Icons.Rounded.ChildCare),
    SPEED("Test de velocidad", Icons.Rounded.Speed),
    UPDATES("Actualizaciones", Icons.Rounded.SystemUpdate),
    ABOUT("Acerca de", Icons.Rounded.Info),
}

private val EXTERNAL_PLAYERS = listOf(
    "" to "Interno (IPTV)",
    "org.videolan.vlc" to "VLC",
    "com.mxtech.videoplayer.ad" to "MX Player",
    "org.xbmc.kodi" to "Kodi",
)

@Composable
fun SettingsScreen(nav: Navigator) {
    var section by remember { mutableStateOf(Section.PLAYLISTS) }
    val firstSection = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstSection.requestFocus() } }
    val c = Lumen.colors
    Column(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
    ) {
        TopBar(nav, Routes.SETTINGS, title = "Ajustes")
        Gap(height = 18.dp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(6.dp)) {
                items(Section.entries) { s ->
                    val sel = s == section
                    FocusSurface(
                        onClick = { section = s },
                        modifier = Modifier.fillMaxWidth().then(if (s.ordinal == 0) Modifier.focusRequester(firstSection) else Modifier),
                        containerColor = if (sel) c.surfaceElevated else Color.Transparent,
                        focusedScale = 1.03f,
                        radius = 12.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(s.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (sel) c.accent else c.textSecondary)
                            LText(s.label, style = Lumen.type.callout)
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(6.dp)) {
                item { LText(section.label, style = Lumen.type.title, modifier = Modifier.padding(bottom = 8.dp)) }
                sectionContent(section, nav)
            }
        }
    }
}

private fun LazyListScope.sectionContent(section: Section, nav: Navigator) {
    when (section) {
        Section.PLAYLISTS -> item { PlaylistsSection(nav) }
        Section.APPEARANCE -> item { AppearanceSection() }
        Section.PLAYER -> item { PlayerSection() }
        Section.EPG -> item { EpgSection() }
        Section.VPN -> item { VpnSection(nav) }
        Section.PARENTAL -> item { ParentalSection() }
        Section.SPEED -> item { SpeedSection() }
        Section.UPDATES -> item { UpdatesSection() }
        Section.ABOUT -> item { AboutSection() }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun SettingRow(title: String, value: String = "", subtitle: String = "", onClick: () -> Unit) {
    val c = Lumen.colors
    FocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LText(title, style = Lumen.type.callout)
                if (subtitle.isNotBlank()) LText(subtitle, style = Lumen.type.caption, color = c.textSecondary, maxLines = 2)
            }
            if (value.isNotBlank()) LText(value, style = Lumen.type.callout, color = c.textSecondary, maxLines = 1)
        }
    }
}

private fun onOff(v: Boolean) = if (v) "Activado" else "Desactivado"

@Composable
private fun rememberSettings(): AppSettings {
    val graph = LocalGraph.current
    val s by graph.settings.settings.collectAsState()
    return s
}

@Composable
private fun PlaylistsSection(nav: Navigator) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val playlists by graph.playlists.playlists.collectAsState(initial = emptyList())
    val active by graph.playlists.active.collectAsState(initial = null)
    var menuFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleteFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    val c = Lumen.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        playlists.forEach { p ->
            val isActive = p.id == active?.id
            FocusSurface(onClick = { menuFor = p }, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isActive) Icon(Icons.Rounded.CheckCircle, contentDescription = "Activa", tint = c.success)
                    Column(Modifier.weight(1f)) {
                        LText(p.name, style = Lumen.type.headline)
                        val detail = buildList {
                            add(if (p.type == PlaylistType.XTREAM) "Xtream · ${p.serverUrl.substringAfter("://")}" else "M3U")
                            if (p.expiresAt > 0) add("Expira ${TimeFormatter(true).date(p.expiresAt)}")
                            if (p.maxConnections > 0) add("${p.maxConnections} conexiones")
                            add("Actualizada ${relativeAge(p.lastSyncAt)}")
                        }.joinToString(" · ")
                        LText(detail, style = Lumen.type.caption, color = c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Añadir lista", onClick = { nav.go(Routes.playlistEdit()) }, primary = true)
            PillButton("Desde el móvil", onClick = { nav.go(Routes.REMOTE_SETUP) })
        }
    }
    menuFor?.let { p ->
        OptionsDialog(p.name, listOf(
            MenuOption("Usar esta lista") { scope.launch { graph.playlists.setActive(p.id) } },
            MenuOption("Actualizar canales y EPG ahora") { graph.appScope.launch { graph.syncPlaylist(p.id) } },
            MenuOption("Editar") { nav.go(Routes.playlistEdit(p.id)) },
            MenuOption("Eliminar") { deleteFor = p },
        ), onDismiss = { menuFor = null })
    }
    deleteFor?.let { p ->
        ConfirmDialog("Eliminar «${p.name}»", "Se borrarán sus canales, favoritos e historial de este dispositivo.", "Eliminar",
            onConfirm = { scope.launch { graph.playlists.delete(p.id) } }, onDismiss = { deleteFor = null })
    }
}

@Composable
private fun AppearanceSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow("Tema", s.themeMode.label, "Claro, oscuro o el del sistema") {
            val next = ThemeMode.entries[(s.themeMode.ordinal + 1) % ThemeMode.entries.size]
            scope.launch { graph.settings.setThemeMode(next) }
        }
        SettingRow("Formato de hora", if (s.use24h) "24 horas" else "12 horas (AM/PM)") { scope.launch { graph.settings.setUse24h(!s.use24h) } }
    }
}

@Composable
private fun PlayerSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    var editUa by remember { mutableStateOf(false) }
    val buffers = listOf(5, 15, 30, 60)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow("Formato de directo", s.liveFormat.label, "Si un canal no arranca, prueba el otro formato") {
            scope.launch { graph.settings.setLiveFormat(if (s.liveFormat == LiveFormat.TS) LiveFormat.HLS else LiveFormat.TS) }
        }
        SettingRow("Búfer", "${s.bufferSeconds} s", "Más búfer = menos cortes, más retardo al cambiar de canal") {
            scope.launch { graph.settings.setBufferSeconds(buffers[(buffers.indexOf(s.bufferSeconds) + 1) % buffers.size]) }
        }
        SettingRow("Imagen por defecto", s.resizeMode.label) {
            scope.launch { graph.settings.setResizeMode(ResizeMode.entries[(s.resizeMode.ordinal + 1) % ResizeMode.entries.size]) }
        }
        SettingRow("Decodificador alternativo", onOff(s.decoderFallback), "Usa otro decodificador si el de hardware falla") {
            scope.launch { graph.settings.setDecoderFallback(!s.decoderFallback) }
        }
        SettingRow("Reproductor", EXTERNAL_PLAYERS.firstOrNull { it.first == s.externalPlayerPackage }?.second ?: s.externalPlayerPackage, "Abrir el vídeo en otra app instalada") {
            val idx = EXTERNAL_PLAYERS.indexOfFirst { it.first == s.externalPlayerPackage }
            scope.launch { graph.settings.setExternalPlayer(EXTERNAL_PLAYERS[(idx + 1) % EXTERNAL_PLAYERS.size].first) }
        }
        SettingRow("Imagen en imagen (PiP)", onOff(s.pipEnabled), "Al pulsar Inicio durante la reproducción (si el Fire TV lo soporta)") {
            scope.launch { graph.settings.setPipEnabled(!s.pipEnabled) }
        }
        SettingRow("User-Agent", s.userAgent, "Algunos proveedores solo aceptan ciertos User-Agent") { editUa = true }
    }
    if (editUa) {
        TextInputDialog("User-Agent", s.userAgent, onDismiss = { editUa = false }) { v ->
            scope.launch { graph.settings.setUserAgent(v.ifBlank { AppSettings.DEFAULT_USER_AGENT }) }
        }
    }
}

@Composable
private fun EpgSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    val epgState by graph.epg.state.collectAsState()
    val active by graph.playlists.active.collectAsState(initial = null)
    var addSource by remember { mutableStateOf(false) }
    var removeSource by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow("Desfase horario de la guía", "${if (s.epgOffsetHours > 0) "+" else ""}${s.epgOffsetHours} h", "Corrige EPGs que llegan con la hora desplazada") {
            val next = if (s.epgOffsetHours >= 12) -12 else s.epgOffsetHours + 1
            scope.launch { graph.settings.setEpgOffset(next) }
        }
        val status = when (val st = epgState) {
            is EpgSyncState.Running -> "Cargando ${st.source}… ${st.programs} programas"
            is EpgSyncState.Done -> "${st.programs} programas cargados"
            is EpgSyncState.Failed -> "Error: ${st.message}"
            EpgSyncState.Idle -> "Actualizada ${relativeAge(active?.lastEpgSyncAt ?: 0)}"
        }
        SettingRow("Actualizar guía ahora", status) { active?.let { p -> graph.appScope.launch { runCatching { graph.epg.sync(p) } } } }
        LText("FUENTES EPG EXTERNAS (XMLTV)", style = Lumen.type.caption, color = Lumen.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        s.externalEpgUrls.forEach { url -> SettingRow(url, "Quitar") { removeSource = url } }
        PillButton("Añadir fuente EPG", onClick = { addSource = true })
    }
    if (addSource) {
        TextInputDialog("URL XMLTV (.xml o .xml.gz)", "", keyboardType = KeyboardType.Uri, onDismiss = { addSource = false }) { url ->
            if (url.isNotBlank()) scope.launch { graph.settings.setExternalEpgUrls(s.externalEpgUrls + url.trim()) }
        }
    }
    removeSource?.let { url ->
        ConfirmDialog("Quitar fuente EPG", url, "Quitar", onConfirm = {
            scope.launch { graph.settings.setExternalEpgUrls(s.externalEpgUrls - url) }
        }, onDismiss = { removeSource = null })
    }
}

@Composable
private fun VpnSection(nav: Navigator) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow("Servidores y conexión", "Abrir", "Elige país y servidor de Proton VPN") { nav.go(Routes.VPN) }
        SettingRow("Conectar al abrir IPTV", onOff(s.vpnAutoConnect), "Usa el último servidor (o el favorito)") { scope.launch { graph.settings.setVpnAutoConnect(!s.vpnAutoConnect) } }
        SettingRow("Desconectar al salir de IPTV", onOff(s.vpnDisconnectOnExit), "Recomendado si el ámbito es «Todo el Fire TV»") { scope.launch { graph.settings.setVpnDisconnectOnExit(!s.vpnDisconnectOnExit) } }
        SettingRow("Ámbito del túnel", if (s.vpnAppOnly) "Solo IPTV" else "Todo el Fire TV", "«Solo IPTV» no afecta a Netflix, Prime, etc.") {
            scope.launch { graph.settings.setVpnAppOnly(!s.vpnAppOnly); graph.vpn.reapplyScope() }
        }
    }
}

@Composable
private fun ParentalSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    var setPin by remember { mutableStateOf(false) }
    var verifyThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LText("Bloquea categorías con un PIN. Pulsa ☰ sobre una categoría en Directo, Cine o Series para bloquearla.", style = Lumen.type.body, color = Lumen.colors.textSecondary)
        if (!s.hasParentalPin) {
            SettingRow("Crear PIN", "", "4 dígitos") { setPin = true }
        } else {
            SettingRow("Cambiar PIN") { verifyThen = { setPin = true } }
            SettingRow("Categorías bloqueadas", "${s.lockedCategories.size}") {}
            SettingRow("Volver a bloquear ahora", "", "Pide el PIN otra vez en esta sesión") { graph.parental.lockSession(); message = "Bloqueado" }
            SettingRow("Eliminar PIN y desbloquear todo") { verifyThen = { scope.launch { graph.parental.removePin(); message = "PIN eliminado" } } }
        }
        message?.let { LText(it, style = Lumen.type.callout, color = Lumen.colors.success) }
    }
    if (setPin) {
        PinDialog("Nuevo PIN", "Introduce 4 dígitos", onDismiss = { setPin = false }) { pin ->
            scope.launch { graph.parental.setPin(pin); message = "PIN guardado" }
            setPin = false
            true
        }
    }
    verifyThen?.let { action ->
        PinDialog("PIN actual", onDismiss = { verifyThen = null }) { pin ->
            graph.parental.verify(pin).also { if (it) { verifyThen = null; action() } }
        }
    }
}

@Composable
private fun SpeedSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val active by graph.playlists.active.collectAsState(initial = null)
    val vpn by graph.vpn.state.collectAsState()
    var running by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(0.0) }
    var result by remember { mutableStateOf<SpeedResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val c = Lumen.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LText(
            "Mide la descarga real de IPTV. " + if (vpn is app.lumentv.vpn.VpnState.Connected) "La VPN está activa: el resultado incluye el túnel." else "La VPN está desactivada.",
            style = Lumen.type.body, color = c.textSecondary,
        )
        LText(if (running) "%.1f Mbps".format(live) else result?.let { "%.1f Mbps".format(it.downloadMbps) } ?: "— Mbps", style = Lumen.type.largeTitle)
        result?.let { r ->
            LText("Latencia: ${r.latencyMs ?: "—"} ms · Servidor IPTV: ${r.iptvLatencyMs ?: "—"} ms", style = Lumen.type.callout, color = c.textSecondary)
            LText(
                when {
                    r.downloadMbps >= 25 -> "Suficiente para 4K."
                    r.downloadMbps >= 10 -> "Suficiente para Full HD."
                    r.downloadMbps >= 5 -> "Suficiente para HD; el 4K puede cortarse."
                    else -> "Conexión justa: prueba un servidor VPN más cercano o sin VPN."
                },
                style = Lumen.type.callout,
            )
        }
        error?.let { LText(it, style = Lumen.type.callout, color = c.danger) }
        PillButton(if (running) "Midiendo…" else "Iniciar test", onClick = {
            if (running) return@PillButton
            running = true; error = null; live = 0.0
            scope.launch {
                runCatching { graph.speedTest.run(active?.serverUrl) { live = it } }
                    .onSuccess { result = it }.onFailure { error = "Error: ${it.message}" }
                running = false
            }
        }, primary = true, icon = Icons.Rounded.Speed)
    }
}

@Composable
private fun UpdatesSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val available by graph.availableUpdate.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val c = Lumen.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRow("Versión instalada", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") {}
        if (!graph.updater.isConfigured) {
            LText("Las actualizaciones automáticas no están configuradas en este build (LUMEN_UPDATE_GITHUB_REPO o LUMEN_UPDATE_MANIFEST_URL en .env).", style = Lumen.type.body, color = c.textSecondary)
            return@Column
        }
        SettingRow("Buscar actualizaciones", status ?: "") {
            status = "Buscando…"
            scope.launch {
                graph.checkForUpdates(force = true)
                    .onSuccess { status = if (it == null || !it.isNewer) "Estás en la última versión" else "Disponible ${it.versionName}" }
                    .onFailure { status = "Error: ${it.message}" }
            }
        }
        available?.let { info ->
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LText("IPTV ${info.versionName}", style = Lumen.type.headline)
                    if (info.notes.isNotBlank()) LText(info.notes, style = Lumen.type.body, color = c.textSecondary, maxLines = 8)
                    progress?.let { ProgressLine(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Descargar e instalar", primary = true, icon = Icons.Rounded.SystemUpdate, onClick = {
                            if (!graph.updater.canInstall()) {
                                status = "Permite a IPTV instalar apps y vuelve a pulsar"
                                graph.updater.openInstallPermissionSettings()
                                return@PillButton
                            }
                            progress = 0f
                            scope.launch {
                                runCatching { graph.updater.download(info) { progress = it } }
                                    .onSuccess { graph.updater.install(it); progress = null }
                                    .onFailure { status = "Error: ${it.message}"; progress = null }
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val c = Lumen.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LText("IPTV ${BuildConfig.VERSION_NAME}", style = Lumen.type.headline)
        LText("Reproductor IPTV personal para Fire TV con VPN WireGuard integrada.", style = Lumen.type.body, color = c.textSecondary)
        LText("IPTV no incluye ni proporciona contenido: reproduce las listas que configures tú.", style = Lumen.type.body, color = c.textSecondary)
        LText("Componentes de código abierto: AndroidX Media3, Jetpack Compose for TV, WireGuard (Apache 2.0), OkHttp, Coil, ZXing, fuente Inter (OFL).", style = Lumen.type.caption, color = c.textTertiary)
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(Modifier.width(520.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LText(title, style = Lumen.type.headline)
                TvTextField(value, { value = it }, title, keyboardType = keyboardType, imeAction = ImeAction.Done)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Guardar", onClick = { onSave(value); onDismiss() }, primary = true)
                    PillButton("Cancelar", onClick = onDismiss)
                }
            }
        }
    }
}
