package app.lumentv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import app.lumentv.core.util.TimeFormatter
import app.lumentv.core.util.relativeAge
import app.lumentv.data.catalog.SyncState
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.SectionTitle
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.playChannels
import app.lumentv.ui.resume
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.launch

private data class Tile(val label: String, val caption: String, val icon: ImageVector, val tint: Color, val route: String)

@Composable
fun HomeScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val settings by graph.settings.settings.collectAsState()
    val sync by graph.catalog.syncState.collectAsState()
    val update by graph.availableUpdate.collectAsState()
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

    val firstTile = remember { FocusRequester() }
    val c = Lumen.colors
    val tiles = listOf(
        Tile("TV en directo", "$channelCount canales", Icons.Rounded.LiveTv, Color(0xFF0A84FF), Routes.LIVE),
        Tile("Cine", "$movieCount películas", Icons.Rounded.Movie, Color(0xFFFF375F), Routes.MOVIES),
        Tile("Series", "$seriesCount series", Icons.Rounded.VideoLibrary, Color(0xFFBF5AF2), Routes.SERIES),
        Tile("Guía EPG", "Parrilla de programación", Icons.AutoMirrored.Rounded.ViewList, Color(0xFF30D158), Routes.GUIDE),
        Tile("Multipantalla", "Hasta 4 señales", Icons.Rounded.GridView, Color(0xFF5E5CE6), Routes.MULTIVIEW),
        Tile("Catch-up", "Programas pasados", Icons.Rounded.History, Color(0xFFFF9F0A), Routes.CATCHUP),
    )

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item { TopBar(nav, Routes.HOME) }

            if (update != null) item {
                FocusSurface(onClick = { nav.go(Routes.SETTINGS) }, containerColor = c.accent.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = c.accent)
                        LText("Actualización disponible: Lumen ${update?.versionName}. Pulsa para instalarla.", style = Lumen.type.callout)
                    }
                }
            }

            item {
                val last = history.firstOrNull()
                Column {
                    SectionTitle("Sigue viendo")
                    if (last != null && playlist != null) {
                        ContinueCard(last, settings.use24h) { scope.launch { graph.resume(context, nav, playlist!!, last) } }
                    } else {
                        WelcomeCard(sync) { nav.replaceRoot(Routes.LIVE) }
                    }
                }
            }

            item {
                Column {
                    SectionTitle("Accesos")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        tiles.forEachIndexed { i, t ->
                            FocusSurface(
                                onClick = { if (t.route in listOf(Routes.LIVE, Routes.MOVIES, Routes.SERIES, Routes.GUIDE)) nav.replaceRoot(t.route) else nav.go(t.route) },
                                modifier = Modifier.weight(1f).height(108.dp).then(if (i == 0) Modifier.focusRequester(firstTile) else Modifier),
                                radius = LumenDimens.radiusLarge,
                            ) {
                                Column(Modifier.padding(14.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                    Box(
                                        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(t.tint.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(t.icon, contentDescription = null, tint = t.tint, modifier = Modifier.size(20.dp)) }
                                    Column {
                                        LText(t.label, style = Lumen.type.callout.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), maxLines = 1)
                                        LText(t.caption, style = Lumen.type.caption, color = c.textSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val rowChannels = favoriteChannels.value
            val recentLive = history.filter { it.kind == ContentKind.LIVE }
            if (rowChannels.isNotEmpty() && playlist != null) item {
                Column {
                    SectionTitle("Canales favoritos")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        items(rowChannels, key = { it.streamId }) { ch ->
                            ChannelCard(ch) { graph.playChannels(context, nav, playlist!!, rowChannels, rowChannels.indexOf(ch)) }
                        }
                    }
                }
            } else if (recentLive.isNotEmpty()) item {
                Column {
                    SectionTitle("Vistos recientemente")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        items(recentLive, key = { it.itemId }) { h ->
                            HistoryCard(h) { scope.launch { playlist?.let { graph.resume(context, nav, it, h) } } }
                        }
                    }
                }
            }

            item { Footer(playlist, sync, onRefresh = { playlist?.let { p -> scope.launch { graph.syncPlaylist(p.id) } } }) }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstTile.requestFocus() } }
}

@Composable
private fun ContinueCard(h: HistoryEntity, use24h: Boolean, onResume: () -> Unit) {
    val c = Lumen.colors
    FocusSurface(onClick = onResume, modifier = Modifier.fillMaxWidth(), radius = LumenDimens.radiusLarge, focusedScale = 1.02f) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.width(200.dp).aspectRatio(16f / 9f)) {
                Artwork(h.image, h.title, Modifier.fillMaxSize(), contentScale = if (h.kind == ContentKind.LIVE) ContentScale.Fit else ContentScale.Crop)
                if (h.kind == ContentKind.LIVE) Chip("● En directo", c.danger, filled = true, modifier = Modifier.padding(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(
                    when (h.kind) { ContentKind.LIVE -> "Canal"; ContentKind.VOD -> "Película"; else -> "Serie" },
                    c.accent,
                )
                LText(h.title, style = Lumen.type.title, maxLines = 1)
                if (h.subtitle.isNotBlank()) LText(h.subtitle, style = Lumen.type.body, color = c.textSecondary, maxLines = 1)
                if (h.durationMs > 0) {
                    ProgressLine(h.positionMs.toFloat() / h.durationMs, Modifier.width(320.dp))
                } else {
                    LText("Visto ${relativeAge(h.watchedAt)} · ${TimeFormatter(use24h).time(h.watchedAt)}", style = Lumen.type.caption, color = c.textSecondary)
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (c.isDark) Color.White else c.accent).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = if (c.isDark) Color.Black else Color.White)
                Gap(width = 6.dp)
                LText("Reanudar", style = Lumen.type.callout, color = if (c.isDark) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun WelcomeCard(sync: SyncState, onStart: () -> Unit) {
    val c = Lumen.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(LumenDimens.radiusLarge))
            .background(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.35f), c.surface)))
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LText("Bienvenido a Lumen", style = Lumen.type.largeTitle)
            val msg = when (sync) {
                is SyncState.Running -> "Cargando ${sync.step.lowercase()}… ${if (sync.count > 0) "(${sync.count})" else ""}"
                is SyncState.Failed -> "No se pudo actualizar la lista: ${sync.message}"
                else -> "Elige un canal y lo verás aquí para retomarlo con un clic."
            }
            LText(msg, style = Lumen.type.body, color = c.textSecondary)
            Gap(height = 6.dp)
            PillButton("Ver TV en directo", onClick = onStart, primary = true, icon = Icons.Rounded.LiveTv)
        }
    }
}

@Composable
private fun ChannelCard(ch: ChannelEntity, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.width(200.dp), radius = LumenDimens.radiusMedium) {
        Column {
            Artwork(ch.logo, ch.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(18.dp), radius = 0.dp, background = Color.Transparent)
            LText(ch.name, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun HistoryCard(h: HistoryEntity, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.width(200.dp), radius = LumenDimens.radiusMedium) {
        Column {
            Artwork(h.image, h.title, Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(18.dp), radius = 0.dp, background = Color.Transparent)
            LText(h.title, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Footer(p: PlaylistEntity?, sync: SyncState, onRefresh: () -> Unit) {
    val c = Lumen.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val expiry = p?.expiresAt?.takeIf { it > 0 }?.let { " · Expira ${TimeFormatter(true).date(it)}" }.orEmpty()
        LText("${p?.name ?: "Sin lista"}$expiry".uppercase(), style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
        val status = when (sync) {
            is SyncState.Running -> "Actualizando ${sync.step.lowercase()}…"
            is SyncState.Failed -> "Error al actualizar"
            else -> "Última actualización ${relativeAge(p?.lastSyncAt ?: 0)}"
        }
        LText(status.uppercase(), style = Lumen.type.caption, color = c.textSecondary)
        PillButton("Actualizar", onClick = onRefresh, icon = Icons.Rounded.Refresh, enabled = sync !is SyncState.Running)
    }
}
