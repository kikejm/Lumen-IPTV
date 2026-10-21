package app.lumentv.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumentv.core.util.TimeFormatter
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.epg.Program
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.play
import app.lumentv.ui.playChannels
import app.lumentv.ui.screens.live.LiveViewModel
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens

private const val WINDOW_MIN = 150
/** Escala horizontal de la parrilla; se calcula para que la ventana ocupe todo el ancho. */
private val LocalPxPerMin = compositionLocalOf { 4.dp }
private val CHANNEL_COL: Dp = 190.dp

@Composable
fun GuideScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val vm: LiveViewModel = viewModel(key = "guide") { LiveViewModel(graph) }
    val playlist by vm.playlist.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val channels by vm.channels.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val fmt = remember(settings.use24h, settings.epgOffsetHours) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }
    val c = Lumen.colors

    // Ventana alineada a la media hora anterior.
    var windowStart by remember { mutableLongStateOf(alignHalfHour(System.currentTimeMillis())) }
    val windowEnd = windowStart + WINDOW_MIN * 60_000L
    var detail by remember { mutableStateOf<Pair<ChannelEntity, Program>?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val pxPerMin = (maxWidth - LumenDimens.safeHorizontal * 2 - CHANNEL_COL - 8.dp) / WINDOW_MIN.toFloat()
        CompositionLocalProvider(LocalPxPerMin provides pxPerMin) {
            Column(
                Modifier.fillMaxSize().background(c.background)
                    .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
            ) {
                TopBar(nav, Routes.GUIDE)
                Gap(height = 12.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                    items(categories.filterNot { it.locked && vm.isCategoryLocked(it.id) }, key = { it.id }) { cat ->
                        val sel = cat.id == selected
                        FocusSurface(
                            onClick = { vm.selectedCategory.value = cat.id },
                            radius = 50.dp,
                            containerColor = if (sel) c.accent else c.surface,
                            focusedContainerColor = if (sel) c.accent else c.surfaceFocused,
                            contentColor = if (sel) c.onAccent else c.textPrimary,
                            focusedContentColor = if (sel) c.onAccent else c.textPrimary,
                            focusedScale = 1.05f,
                        ) { LText(cat.name, style = Lumen.type.caption, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }
                    }
                }
                Gap(height = 10.dp)
                // Cabecera de tiempos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(CHANNEL_COL), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconCircleButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "1 hora antes", onClick = { windowStart -= 60 * 60_000L })
                        PillButton("Ahora", onClick = { windowStart = alignHalfHour(System.currentTimeMillis()) }, icon = Icons.Rounded.Schedule)
                        IconCircleButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "1 hora después", onClick = { windowStart += 60 * 60_000L })
                    }
                    Box(Modifier.weight(1f).height(24.dp)) {
                        for (i in 0 until WINDOW_MIN / 30) {
                            LText(fmt.time(windowStart + i * 30 * 60_000L), style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.offset(x = LocalPxPerMin.current * (i * 30) + 8.dp))
                        }
                    }
                }
                Gap(height = 6.dp)
                Box(Modifier.weight(1f)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(channels, key = { _, ch -> ch.streamId }) { index, ch ->
                            GuideRow(
                                playlist = playlist,
                                channel = ch,
                                from = windowStart,
                                to = windowEnd,
                                fmt = fmt,
                                onChannel = { playlist?.let { graph.playChannels(context, nav, it, channels, index) } },
                                onProgram = { prog -> detail = ch to prog },
                            )
                        }
                    }
                    // Línea de "ahora"
                    val now = System.currentTimeMillis()
                    if (now in windowStart until windowEnd) {
                        Box(
                            Modifier.fillMaxHeight().width(2.dp).offset(x = CHANNEL_COL + LocalPxPerMin.current * ((now - windowStart) / 60_000f))
                                .background(c.danger.copy(alpha = 0.8f)),
                        )
                    }
                }
            }
        }
    }

    detail?.let { (ch, prog) ->
        val p = playlist
        val now = System.currentTimeMillis()
        val canCatchup = ch.tvArchive && prog.stopAt < now
        app.lumentv.ui.components.OptionsDialog(
            "${prog.title} · ${fmt.range(prog.startAt, prog.stopAt)}",
            buildList {
                if (prog.isLive(now) || prog.startAt > now) add(app.lumentv.ui.components.MenuOption("Ver ${ch.name} en directo") {
                    p?.let { graph.playChannels(context, nav, it, channels, channels.indexOf(ch).coerceAtLeast(0)) }
                })
                if (canCatchup && p != null) add(app.lumentv.ui.components.MenuOption("Ver desde el principio (catch-up)") {
                    graph.play(context, nav, listOf(graph.streams.catchup(p, ch, prog)), 0)
                })
                if (prog.description.isNotBlank()) add(app.lumentv.ui.components.MenuOption(prog.description.take(160)) {})
            },
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun GuideRow(
    playlist: PlaylistEntity?,
    channel: ChannelEntity,
    from: Long,
    to: Long,
    fmt: TimeFormatter,
    onChannel: () -> Unit,
    onProgram: (Program) -> Unit,
) {
    val graph = LocalGraph.current
    val c = Lumen.colors
    val programs by produceState(emptyList<Program>(), channel.streamId, from, playlist?.id) {
        value = playlist?.let { runCatching { graph.epg.programs(it, channel, from, to) }.getOrDefault(emptyList()) }.orEmpty()
    }
    Row(Modifier.height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        FocusSurface(onClick = onChannel, modifier = Modifier.width(CHANNEL_COL - 8.dp).fillMaxHeight(), radius = 10.dp, focusedScale = 1.03f) {
            Row(Modifier.padding(horizontal = 8.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Artwork(channel.logo, channel.name, Modifier.size(width = 44.dp, height = 30.dp), radius = 5.dp)
                LText(channel.name, style = Lumen.type.caption, maxLines = 2)
            }
        }
        Gap(width = 8.dp)
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            if (programs.isEmpty()) {
                Box(Modifier.width(LocalPxPerMin.current * WINDOW_MIN).fillMaxHeight().background(c.surface.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)), contentAlignment = Alignment.CenterStart) {
                    LText("Sin información", style = Lumen.type.caption, color = c.textTertiary, modifier = Modifier.padding(start = 12.dp))
                }
            }
            val now = System.currentTimeMillis()
            programs.forEach { prog ->
                val start = maxOf(prog.startAt, from)
                val end = minOf(prog.stopAt, to)
                if (end <= start) return@forEach
                val x = LocalPxPerMin.current * ((start - from) / 60_000f)
                val w = LocalPxPerMin.current * ((end - start) / 60_000f) - 3.dp
                val live = prog.isLive(now)
                FocusSurface(
                    onClick = { onProgram(prog) },
                    modifier = Modifier.offset(x = x).width(w.coerceAtLeast(6.dp)).fillMaxHeight(),
                    radius = 10.dp,
                    containerColor = if (live) c.accent.copy(alpha = 0.22f) else c.surface,
                    focusedScale = 1.02f,
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        LText(prog.title, style = Lumen.type.caption, color = c.textPrimary, maxLines = 1)
                        LText(fmt.range(prog.startAt, prog.stopAt), style = Lumen.type.caption, color = if (live) c.accent else c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun alignHalfHour(t: Long): Long {
    val half = 30 * 60_000L
    return t - (t % half)
}
