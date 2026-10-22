package app.lumentv.ui.screens.vpn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import app.lumentv.MainActivity
import app.lumentv.core.util.countryName
import app.lumentv.core.util.flagEmoji
import app.lumentv.core.util.formatBytes
import app.lumentv.core.util.formatDuration
import app.lumentv.data.db.VpnServerEntity
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.ConfirmDialog
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.GlassPanel
import app.lumentv.ui.components.KeyHints
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.MenuOption
import app.lumentv.ui.components.OptionsDialog
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.StatusDot
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.TvTextField
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.onMenuKey
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import app.lumentv.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Country(val code: String, val name: String, val servers: List<VpnServerEntity>)

@Composable
fun VpnScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val activity = LocalContext.current as? MainActivity
    val scope = rememberCoroutineScope()
    val servers by graph.vpnServers.servers.collectAsState(initial = emptyList())
    val state by graph.vpn.state.collectAsState()
    val stats by graph.vpn.stats.collectAsState()
    val ip by graph.vpn.publicIp.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val c = Lumen.colors

    val countries = remember(servers) {
        servers.groupBy { it.countryCode }
            .map { (code, list) -> Country(code, countryName(code), list.sortedWith(compareByDescending<VpnServerEntity> { it.favorite }.thenBy { it.name })) }
            .sortedBy { it.name }
    }
    val connectedServer = (state as? VpnState.Connected)?.server ?: (state as? VpnState.Connecting)?.server
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(countries, connectedServer?.countryCode) {
        if (selectedCountry == null || countries.none { it.code == selectedCountry }) {
            selectedCountry = connectedServer?.countryCode ?: countries.firstOrNull()?.code
        }
    }
    var serverMenu by remember { mutableStateOf<VpnServerEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<VpnServerEntity?>(null) }
    var showUrlImport by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val powerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { powerFocus.requestFocus() } }

    fun connect(server: VpnServerEntity) {
        val a = activity ?: return
        a.ensureVpnPermission { granted ->
            if (granted) scope.launch { graph.vpn.connect(server) } else message = "Sin el permiso de Android no se puede activar la VPN."
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
    ) {
        TopBar(nav, Routes.VPN, title = "VPN · Proton (WireGuard)")
        Gap(height = 18.dp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 1) Estado
            GlassPanel(Modifier.width(300.dp).fillMaxHeight()) {
                StatusPanel(
                    state = state,
                    rx = stats?.rxBytes ?: 0,
                    tx = stats?.txBytes ?: 0,
                    lastHandshakeMs = stats?.lastHandshakeMs ?: 0,
                    ipText = ip?.let { "${it.ip} · ${it.country}" } ?: "—",
                    appOnly = settings.vpnAppOnly,
                    hasServers = servers.isNotEmpty(),
                    onToggle = {
                        if (state is VpnState.Connected || state is VpnState.Connecting) scope.launch { graph.vpn.disconnect() }
                        else activity?.ensureVpnPermission { ok -> if (ok) scope.launch { graph.vpn.quickConnect() } }
                    },
                    onQuick = { activity?.ensureVpnPermission { ok -> if (ok) scope.launch { graph.vpn.quickConnect() } } },
                    powerFocus = powerFocus,
                    onScope = { appOnly ->
                        scope.launch {
                            graph.settings.setVpnAppOnly(appOnly)
                            graph.vpn.reapplyScope()
                        }
                    },
                )
            }

            if (servers.isEmpty()) {
                NoServers(Modifier.weight(1f), onPhone = { nav.go(Routes.REMOTE_SETUP) }, onUrl = { showUrlImport = true })
            } else {
                // 2) Países
                Column(Modifier.width(230.dp).fillMaxHeight()) {
                    LText("PAÍSES", style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(6.dp)) {
                        items(countries, key = { it.code }) { country ->
                            val sel = country.code == selectedCountry
                            FocusSurface(
                                onClick = { selectedCountry = country.code },
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = if (sel) c.surfaceElevated else Color.Transparent,
                                focusedScale = 1.03f,
                                radius = 12.dp,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    LText(flagEmoji(country.code), style = Lumen.type.title)
                                    Column(Modifier.weight(1f)) {
                                        LText(country.name, style = Lumen.type.callout, maxLines = 1)
                                        LText("${country.servers.size} ${if (country.servers.size == 1) "servidor" else "servidores"}", style = Lumen.type.caption, color = c.textSecondary)
                                    }
                                    if (connectedServer?.countryCode == country.code) StatusDot(c.success)
                                }
                            }
                        }
                    }
                }

                // 3) Servidores
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val country = countries.firstOrNull { it.code == selectedCountry }
                    LText("SERVIDORES · ${country?.name?.uppercase() ?: ""}", style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(6.dp)) {
                        items(country?.servers.orEmpty(), key = { it.id }) { server ->
                            val isCurrent = connectedServer?.id == server.id
                            FocusSurface(
                                onClick = { if (!isCurrent) connect(server) },
                                modifier = Modifier.fillMaxWidth().onMenuKey { serverMenu = server },
                                containerColor = if (isCurrent) c.success.copy(alpha = 0.14f) else c.surface,
                                focusedScale = 1.02f,
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            LText(server.name, style = Lumen.type.headline, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                            if (server.isFree) Chip("Free", c.accent)
                                        }
                                        LText(server.endpoint.ifBlank { "Endpoint desconocido" }, style = Lumen.type.caption, color = c.textSecondary, maxLines = 1)
                                    }
                                    if (server.favorite) Icon(Icons.Rounded.Star, contentDescription = "Favorito", tint = c.warning, modifier = Modifier.size(18.dp))
                                    when {
                                        isCurrent && state is VpnState.Connected -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Conectado", tint = c.success)
                                        isCurrent -> LText("Conectando…", style = Lumen.type.caption, color = c.warning)
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        PillButton("Desde el móvil", onClick = { nav.go(Routes.REMOTE_SETUP) }, icon = Icons.Rounded.QrCode2)
                        PillButton("URL", onClick = { showUrlImport = true }, icon = Icons.Rounded.Link)
                    }
                }
            }
        }
        Gap(height = 8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyHints("OK" to "Conectar", "☰" to "Favorito / borrar", modifier = Modifier.weight(1f))
            message?.let { LText(it, style = Lumen.type.callout, color = c.warning) }
            (state as? VpnState.Failed)?.let { LText(it.message, style = Lumen.type.callout, color = c.danger) }
        }
    }

    serverMenu?.let { s ->
        OptionsDialog(s.name, listOf(
            MenuOption(if (s.favorite) "Quitar de favoritos" else "Marcar como favorito", if (s.favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star) {
                scope.launch { graph.vpnServers.setFavorite(s.id, !s.favorite) }
            },
            MenuOption("Eliminar servidor", Icons.Rounded.Delete) { confirmDelete = s },
        ), onDismiss = { serverMenu = null })
    }
    confirmDelete?.let { s ->
        ConfirmDialog("Eliminar ${s.name}", "Se borrará su configuración WireGuard de este dispositivo.", "Eliminar",
            onConfirm = { scope.launch { if (connectedServer?.id == s.id) graph.vpn.disconnect(); graph.vpnServers.delete(s.id) } },
            onDismiss = { confirmDelete = null })
    }
    if (showUrlImport) {
        UrlImportDialog(onDismiss = { showUrlImport = false }) { url ->
            scope.launch {
                message = runCatching { graph.vpnServers.importFromUrl(url) }
                    .fold({ r -> "Importados ${r.imported.size} servidores" + if (r.errors.isNotEmpty()) " (${r.errors.size} con error)" else "" }, { "Error: ${it.message}" })
            }
        }
    }
}

@Composable
private fun StatusPanel(
    state: VpnState,
    rx: Long,
    tx: Long,
    lastHandshakeMs: Long,
    ipText: String,
    appOnly: Boolean,
    hasServers: Boolean,
    onToggle: () -> Unit,
    onQuick: () -> Unit,
    onScope: (Boolean) -> Unit,
    powerFocus: FocusRequester,
) {
    val c = Lumen.colors
    // El túnel puede estar "arriba" sin que el servidor haya respondido (config caducada, red bloqueada…):
    // solo decimos "Conectado" tras el primer handshake WireGuard.
    val waitingMs = (state as? VpnState.Connected)?.let { System.currentTimeMillis() - it.since } ?: 0
    val (color, label) = when (state) {
        is VpnState.Connected -> when {
            lastHandshakeMs > 0 -> c.success to "Conectado"
            waitingMs < 15_000 -> c.warning to "Negociando…"
            else -> c.danger to "El servidor no responde"
        }
        is VpnState.Connecting -> c.warning to "Conectando…"
        is VpnState.Failed -> c.danger to "Error"
        VpnState.Disconnected -> c.textSecondary to "Desconectado"
    }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        while (state is VpnState.Connected) {
            elapsed = System.currentTimeMillis() - state.since
            delay(1000)
        }
    }
    Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        LText(label, style = Lumen.type.headline, color = color)
        if (state is VpnState.Connected) {
            LText(
                if (lastHandshakeMs == 0L && waitingMs >= 15_000) "Revisa que la configuración siga activa en tu cuenta Proton" else formatDuration(elapsed),
                style = Lumen.type.callout, color = c.textSecondary, maxLines = 2,
            )
        }
        FocusSurface(
            onClick = onToggle,
            modifier = Modifier.size(84.dp).focusRequester(powerFocus),
            radius = 42.dp,
            containerColor = color.copy(alpha = 0.16f),
            focusedContainerColor = color.copy(alpha = 0.3f),
            focusedScale = 1.08f,
        ) {
            Box(Modifier.fillMaxSize().border(2.dp, color.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Conectar o desconectar", tint = color, modifier = Modifier.size(38.dp))
            }
        }
        val server = (state as? VpnState.Connected)?.server ?: (state as? VpnState.Connecting)?.server ?: (state as? VpnState.Failed)?.server
        if (server != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LText(flagEmoji(server.countryCode), style = Lumen.type.title)
                Column {
                    LText(countryName(server.countryCode), style = Lumen.type.headline)
                    LText(server.name, style = Lumen.type.caption, color = c.textSecondary)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceElevated).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoRow("IP pública", ipText)
            InfoRow("Protocolo", "WireGuard")
            InfoRow("Tráfico", "↓ ${formatBytes(rx)}  ↑ ${formatBytes(tx)}")
        }
        if (hasServers && state !is VpnState.Connected) {
            PillButton("Conexión rápida", onClick = onQuick, icon = Icons.Rounded.Bolt, primary = true)
        }
        LText("ÁMBITO DEL TÚNEL", style = Lumen.type.caption, color = c.textSecondary)
        Row(Modifier.clip(RoundedCornerShape(50)).background(c.surfaceElevated).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ScopeSegment("Solo IPTV", selected = appOnly) { onScope(true) }
            ScopeSegment("Todo el Fire TV", selected = !appOnly) { onScope(false) }
        }
    }
}

@Composable
private fun ScopeSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Lumen.colors
    FocusSurface(
        onClick = onClick,
        radius = 50.dp,
        containerColor = if (selected) c.accent else Color.Transparent,
        focusedContainerColor = if (selected) c.accent else c.surfaceFocused,
        contentColor = if (selected) c.onAccent else c.textPrimary,
        focusedContentColor = if (selected) c.onAccent else c.textPrimary,
        focusedScale = 1.04f,
    ) { LText(label, style = Lumen.type.caption, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        LText(label, style = Lumen.type.caption, color = Lumen.colors.textSecondary, modifier = Modifier.weight(1f))
        LText(value, style = Lumen.type.caption, color = Lumen.colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun NoServers(modifier: Modifier, onPhone: () -> Unit, onUrl: () -> Unit) {
    val c = Lumen.colors
    Column(modifier.fillMaxHeight().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.accent, modifier = Modifier.size(44.dp))
        Gap(height = 12.dp)
        LText("Añade tus servidores de Proton VPN", style = Lumen.type.title)
        Gap(height = 10.dp)
        listOf(
            "1. En el móvil o PC entra en account.protonvpn.com → Descargas → Configuración WireGuard.",
            "2. Elige plataforma «Android», el país y el servidor (los gratuitos llevan FREE) y pulsa Crear.",
            "3. Descarga un .conf por cada servidor que quieras tener en la tele.",
            "4. Pulsa «Añadir desde el móvil», escanea el QR y súbelos. Aparecerán aquí agrupados por país.",
        ).forEach { LText(it, style = Lumen.type.body, color = c.textSecondary) }
        Gap(height = 18.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Añadir desde el móvil", onClick = onPhone, icon = Icons.Rounded.QrCode2, primary = true)
            PillButton("Importar desde URL", onClick = onUrl, icon = Icons.Rounded.Link)
        }
    }
}

@Composable
private fun UrlImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(Modifier.width(520.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LText("Importar configuración desde URL", style = Lumen.type.headline)
                LText("Un .conf, o varios en un único texto separados por líneas «### nombre.conf» (p. ej. un Gist privado).", style = Lumen.type.callout, color = Lumen.colors.textSecondary)
                TvTextField(url, { url = it }, "URL", placeholder = "https://…", keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Importar", onClick = { if (url.isNotBlank()) { onImport(url); onDismiss() } }, primary = true)
                    PillButton("Cancelar", onClick = onDismiss)
                }
            }
        }
    }
}
