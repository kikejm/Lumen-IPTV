package app.lumentv.ui.screens.catchup

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lumentv.core.util.TimeFormatter
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.epg.Program
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.play
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens

/**
 * Catch-up (Stitch 14-catchup): canales con archivo y sus programas emitidos, agrupados por día.
 * Vertical: canales → programas del elegido (Atrás vuelve a los canales). Horizontal: dos paneles.
 * [initialChannelId] llega desde el menú de un canal de TV en directo.
 */
@Composable
fun CatchupScreen(nav: Navigator, insets: PaddingValues, initialChannelId: String?) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val wide = LocalWidthClass.current != WidthClass.Compact
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val pid = playlist?.id ?: -1L
    val channels by remember(pid) { graph.catalog.archiveChannels(pid) }.collectAsState(initial = emptyList())
    val settings by graph.settings.settings.collectAsState()
    val fmt = remember(settings.use24h, settings.epgOffsetHours) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }
    var selectedId by rememberSaveable { mutableStateOf(initialChannelId) }
    val selected = channels.firstOrNull { it.streamId == selectedId }
    var programs by remember { mutableStateOf<List<Program>?>(null) }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    val c = Lumen.colors

    LaunchedEffect(selectedId, playlist) {
        val p = playlist ?: return@LaunchedEffect
        val ch = channels.firstOrNull { it.streamId == selectedId } ?: graph.catalog.channel(p.id, selectedId ?: return@LaunchedEffect) ?: return@LaunchedEffect
        programs = null
        programs = runCatching { graph.epg.archive(p, ch) }.getOrDefault(emptyList())
    }
    // En vertical, Atrás desde los programas vuelve a la lista de canales (salvo si se llegó con uno elegido).
    BackHandler(enabled = !wide && selectedId != null && initialChannelId == null) { selectedId = null; day = null }

    val channelList: @Composable (Modifier) -> Unit = { modifier ->
        if (channels.isEmpty()) {
            EmptyState(Icons.Rounded.History, "Sin canales con catch-up", "Tu proveedor no ofrece archivo en ningún canal, o la lista aún se está cargando.", modifier)
        } else {
            LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(channels, key = { it.streamId }) { ch ->
                    val sel = ch.streamId == selectedId
                    TapSurface(
                        onClick = { selectedId = ch.streamId; day = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        radius = 12.dp,
                        color = if (sel) c.surfaceElevated else Color.Transparent,
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Artwork(ch.logo, ch.name, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
                            Column(Modifier.weight(1f)) {
                                LText(ch.name, style = Lumen.type.callout, maxLines = 1)
                                LText("Últimos ${ch.archiveDays} días", style = Lumen.type.caption, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(insets)) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            TopBar(nav, if (!wide && selected != null) selected.name else "Catch-up", showBack = true, showActions = false)
        }
        if (wide) {
            Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                channelList(Modifier.width(320.dp).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ProgramList(selected, programs, day, { day = it }, fmt) { ch, prog ->
                        playlist?.let { p -> graph.play(context, nav, listOf(graph.streams.catchup(p, ch, prog)), 0) }
                    }
                }
            }
        } else if (selectedId == null) {
            channelList(Modifier.weight(1f).padding(horizontal = 16.dp))
        } else {
            Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                ProgramList(selected, programs, day, { day = it }, fmt) { ch, prog ->
                    playlist?.let { p -> graph.play(context, nav, listOf(graph.streams.catchup(p, ch, prog)), 0) }
                }
            }
        }
    }
}

@Composable
private fun ProgramList(
    channel: ChannelEntity?,
    programs: List<Program>?,
    day: String?,
    onDay: (String) -> Unit,
    fmt: TimeFormatter,
    onPlay: (ChannelEntity, Program) -> Unit,
) {
    val c = Lumen.colors
    when {
        channel == null -> EmptyState(Icons.Rounded.History, "Elige un canal", "Verás los programas emitidos que puedes ver desde el principio.")
        programs == null -> LText("Cargando programación…", style = Lumen.type.body, color = c.textSecondary, modifier = Modifier.padding(16.dp))
        programs.isEmpty() -> EmptyState(Icons.Rounded.History, "Sin programas archivados", "El proveedor no devuelve archivo para este canal.")
        else -> {
            val days = remember(programs) { programs.map { fmt.day(it.startAt) }.distinct() }
            // Por defecto, el día más reciente (hoy).
            val shownDay = day?.takeIf { it in days } ?: days.last()
            val now = System.currentTimeMillis()
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                    items(days.reversed()) { d -> FilterChip(d.replaceFirstChar { it.uppercase() }, selected = d == shownDay, onClick = { onDay(d) }) }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(programs.filter { fmt.day(it.startAt) == shownDay }, key = { it.startAt }) { prog ->
                        val playable = prog.stopAt < now
                        TapSurface(
                            onClick = { if (playable) onPlay(channel, prog) },
                            enabled = playable,
                            modifier = Modifier.fillMaxWidth(),
                            radius = LumenDimens.radiusSmall,
                        ) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    LText(
                                        if (prog.isLive(now)) "AHORA · ${fmt.range(prog.startAt, prog.stopAt)}" else fmt.range(prog.startAt, prog.stopAt),
                                        style = Lumen.type.caption,
                                        color = if (prog.isLive(now)) c.accent else c.textSecondary,
                                    )
                                    LText(prog.title, style = Lumen.type.headline, maxLines = 1)
                                    if (prog.description.isNotBlank()) LText(prog.description, style = Lumen.type.callout, color = c.textSecondary, maxLines = 2)
                                }
                                if (playable) Icon(Icons.Rounded.PlayArrow, contentDescription = "Ver", tint = c.accent, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
