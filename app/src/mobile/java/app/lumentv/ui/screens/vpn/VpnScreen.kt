package app.lumentv.ui.screens.vpn

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.lumentv.MobileActivity
import app.lumentv.core.util.countryName
import app.lumentv.core.util.flagEmoji
import app.lumentv.core.util.formatBytes
import app.lumentv.core.util.formatDuration
import app.lumentv.data.db.VpnServerEntity
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.ConfirmDialog
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.GroupCard
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.NoticePill
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.SectionTitle
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.SwitchRow
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TextInputSheet
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import app.lumentv.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Country(val code: String, val name: String, val servers: List<VpnServerEntity>)

/**
 * VPN (Stitch 07-vpn / L07-vpn). Misma lógica que la TV (`VpnManager`, un solo túnel; «Conectado»
 * solo tras el primer handshake), con la importación propia del móvil: selector de ficheros,
 * «Compartir»/«Abrir con» desde otra app (ver [ConfImport]) o una URL.
 *
 * - Vertical: estado → ámbito → países; tocar un país abre sus servidores (Atrás vuelve).
 * - Horizontal y tableta: estado · países · servidores.
 */
@Composable
fun VpnScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val activity = context as? MobileActivity
    val widthClass = LocalWidthClass.current
    val wide = widthClass != WidthClass.Compact
    val servers by graph.vpnServers.servers.collectAsState(initial = null)
    val state by graph.vpn.state.collectAsState()
    val stats by graph.vpn.stats.collectAsState()
    val ip by graph.vpn.publicIp.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val incoming by ConfImport.notice.collectAsState()

    val countries = remember(servers) {
        servers.orEmpty().groupBy { it.countryCode }
            .map { (code, list) -> Country(code, countryName(code), list.sortedWith(compareByDescending<VpnServerEntity> { it.favorite }.thenBy { it.name })) }
            .sortedBy { it.name }
    }
    val activeServer = (state as? VpnState.Connected)?.server ?: (state as? VpnState.Connecting)?.server
    var selectedCountry by rememberSaveable { mutableStateOf<String?>(null) }
    // Solo en vertical: la lista de servidores es una página aparte.
    var serversOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(countries, activeServer?.countryCode) {
        if (selectedCountry == null || countries.none { it.code == selectedCountry }) {
            selectedCountry = activeServer?.countryCode ?: countries.firstOrNull()?.code
            if (selectedCountry == null) serversOpen = false
        }
    }
    BackHandler(enabled = !wide && serversOpen) { serversOpen = false }

    var serverMenu by remember { mutableStateOf<VpnServerEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<VpnServerEntity?>(null) }
    var urlImport by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<ConfImport.Notice?>(null) }
    LaunchedEffect(incoming) {
        incoming?.let { notice = it; ConfImport.consume() }
    }
    // La IP pública se consulta al conectar y desconectar; si aún no se sabe, al abrir la pantalla.
    LaunchedEffect(Unit) { if (ip == null) graph.vpn.refreshPublicIp() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) graph.appScope.launch { ConfImport.post(ConfImport.import(context.applicationContext, graph, uris)) }
    }
    // Muchos gestores de archivos no saben el tipo MIME de un .conf: se aceptan todos y se valida al leer.
    val pickFiles = { picker.launch(arrayOf("*/*")) }

    /** Conectar y desconectar van en appScope: deben terminar aunque se cierre la pantalla. */
    fun withPermission(action: suspend () -> Unit) {
        val a = activity ?: return
        a.ensureVpnPermission { granted ->
            if (granted) graph.appScope.launch { action() }
            else notice = ConfImport.Notice("Sin el permiso de Android no se puede activar la VPN.", error = true)
        }
    }
    fun connect(server: VpnServerEntity) = withPermission { graph.vpn.connect(server) }
    fun toggle() {
        if (state is VpnState.Connected || state is VpnState.Connecting) graph.appScope.launch { graph.vpn.disconnect() }
        else withPermission { graph.vpn.quickConnect() }
    }
    fun setScope(appOnly: Boolean) {
        graph.appScope.launch {
            graph.settings.setVpnAppOnly(appOnly)
            graph.vpn.reapplyScope()
        }
    }

    val statusCard: @Composable () -> Unit = {
        StatusCard(
            state = state,
            rx = stats?.rxBytes ?: 0,
            tx = stats?.txBytes ?: 0,
            lastHandshakeMs = stats?.lastHandshakeMs ?: 0,
            ipText = ip?.let { listOf(it.ip, it.country).filter(String::isNotBlank).joinToString(" · ") } ?: "—",
            hasServers = !servers.isNullOrEmpty(),
            onToggle = ::toggle,
        )
    }
    val scopeCard: @Composable () -> Unit = {
        GroupCard {
            SwitchRow(
                title = "Solo esta app",
                checked = settings.vpnAppOnly,
                onCheckedChange = ::setScope,
                subtitle = if (settings.vpnAppOnly) "Solo el tráfico de IPTV pasa por la VPN; el resto del móvil sale como siempre."
                else "Todo el tráfico del móvil pasa por la VPN.",
            )
        }
    }
    val importButtons: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Importar .conf", onClick = pickFiles, icon = Icons.Rounded.UploadFile, modifier = Modifier.weight(1f))
            PillButton("Desde URL", onClick = { urlImport = true }, icon = Icons.Rounded.Link, modifier = Modifier.weight(1f))
        }
    }
    val countryList: @Composable (Modifier, Boolean) -> Unit = { modifier, asPage ->
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { SectionTitle("Países", trailing = { LText("${countries.size}", style = Lumen.type.caption, color = Lumen.colors.textTertiary) }) }
            items(countries, key = { it.code }) { country ->
                CountryRow(
                    country = country,
                    selected = !asPage && country.code == selectedCountry,
                    active = activeServer?.countryCode == country.code,
                    chevron = asPage,
                    onClick = { selectedCountry = country.code; if (asPage) serversOpen = true },
                )
            }
        }
    }
    val serverList: @Composable (Modifier, Boolean) -> Unit = { modifier, showTitle ->
        val country = countries.firstOrNull { it.code == selectedCountry }
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (showTitle) item { SectionTitle("Servidores · ${country?.name.orEmpty()}") }
            items(country?.servers.orEmpty(), key = { it.id }) { server ->
                ServerRow(
                    server = server,
                    current = activeServer?.id == server.id,
                    connected = state is VpnState.Connected,
                    onClick = { if (activeServer?.id != server.id) connect(server) },
                    onMenu = { serverMenu = server },
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            val pageCountry = countries.firstOrNull { it.code == selectedCountry }?.takeIf { !wide && serversOpen }
            Box(Modifier.padding(horizontal = 16.dp)) {
                TopBar(
                    nav,
                    pageCountry?.let { "${flagEmoji(it.code)} ${it.name}" } ?: "VPN",
                    showBack = true,
                    showActions = false,
                    onBack = if (pageCountry != null) ({ serversOpen = false }) else null,
                )
            }
            when {
                servers == null -> Unit // Primera lectura de Room: un instante.
                wide -> Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.width(if (widthClass == WidthClass.Expanded) 340.dp else 300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        statusCard()
                        scopeCard()
                        if (countries.isNotEmpty()) importButtons()
                    }
                    if (countries.isEmpty()) {
                        NoServers(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), onFiles = pickFiles, onUrl = { urlImport = true })
                    } else {
                        countryList(Modifier.weight(0.8f).fillMaxHeight(), false)
                        serverList(Modifier.weight(1.2f).fillMaxHeight(), true)
                    }
                }
                pageCountry != null -> serverList(Modifier.weight(1f).padding(horizontal = 16.dp), false)
                else -> LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item { statusCard() }
                    item { scopeCard() }
                    if (countries.isEmpty()) {
                        item { NoServers(Modifier, onFiles = pickFiles, onUrl = { urlImport = true }) }
                    } else {
                        item { SectionTitle("Países", Modifier.padding(top = 8.dp), trailing = { LText("${countries.size}", style = Lumen.type.caption, color = Lumen.colors.textTertiary) }) }
                        items(countries, key = { it.code }) { country ->
                            CountryRow(country, selected = false, active = activeServer?.countryCode == country.code, chevron = true) {
                                selectedCountry = country.code
                                serversOpen = true
                            }
                        }
                        item { Box(Modifier.padding(top = 8.dp)) { importButtons() } }
                    }
                }
            }
        }
        NoticePill(notice?.text, onShown = { notice = null }, error = notice?.error == true, modifier = Modifier.align(Alignment.BottomCenter))
    }

    serverMenu?.let { s ->
        val isCurrent = activeServer?.id == s.id
        OptionsSheet(
            s.name,
            listOfNotNull(
                if (isCurrent) SheetOption("Desconectar", Icons.Rounded.PowerSettingsNew) { graph.appScope.launch { graph.vpn.disconnect() } }
                else SheetOption("Conectar", Icons.Rounded.Bolt) { connect(s) },
                SheetOption(if (s.favorite) "Quitar de favoritos" else "Marcar como favorito", if (s.favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star) {
                    graph.appScope.launch { graph.vpnServers.setFavorite(s.id, !s.favorite) }
                },
                SheetOption("Eliminar servidor", Icons.Rounded.Delete, destructive = true) { confirmDelete = s },
            ),
            onDismiss = { serverMenu = null },
            subtitle = "${countryName(s.countryCode)} · ${s.endpoint.ifBlank { "endpoint desconocido" }}",
        )
    }
    confirmDelete?.let { s ->
        ConfirmDialog(
            "Eliminar ${s.name}",
            "Se borrará su configuración WireGuard de este móvil.",
            "Eliminar",
            onConfirm = {
                graph.appScope.launch {
                    if (activeServer?.id == s.id) graph.vpn.disconnect()
                    graph.vpnServers.delete(s.id)
                }
            },
            onDismiss = { confirmDelete = null },
            destructive = true,
        )
    }
    if (urlImport) {
        TextInputSheet(
            title = "Importar desde una URL",
            initial = "",
            label = "URL",
            placeholder = "https://…",
            message = "Un .conf, o varios en un único texto separados por líneas «### nombre.conf» (por ejemplo, un Gist privado).",
            saveLabel = "Importar",
            keyboardType = KeyboardType.Uri,
            onDismiss = { urlImport = false },
            onSave = { url ->
                if (url.isNotBlank()) graph.appScope.launch {
                    ConfImport.post(
                        runCatching { ConfImport.describe(graph.vpnServers.importFromUrl(url)) }
                            .getOrElse { ConfImport.Notice("No se pudo descargar: ${it.message ?: it.javaClass.simpleName}", error = true) },
                    )
                }
            },
        )
    }
}

@Composable
private fun StatusCard(
    state: VpnState,
    rx: Long,
    tx: Long,
    lastHandshakeMs: Long,
    ipText: String,
    hasServers: Boolean,
    onToggle: () -> Unit,
) {
    val c = Lumen.colors
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        elapsed = 0
        while (state is VpnState.Connected) {
            elapsed = System.currentTimeMillis() - state.since
            delay(1_000)
        }
    }
    // El túnel puede estar «arriba» sin que el servidor responda (configuración caducada, red que
    // bloquea WireGuard…): solo se dice «Conectado» tras el primer handshake.
    val (color, label) = when (state) {
        is VpnState.Connected -> when {
            lastHandshakeMs > 0 -> c.success to "Conectado"
            elapsed < 15_000 -> c.warning to "Negociando…"
            else -> c.danger to "El servidor no responde"
        }
        is VpnState.Connecting -> c.warning to "Conectando…"
        is VpnState.Failed -> c.danger to "No se pudo conectar"
        VpnState.Disconnected -> c.textSecondary to "Desconectada"
    }
    val detail = when (state) {
        is VpnState.Connected -> if (lastHandshakeMs == 0L && elapsed >= 15_000) "Revisa que la configuración siga activa en tu cuenta de Proton." else "Túnel WireGuard · ${formatDuration(elapsed)}"
        is VpnState.Connecting -> "Levantando el túnel WireGuard"
        is VpnState.Failed -> state.message
        VpnState.Disconnected -> if (hasServers) "El tráfico sale sin cifrar por tu conexión." else "Importa un servidor de Proton para empezar."
    }
    val server = (state as? VpnState.Connected)?.server ?: (state as? VpnState.Connecting)?.server ?: (state as? VpnState.Failed)?.server
    val on = state is VpnState.Connected || state is VpnState.Connecting

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(LumenDimens.radiusLarge)).background(c.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)).border(1.5.dp, color.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Shield, contentDescription = null, tint = color, modifier = Modifier.size(28.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LText(label, style = Lumen.type.title, color = color, maxLines = 1)
                LText(detail, style = Lumen.type.callout, color = c.textSecondary, maxLines = 3)
            }
        }
        if (state is VpnState.Failed) {
            // Android solo permite una VPN a la vez: es el fallo más habitual en un móvil.
            LText(
                "Si tienes otra VPN activa (la app de Proton, «VPN siempre activa»…), desactívala e inténtalo de nuevo.",
                style = Lumen.type.callout, color = c.textTertiary,
            )
        }
        if (server != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(LumenDimens.radiusSmall)).background(c.surfaceElevated).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LText(flagEmoji(server.countryCode), style = Lumen.type.title)
                Column(Modifier.weight(1f)) {
                    LText(server.name, style = Lumen.type.headline, maxLines = 1)
                    LText(countryName(server.countryCode), style = Lumen.type.callout, color = c.textSecondary, maxLines = 1)
                }
                if (server.isFree) Chip("Free")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("IP pública", ipText, Modifier.weight(1f))
            }
            if (state is VpnState.Connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Descargado", formatBytes(rx), Modifier.weight(1f))
                    StatTile("Enviado", formatBytes(tx), Modifier.weight(1f))
                }
            }
        }
        if (on) {
            PowerButton("Desconectar", color = c.danger, onClick = onToggle)
        } else if (hasServers) {
            PillButton(if (state is VpnState.Failed) "Reintentar" else "Conexión rápida", onClick = onToggle, icon = Icons.Rounded.Bolt, primary = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    Column(
        modifier.clip(RoundedCornerShape(LumenDimens.radiusSmall)).background(c.surfaceElevated).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LText(label.uppercase(), style = Lumen.type.caption, color = c.textSecondary, maxLines = 1)
        LText(value, style = Lumen.type.headline, maxLines = 1)
    }
}

/** Botón de apagado: contorno y texto en [color] (rojo para desconectar), como en Stitch. */
@Composable
private fun PowerButton(text: String, color: Color, onClick: () -> Unit) {
    TapSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)),
        radius = 50.dp,
        color = color.copy(alpha = 0.12f),
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            LText(text, style = Lumen.type.headline, color = color)
        }
    }
}

@Composable
private fun CountryRow(country: Country, selected: Boolean, active: Boolean, chevron: Boolean, onClick: () -> Unit) {
    val c = Lumen.colors
    TapSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (selected) Modifier.border(1.dp, c.accent.copy(alpha = 0.6f), RoundedCornerShape(LumenDimens.radiusMedium)) else Modifier),
        color = if (selected) c.surfaceElevated else c.surface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LText(flagEmoji(country.code), style = Lumen.type.largeTitle)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LText(country.name, style = Lumen.type.headline, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (active) Chip("Actual")
                }
                LText(
                    "${country.servers.size} ${if (country.servers.size == 1) "servidor" else "servidores"}",
                    style = Lumen.type.callout, color = c.textSecondary,
                )
            }
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textTertiary)
        }
    }
}

@Composable
private fun ServerRow(server: VpnServerEntity, current: Boolean, connected: Boolean, onClick: () -> Unit, onMenu: () -> Unit) {
    val c = Lumen.colors
    TapSurface(
        onClick = onClick,
        onLongClick = onMenu,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        color = if (current) c.success.copy(alpha = 0.12f) else c.surface,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LText(server.name, style = Lumen.type.headline, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (server.isFree) Chip("Free")
                }
                LText(
                    when {
                        current && connected -> "Conectado"
                        current -> "Conectando…"
                        else -> server.endpoint.ifBlank { "Endpoint desconocido" }
                    },
                    style = Lumen.type.callout,
                    color = when {
                        current && connected -> c.success
                        current -> c.warning
                        else -> c.textSecondary
                    },
                    maxLines = 1,
                )
            }
            if (server.favorite) Icon(Icons.Rounded.Star, contentDescription = "Favorito", tint = c.warning, modifier = Modifier.size(18.dp))
            if (current && connected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.success, modifier = Modifier.size(22.dp))
            IconCircleButton(Icons.Rounded.MoreVert, "Opciones de ${server.name}", onClick = onMenu, color = Color.Transparent)
        }
    }
}

@Composable
private fun NoServers(modifier: Modifier, onFiles: () -> Unit, onUrl: () -> Unit) {
    val c = Lumen.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(LumenDimens.radiusLarge)).background(c.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.accent, modifier = Modifier.size(36.dp))
        LText("Añade tus servidores de Proton VPN", style = Lumen.type.title)
        listOf(
            "1. Entra en account.protonvpn.com → Descargas → Configuración de WireGuard.",
            "2. Elige la plataforma «Android», el país y el servidor (los gratuitos llevan FREE) y pulsa Crear.",
            "3. Descarga el .conf y pulsa «Importar .conf». También puedes abrirlo o compartirlo con IPTV desde Archivos o el navegador.",
        ).forEach { LText(it, style = Lumen.type.body, color = c.textSecondary) }
        Gap(height = 6.dp)
        PillButton("Importar .conf", onClick = onFiles, icon = Icons.Rounded.UploadFile, primary = true, modifier = Modifier.fillMaxWidth())
        PillButton("Importar desde URL", onClick = onUrl, icon = Icons.Rounded.Link, modifier = Modifier.fillMaxWidth())
    }
}
