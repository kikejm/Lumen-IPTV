package app.lumentv.ui.screens.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lumentv.core.util.TimeFormatter
import app.lumentv.core.util.relativeAge
import app.lumentv.data.catalog.SyncState
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.ConfirmDialog
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.SectionTitle
import app.lumentv.ui.components.StatusDot
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.UpdateBanner
import app.lumentv.ui.components.rememberUpdateInstaller
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.playChannels
import app.lumentv.ui.resume
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import app.lumentv.vpn.VpnState
import kotlinx.coroutines.launch

/** Tintes de los accesos (DESIGN.md: solo como fondo al 18 % del icono). */
private object TileTint {
    val live = Color(0xFF0A84FF)
    val movies = Color(0xFFFF375F)
    val series = Color(0xFFBF5AF2)
    val guide = Color(0xFF30D158)
    val multiview = Color(0xFF5E5CE6)
    val vpn = Color(0xFF64D2FF)
}

private data class Tile(val label: String, val caption: String, val icon: ImageVector, val tint: Color, val route: String, val captionColor: Color? = null)

/**
 * Inicio (Stitch 01-inicio / L01-inicio / T01-inicio): «Sigue viendo», accesos, favoritos y pie
 * con el estado de la lista. Datos reales de Room; las tarjetas reanudan como en la TV.
 */
@Composable
fun HomeScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = LocalWidthClass.current
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val settings by graph.settings.settings.collectAsState()
    val sync by graph.catalog.syncState.collectAsState()
    val vpn by graph.vpn.state.collectAsState()
    val update by graph.updateNotice.collectAsState()
    val installer = rememberUpdateInstaller()
    val pid = playlist?.id ?: -1L

    val history by remember(pid) { graph.library.history(pid, 12) }.collectAsState(initial = emptyList())
    val favIds by remember(pid) { graph.library.favoriteIds(pid, ContentKind.LIVE) }.collectAsState(initial = emptySet())
    val channelCount by remember(pid) { graph.catalog.channelCount(pid) }.collectAsState(initial = 0)
    val movieCount by remember(pid) { graph.catalog.movieCount(pid) }.collectAsState(initial = 0)
    val seriesCount by remember(pid) { graph.catalog.seriesCount(pid) }.collectAsState(initial = 0)

    // Canales favoritos resueltos desde Room (la lista de ids cambia poco).
    val favoriteChannels = remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    LaunchedEffect(pid, favIds) {
        favoriteChannels.value = if (favIds.isEmpty()) emptyList() else graph.catalog.channelsByIds(pid, favIds.toList())
    }

    val c = Lumen.colors
    val vpnOn = vpn is VpnState.Connected
    val tiles = listOf(
        Tile("TV Directo", "$channelCount canales", Icons.Rounded.LiveTv, TileTint.live, Routes.LIVE),
        Tile("Cine", "$movieCount películas", Icons.Rounded.Movie, TileTint.movies, Routes.MOVIES),
        Tile("Series", "$seriesCount series", Icons.Rounded.VideoLibrary, TileTint.series, Routes.SERIES),
        Tile("Guía EPG", "Parrilla de hoy", Icons.AutoMirrored.Rounded.ViewList, TileTint.guide, Routes.GUIDE),
        Tile("Multipantalla", "Varios canales", Icons.Rounded.GridView, TileTint.multiview, Routes.MULTIVIEW),
        Tile("VPN", if (vpnOn) "● Conectada" else "Desconectada", Icons.Rounded.Shield, TileTint.vpn, Routes.VPN, captionColor = if (vpnOn) c.success else null),
    )
    val openTile: (Tile) -> Unit = { t ->
        if (t.route in listOf(Routes.LIVE, Routes.MOVIES, Routes.SERIES, Routes.GUIDE)) nav.replaceRoot(t.route) else nav.go(t.route)
    }

    // Inicio es la raíz: Atrás no sale a la primera, pregunta antes (como la TV, que pide pulsarlo dos veces).
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }
    if (confirmExit) {
        ConfirmDialog(
            title = "¿Estás seguro de que quieres salir?",
            message = null,
            confirmLabel = "Salir",
            onConfirm = { (context as? Activity)?.finish() },
            onDismiss = { confirmExit = false },
        )
    }

    val last = history.firstOrNull()
    val continueSection: @Composable () -> Unit = {
        Column {
            SectionTitle("Sigue viendo")
            if (last != null) {
                ContinueCard(last, settings.use24h) { playlist?.let { p -> scope.launch { graph.resume(context, nav, p, last) } } }
            } else {
                WelcomeCard(sync) { nav.replaceRoot(Routes.LIVE) }
            }
        }
    }
    val tilesSection: @Composable (perRow: Int) -> Unit = { perRow ->
        Column {
            SectionTitle("Accesos")
            TileGrid(tiles, perRow, openTile)
        }
    }
    val favoritesSection: @Composable () -> Unit = {
        val rowChannels = favoriteChannels.value
        val recentLive = history.filter { it.kind == ContentKind.LIVE }
        if (rowChannels.isNotEmpty()) {
            Column {
                SectionTitle("Mis favoritos")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rowChannels, key = { it.streamId }) { ch -> ChannelCard(ch.name, ch.logo) { playlist?.let { p -> graph.playChannels(context, nav, p, rowChannels, rowChannels.indexOf(ch)) } } }
                }
            }
        } else if (recentLive.isNotEmpty()) {
            Column {
                SectionTitle("Vistos recientemente")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentLive, key = { it.itemId }) { h -> ChannelCard(h.title, h.image) { playlist?.let { p -> scope.launch { graph.resume(context, nav, p, h) } } } }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TopBar(nav, "IPTV")
        update?.let { UpdateBanner(it, installer, onDismiss = graph::dismissUpdateNotice) }
        when (widthClass) {
            // Vertical: una columna (Stitch 01-inicio).
            WidthClass.Compact -> {
                continueSection()
                tilesSection(2)
                favoritesSection()
            }
            // Horizontal: «Sigue viendo» a la izquierda; accesos y favoritos a la derecha (L01-inicio).
            WidthClass.Medium -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(0.45f)) { continueSection() }
                Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    tilesSection(3)
                    favoritesSection()
                }
            }
            // Tableta: todo a lo ancho, accesos en una sola fila (T01-inicio).
            WidthClass.Expanded -> {
                continueSection()
                tilesSection(6)
                favoritesSection()
            }
        }
        Footer(playlist, sync, onRefresh = { playlist?.let { p -> scope.launch { graph.syncPlaylist(p.id) } } })
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TileGrid(tiles: List<Tile>, perRow: Int, onClick: (Tile) -> Unit) {
    val c = Lumen.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t ->
                    TapSurface(onClick = { onClick(t) }, modifier = Modifier.weight(1f).heightIn(min = 76.dp), radius = LumenDimens.radiusMedium) {
                        // Alto mínimo, no fijo: con letra grande del sistema la fila crece en vez de cortar.
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(t.tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Icon(t.icon, contentDescription = null, tint = t.tint, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                LText(t.label, style = Lumen.type.headline, maxLines = 1)
                                LText(t.caption, style = Lumen.type.callout, color = t.captionColor ?: c.textSecondary, maxLines = 1)
                            }
                        }
                    }
                }
                // Huecos para que la última fila no estire sus mosaicos.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ContinueCard(h: HistoryEntity, use24h: Boolean, onClick: () -> Unit) {
    val c = Lumen.colors
    TapSurface(onClick = onClick, modifier = Modifier.fillMaxWidth(), radius = LumenDimens.radiusMedium) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Artwork(
                h.image, h.title, Modifier.fillMaxSize(), radius = 0.dp,
                contentScale = if (h.kind == ContentKind.LIVE) ContentScale.Fit else ContentScale.Crop,
            )
            // Degradado para que el texto se lea sobre cualquier carátula (overlay de vídeo: negro).
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Box(Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Reanudar", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(when (h.kind) { ContentKind.LIVE -> "Canal"; ContentKind.VOD -> "Película"; else -> "Serie" }, c.accent)
                LText(h.title, style = Lumen.type.title, color = Color.White, maxLines = 1)
                if (h.subtitle.isNotBlank()) LText(h.subtitle, style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                if (h.durationMs > 0) {
                    ProgressLine(h.positionMs.toFloat() / h.durationMs)
                } else {
                    LText("Visto ${relativeAge(h.watchedAt)} · ${TimeFormatter(use24h).time(h.watchedAt)}", style = Lumen.type.caption, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(sync: SyncState, onStart: () -> Unit) {
    val c = Lumen.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LumenDimens.radiusLarge))
            .background(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.35f), c.surface)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LText("Bienvenido a IPTV", style = Lumen.type.title)
        val msg = when (sync) {
            is SyncState.Running -> "Cargando ${sync.step.lowercase()}… ${if (sync.count > 0) "(${sync.count})" else ""}"
            is SyncState.Failed -> "No se pudo actualizar la lista: ${sync.message}"
            else -> "Elige un canal y lo verás aquí para retomarlo con un toque."
        }
        LText(msg, style = Lumen.type.body, color = c.textSecondary)
        Gap(height = 4.dp)
        PillButton("Ver TV en directo", onClick = onStart, primary = true, icon = Icons.Rounded.LiveTv)
    }
}

@Composable
private fun ChannelCard(name: String, logo: String, onClick: () -> Unit) {
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TapSurface(onClick = onClick, modifier = Modifier.size(92.dp), radius = LumenDimens.radiusMedium) {
            Artwork(logo, name, Modifier.fillMaxSize().padding(14.dp), radius = 0.dp, background = Color.Transparent)
        }
        Gap(height = 6.dp)
        LText(name, style = Lumen.type.callout, maxLines = 1)
    }
}

@Composable
private fun Footer(p: PlaylistEntity?, sync: SyncState, onRefresh: () -> Unit) {
    val c = Lumen.colors
    val running = sync is SyncState.Running
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            val expiry = p?.expiresAt?.takeIf { it > 0 }?.let { " · expira ${TimeFormatter(true).date(it)}" }.orEmpty()
            LText("Lista «${p?.name ?: "—"}»$expiry", style = Lumen.type.callout, color = c.textSecondary, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(
                    when (sync) {
                        is SyncState.Running -> c.warning
                        is SyncState.Failed -> c.danger
                        else -> c.success
                    },
                    size = 6.dp,
                )
                val status = when (sync) {
                    is SyncState.Running -> "Actualizando ${sync.step.lowercase()}…"
                    is SyncState.Failed -> "Error al actualizar"
                    else -> "Actualizada ${relativeAge(p?.lastSyncAt ?: 0)}"
                }
                LText(status, style = Lumen.type.caption, color = c.textTertiary, maxLines = 1)
            }
        }
        PillButton("Actualizar", onClick = onRefresh, icon = Icons.Rounded.Refresh, enabled = !running)
    }
}
