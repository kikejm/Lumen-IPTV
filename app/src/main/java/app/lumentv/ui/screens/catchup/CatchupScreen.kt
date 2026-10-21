package app.lumentv.ui.screens.catchup

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lumentv.core.util.TimeFormatter
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.epg.Program
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.play
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens

/** Catch-up: canales con archivo y su programación pasada reproducible. */
@Composable
fun CatchupScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val pid = playlist?.id ?: -1L
    val channels by remember(pid) { graph.catalog.archiveChannels(pid) }.collectAsState(initial = emptyList())
    val settings by graph.settings.settings.collectAsState()
    val fmt = remember(settings.use24h) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }
    var selected by remember { mutableStateOf<ChannelEntity?>(null) }
    var programs by remember { mutableStateOf<List<Program>?>(null) }
    val c = Lumen.colors

    LaunchedEffect(selected, playlist) {
        val p = playlist ?: return@LaunchedEffect
        val ch = selected ?: return@LaunchedEffect
        programs = null
        programs = graph.epg.archive(p, ch)
    }

    Column(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
    ) {
        TopBar(nav, Routes.CATCHUP, title = "Catch-up")
        Gap(height = 18.dp)
        if (channels.isEmpty()) {
            EmptyState(Icons.Rounded.History, "Sin canales con catch-up", "Tu proveedor no ofrece archivo en ningún canal, o la lista aún se está cargando.")
            return@Column
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LazyColumn(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(6.dp)) {
                items(channels, key = { it.streamId }) { ch ->
                    val sel = ch.streamId == selected?.streamId
                    FocusSurface(
                        onClick = { selected = ch },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (sel) c.surfaceElevated else Color.Transparent,
                        focusedScale = 1.02f,
                        radius = 12.dp,
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Artwork(ch.logo, ch.name, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
                            Column(Modifier.weight(1f)) {
                                LText(ch.name, style = Lumen.type.callout, maxLines = 1)
                                LText("${ch.archiveDays} días de archivo", style = Lumen.type.caption, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val list = programs
                when {
                    selected == null -> EmptyState(Icons.Rounded.History, "Elige un canal", "Verás los programas emitidos que puedes ver desde el principio.")
                    list == null -> LText("Cargando programación…", style = Lumen.type.body, color = c.textSecondary, modifier = Modifier.align(Alignment.Center))
                    list.isEmpty() -> EmptyState(Icons.Rounded.History, "Sin programas archivados", "El proveedor no devuelve archivo para este canal.")
                    else -> {
                        val ch = selected!!
                        var lastDay = ""
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(6.dp)) {
                            list.forEach { prog ->
                                val day = fmt.day(prog.startAt)
                                if (day != lastDay) {
                                    lastDay = day
                                    item(key = "day-$day") { LText(day.uppercase(), style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp, start = 6.dp)) }
                                }
                                item(key = "p-${prog.startAt}") {
                                    FocusSurface(
                                        onClick = { playlist?.let { p -> graph.play(context, nav, listOf(graph.streams.catchup(p, ch, prog)), 0) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        focusedScale = 1.02f,
                                    ) {
                                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            LText(fmt.range(prog.startAt, prog.stopAt), style = Lumen.type.callout, color = c.accent, modifier = Modifier.width(120.dp))
                                            Column(Modifier.weight(1f)) {
                                                LText(prog.title, style = Lumen.type.callout, maxLines = 1)
                                                if (prog.description.isNotBlank()) LText(prog.description, style = Lumen.type.caption, color = c.textSecondary, maxLines = 2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
