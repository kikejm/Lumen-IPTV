package app.lumentv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lumentv.BuildConfig
import app.lumentv.core.util.TimeFormatter
import app.lumentv.core.util.relativeAge
import app.lumentv.data.catalog.SyncState
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.epg.EpgSyncState
import app.lumentv.data.settings.AppSettings
import app.lumentv.data.settings.LiveFormat
import app.lumentv.data.settings.ResizeMode
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.ConfirmDialog
import app.lumentv.ui.components.GroupCard
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.NoticePill
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.PinSheet
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.RowDivider
import app.lumentv.ui.components.SettingRow
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.SwitchRow
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TextInputSheet
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import app.lumentv.vpn.VpnState
import kotlinx.coroutines.launch

private enum class Section(val label: String, val icon: ImageVector) {
    PLAYLISTS("Listas", Icons.AutoMirrored.Rounded.List),
    PLAYER("Reproductor", Icons.Rounded.PlayCircle),
    EPG("Guía EPG", Icons.Rounded.Schedule),
    VPN("VPN", Icons.Rounded.Shield),
    PARENTAL("Control parental", Icons.Rounded.Lock),
    TOOLS("Herramientas", Icons.Rounded.Handyman),
    ABOUT("Acerca de", Icons.Rounded.Info),
}

private val EXTERNAL_PLAYERS = listOf(
    "" to "Interno (IPTV)",
    "org.videolan.vlc" to "VLC",
    "com.mxtech.videoplayer.ad" to "MX Player",
    "org.xbmc.kodi" to "Kodi",
)

private val BUFFERS = listOf(5, 15, 30, 60)

/**
 * Ajustes (Stitch 08-ajustes / L08-ajustes). Mismas opciones que la TV más las propias del móvil
 * (imagen en imagen). Vertical: todos los grupos en una lista. Horizontal y tableta: maestro-detalle.
 */
@Composable
fun SettingsScreen(nav: Navigator, insets: PaddingValues) {
    val wide = LocalWidthClass.current != WidthClass.Compact
    var section by rememberSaveable { mutableStateOf(Section.PLAYLISTS) }
    var notice by remember { mutableStateOf<String?>(null) }
    val toast: (String) -> Unit = { notice = it }
    val c = Lumen.colors

    Box(Modifier.fillMaxSize().padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = 16.dp)) { TopBar(nav, "Ajustes", showBack = true, showActions = false) }
            if (wide) {
                Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LazyColumn(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(Section.entries) { s ->
                            val sel = s == section
                            TapSurface(
                                onClick = { section = s },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                radius = LumenDimens.radiusSmall,
                                color = if (sel) c.surfaceElevated else Color.Transparent,
                            ) {
                                Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Icon(s.icon, contentDescription = null, tint = if (sel) c.accent else c.textSecondary, modifier = Modifier.size(22.dp))
                                    LText(s.label, style = if (sel) Lumen.type.headline else Lumen.type.body, color = if (sel) c.textPrimary else c.textSecondary, maxLines = 1)
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                        GroupCard(title = section.label) { SectionContent(section, nav, toast) }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    items(Section.entries) { s -> GroupCard(title = s.label) { SectionContent(s, nav, toast) } }
                    item {
                        LText(
                            "IPTV ${BuildConfig.VERSION_NAME}",
                            style = Lumen.type.caption, color = c.textTertiary, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        NoticePill(notice, onShown = { notice = null }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SectionContent(section: Section, nav: Navigator, toast: (String) -> Unit) {
    when (section) {
        Section.PLAYLISTS -> PlaylistsSection(nav, toast)
        Section.PLAYER -> PlayerSection()
        Section.EPG -> EpgSection(toast)
        Section.VPN -> VpnSection(nav)
        Section.PARENTAL -> ParentalSection(toast)
        Section.TOOLS -> ToolsSection(nav)
        Section.ABOUT -> AboutSection()
    }
}

@Composable
private fun rememberSettings(): AppSettings {
    val s by LocalGraph.current.settings.settings.collectAsState()
    return s
}

/** Elegir un valor de una lista corta en una hoja (en lugar de ir pasando valores como en la TV). */
@Composable
private fun <T> ChoiceSheet(title: String, choices: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    OptionsSheet(
        title,
        choices.map { (value, label) ->
            SheetOption(label, if (value == selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked) { onSelect(value) }
        },
        onDismiss = onDismiss,
    )
}

// ---------------------------------------------------------------------------

@Composable
private fun PlaylistsSection(nav: Navigator, toast: (String) -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val playlists by graph.playlists.playlists.collectAsState(initial = emptyList())
    val active by graph.playlists.active.collectAsState(initial = null)
    val sync by graph.catalog.syncState.collectAsState()
    var menuFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleteFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    val c = Lumen.colors

    playlists.forEach { p ->
        val isActive = p.id == active?.id
        val detail = buildList {
            add(if (p.type == PlaylistType.XTREAM) "Xtream · ${p.serverUrl.substringAfter("://")}" else "M3U")
            if (p.expiresAt > 0) add("Expira ${TimeFormatter(true).date(p.expiresAt)}")
            if (p.maxConnections > 0) add("${p.maxConnections} ${if (p.maxConnections == 1) "conexión" else "conexiones"}")
            val running = sync as? SyncState.Running
            add(if (isActive && running != null) "${running.step}…" else "Actualizada ${relativeAge(p.lastSyncAt)}")
        }.joinToString(" · ")
        SettingRow(
            title = p.name,
            subtitle = detail,
            icon = if (isActive) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.List,
            iconTint = if (isActive) c.success else c.textSecondary,
            chevron = true,
            onClick = { menuFor = p },
        )
        RowDivider()
    }
    SettingRow("Añadir lista", icon = Icons.Rounded.AddCircle, titleColor = c.accent, chevron = true, onClick = { nav.go(Routes.playlistEdit()) })

    menuFor?.let { p ->
        OptionsSheet(
            p.name,
            listOfNotNull(
                if (p.id != active?.id) SheetOption("Usar esta lista", Icons.Rounded.CheckCircle) { scope.launch { graph.playlists.setActive(p.id) } } else null,
                SheetOption("Actualizar canales y EPG ahora", Icons.Rounded.Refresh) {
                    graph.appScope.launch { graph.syncPlaylist(p.id) }
                    toast("Actualizando «${p.name}»…")
                },
                SheetOption("Editar", Icons.Rounded.Edit) { nav.go(Routes.playlistEdit(p.id)) },
                SheetOption("Eliminar", Icons.Rounded.Delete, destructive = true) { deleteFor = p },
            ),
            onDismiss = { menuFor = null },
        )
    }
    deleteFor?.let { p ->
        ConfirmDialog(
            "Eliminar «${p.name}»",
            "Se borrarán sus canales, favoritos e historial de este móvil.",
            "Eliminar",
            onConfirm = { scope.launch { graph.playlists.delete(p.id) } },
            onDismiss = { deleteFor = null },
            destructive = true,
        )
    }
}

@Composable
private fun PlayerSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    var choose by remember { mutableStateOf<String?>(null) }
    var editUa by remember { mutableStateOf(false) }

    SettingRow("Formato de directo", subtitle = "Si un canal no arranca, prueba el otro", value = s.liveFormat.label, chevron = true, onClick = { choose = "format" })
    RowDivider()
    SettingRow("Búfer", subtitle = "Más búfer, menos cortes; más espera al cambiar de canal", value = "${s.bufferSeconds} s", chevron = true, onClick = { choose = "buffer" })
    RowDivider()
    SettingRow("Imagen por defecto", value = s.resizeMode.label, chevron = true, onClick = { choose = "resize" })
    RowDivider()
    SwitchRow("Decodificador alternativo", s.decoderFallback, { v -> scope.launch { graph.settings.setDecoderFallback(v) } }, subtitle = "Usa otro decodificador si el del móvil falla")
    RowDivider()
    SwitchRow("Imagen en imagen al salir", s.pipEnabled, { v -> scope.launch { graph.settings.setPipEnabled(v) } }, subtitle = "El vídeo sigue en una ventana pequeña al salir de la app")
    RowDivider()
    SettingRow(
        "Reproductor",
        subtitle = "Abrir el vídeo en otra app instalada",
        value = EXTERNAL_PLAYERS.firstOrNull { it.first == s.externalPlayerPackage }?.second ?: s.externalPlayerPackage,
        chevron = true,
        onClick = { choose = "player" },
    )
    RowDivider()
    SettingRow("User-Agent", subtitle = "Algunos proveedores solo aceptan ciertos", value = s.userAgent, chevron = true, onClick = { editUa = true })

    when (choose) {
        "format" -> ChoiceSheet("Formato de directo", LiveFormat.entries.map { it to it.label }, s.liveFormat, { choose = null }) {
            scope.launch { graph.settings.setLiveFormat(it) }
        }
        "buffer" -> ChoiceSheet("Búfer", BUFFERS.map { it to "$it segundos" }, s.bufferSeconds, { choose = null }) {
            scope.launch { graph.settings.setBufferSeconds(it) }
        }
        "resize" -> ChoiceSheet("Imagen por defecto", ResizeMode.entries.map { it to it.label }, s.resizeMode, { choose = null }) {
            scope.launch { graph.settings.setResizeMode(it) }
        }
        "player" -> ChoiceSheet("Reproductor", EXTERNAL_PLAYERS, s.externalPlayerPackage, { choose = null }) {
            scope.launch { graph.settings.setExternalPlayer(it) }
        }
    }
    if (editUa) {
        TextInputSheet(
            title = "User-Agent",
            initial = s.userAgent,
            label = "User-Agent",
            message = "Déjalo vacío para volver al predeterminado (${AppSettings.DEFAULT_USER_AGENT}).",
            onDismiss = { editUa = false },
            onSave = { v -> scope.launch { graph.settings.setUserAgent(v.ifBlank { AppSettings.DEFAULT_USER_AGENT }) } },
        )
    }
}

@Composable
private fun EpgSection(toast: (String) -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    val epgState by graph.epg.state.collectAsState()
    val active by graph.playlists.active.collectAsState(initial = null)
    var addSource by remember { mutableStateOf(false) }
    var removeSource by remember { mutableStateOf<String?>(null) }
    val c = Lumen.colors

    SwitchRow("Formato de 24 horas", s.use24h, { v -> scope.launch { graph.settings.setUse24h(v) } })
    RowDivider()
    SettingRow(
        "Desfase horario",
        subtitle = "Corrige guías que llegan con la hora desplazada",
        trailing = {
            IconCircleButton(Icons.Rounded.Remove, "Restar una hora", onClick = {
                if (s.epgOffsetHours > -12) scope.launch { graph.settings.setEpgOffset(s.epgOffsetHours - 1) }
            }, color = c.surfaceElevated)
            LText(
                "${if (s.epgOffsetHours > 0) "+" else ""}${s.epgOffsetHours} h",
                style = Lumen.type.headline, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp),
            )
            IconCircleButton(Icons.Rounded.Add, "Sumar una hora", onClick = {
                if (s.epgOffsetHours < 12) scope.launch { graph.settings.setEpgOffset(s.epgOffsetHours + 1) }
            }, color = c.surfaceElevated)
        },
    )
    RowDivider()
    val status = when (val st = epgState) {
        is EpgSyncState.Running -> "Cargando ${st.source}… ${st.programs} programas"
        is EpgSyncState.Done -> "${st.programs} programas cargados"
        is EpgSyncState.Failed -> "Error: ${st.message}"
        EpgSyncState.Idle -> "Actualizada ${relativeAge(active?.lastEpgSyncAt ?: 0)}"
    }
    SettingRow("Actualizar guía ahora", subtitle = status, icon = Icons.Rounded.Refresh, enabled = epgState !is EpgSyncState.Running, onClick = {
        val p = active ?: return@SettingRow
        graph.appScope.launch { runCatching { graph.epg.sync(p) } }
        toast("Actualizando la guía…")
    })
    s.externalEpgUrls.forEach { url ->
        RowDivider()
        SettingRow(url.substringAfter("://"), subtitle = "Fuente XMLTV externa · toca para quitarla", onClick = { removeSource = url })
    }
    RowDivider()
    SettingRow("Añadir fuente EPG (XMLTV)", icon = Icons.Rounded.AddCircle, titleColor = c.accent, onClick = { addSource = true })

    if (addSource) {
        TextInputSheet(
            title = "Añadir fuente EPG",
            initial = "",
            label = "URL XMLTV",
            placeholder = "https://…/guide.xml.gz",
            message = "Un fichero .xml o .xml.gz. Se suma a la guía de tu proveedor.",
            keyboardType = KeyboardType.Uri,
            saveLabel = "Añadir",
            onDismiss = { addSource = false },
            onSave = { url -> if (url.isNotBlank() && url !in s.externalEpgUrls) scope.launch { graph.settings.setExternalEpgUrls(s.externalEpgUrls + url) } },
        )
    }
    removeSource?.let { url ->
        ConfirmDialog("Quitar fuente EPG", url, "Quitar", onConfirm = {
            scope.launch { graph.settings.setExternalEpgUrls(s.externalEpgUrls - url) }
        }, onDismiss = { removeSource = null }, destructive = true)
    }
}

@Composable
private fun VpnSection(nav: Navigator) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    val state by graph.vpn.state.collectAsState()
    val c = Lumen.colors
    val (status, statusColor) = when (val st = state) {
        is VpnState.Connected -> st.server.name to c.success
        is VpnState.Connecting -> "Conectando…" to c.warning
        is VpnState.Failed -> "Error" to c.danger
        VpnState.Disconnected -> "Desconectada" to c.textSecondary
    }
    SettingRow(
        "Servidores y conexión",
        subtitle = "Elige país y servidor de Proton VPN",
        icon = Icons.Rounded.Shield,
        iconTint = statusColor,
        value = status,
        chevron = true,
        onClick = { nav.go(Routes.VPN) },
    )
    RowDivider()
    SwitchRow("Conectar al abrir IPTV", s.vpnAutoConnect, { v -> scope.launch { graph.settings.setVpnAutoConnect(v) } }, subtitle = "Usa el último servidor (o el favorito)")
    RowDivider()
    SwitchRow("Desconectar al salir de IPTV", s.vpnDisconnectOnExit, { v -> scope.launch { graph.settings.setVpnDisconnectOnExit(v) } }, subtitle = "Recomendado si la VPN es para todo el móvil")
    RowDivider()
    SwitchRow(
        "Solo esta app",
        s.vpnAppOnly,
        { v -> graph.appScope.launch { graph.settings.setVpnAppOnly(v); graph.vpn.reapplyScope() } },
        subtitle = "Si se desactiva, todo el tráfico del móvil pasa por la VPN",
    )
}

@Composable
private fun ParentalSection(toast: (String) -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings()
    var setPin by remember { mutableStateOf(false) }
    var verifyThen by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (!s.hasParentalPin) {
        SettingRow(
            "Crear PIN",
            subtitle = "Con un PIN de 4 dígitos puedes bloquear categorías: mantén pulsada una en Directo, Cine o Series.",
            icon = Icons.Rounded.Lock,
            chevron = true,
            onClick = { setPin = true },
        )
    } else {
        SettingRow(
            "Categorías bloqueadas",
            subtitle = "Mantén pulsada una categoría en Directo, Cine o Series para bloquearla o desbloquearla",
            value = "${s.lockedCategories.size}",
        )
        RowDivider()
        SettingRow("Cambiar PIN", chevron = true, onClick = { verifyThen = { setPin = true } })
        RowDivider()
        SettingRow("Volver a bloquear ahora", subtitle = "Pide el PIN otra vez en esta sesión", onClick = {
            graph.parental.lockSession()
            toast("Categorías bloqueadas de nuevo")
        })
        RowDivider()
        SettingRow("Eliminar PIN y desbloquear todo", titleColor = Lumen.colors.danger, onClick = {
            verifyThen = { scope.launch { graph.parental.removePin(); toast("PIN eliminado") } }
        })
    }

    if (setPin) {
        PinSheet("Nuevo PIN", "Introduce 4 dígitos", onSubmit = { pin ->
            scope.launch { graph.parental.setPin(pin); toast("PIN guardado") }
            true
        }, onDismiss = { setPin = false })
    }
    verifyThen?.let { action ->
        PinSheet("PIN actual", "Introduce el PIN para continuar", onSubmit = { pin ->
            graph.parental.verify(pin).also { ok -> if (ok) action() }
        }, onDismiss = { verifyThen = null })
    }
}

@Composable
private fun ToolsSection(nav: Navigator) {
    SettingRow(
        "Test de velocidad",
        subtitle = "Mide la descarga real y la latencia con tu proveedor",
        icon = Icons.Rounded.Speed,
        chevron = true,
        onClick = { nav.go(SPEED_TEST_ROUTE) },
    )
}

@Composable
private fun AboutSection() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val available by graph.availableUpdate.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val c = Lumen.colors

    SettingRow("Versión", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    if (graph.updater.isConfigured) {
        RowDivider()
        SettingRow("Buscar actualizaciones", subtitle = status, icon = Icons.Rounded.SystemUpdate, onClick = {
            status = "Buscando…"
            scope.launch {
                graph.checkForUpdates(force = true)
                    .onSuccess { status = if (it == null || !it.isNewer) "Estás en la última versión" else "Disponible ${it.versionName}" }
                    .onFailure { status = "Error: ${it.message}" }
            }
        })
        available?.let { info ->
            RowDivider()
            SettingRow(
                "Instalar IPTV ${info.versionName}",
                subtitle = info.notes.takeIf { it.isNotBlank() },
                titleColor = c.accent,
                enabled = progress == null,
                onClick = {
                    if (!graph.updater.canInstall()) {
                        status = "Permite a IPTV instalar apps y vuelve a pulsar"
                        graph.updater.openInstallPermissionSettings()
                        return@SettingRow
                    }
                    progress = 0f
                    scope.launch {
                        runCatching { graph.updater.download(info) { progress = it } }
                            .onSuccess { graph.updater.install(it); progress = null }
                            .onFailure { status = "Error: ${it.message}"; progress = null }
                    }
                },
            )
            progress?.let { ProgressLine(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
    }
    RowDivider()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LText("Reproductor IPTV personal con VPN WireGuard integrada. No incluye ni proporciona contenido: reproduce las listas que configures tú.", style = Lumen.type.callout, color = c.textSecondary)
        LText("Código abierto: AndroidX Media3, Jetpack Compose, WireGuard (Apache 2.0), OkHttp, Coil y la fuente Alan Sans (OFL).", style = Lumen.type.caption, color = c.textTertiary)
    }
}
