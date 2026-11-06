package app.lumentv.ui.screens.guide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumentv.core.util.TimeFormatter
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.epg.Program
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EPG_ROW_DELAY_MS
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.play
import app.lumentv.ui.playChannels
import app.lumentv.ui.screens.live.LiveViewModel
import app.lumentv.ui.theme.Lumen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ventana de la parrilla: desde una hora antes de ahora hasta siete después (8 h desplazables). */
private const val BEFORE_MIN = 60
private const val WINDOW_MIN = 8 * 60
private val ROW_HEIGHT = 64.dp

/**
 * Guía (Stitch 06-guia / L06-guia): chips de categoría, «Ahora» y parrilla con la columna de canales
 * fija. Toda la parrilla se desplaza en horizontal a la vez (un `ScrollState` compartido por todas
 * las filas) y en vertical con la lista. La escala depende del ancho: ~1 h visible en vertical,
 * ~2,5 h en horizontal y ~3 h en tableta. Lo que se emite ahora va en superficie aclarada y la
 * hora actual es la única línea roja.
 */
@Composable
fun GuideScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val widthClass = LocalWidthClass.current
    val vm: LiveViewModel = viewModel(key = "guide") { LiveViewModel(graph) }
    val playlist by vm.playlist.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val channels by vm.channels.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val fmt = remember(settings.use24h, settings.epgOffsetHours) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }
    val c = Lumen.colors

    // La hora avanza con la pantalla abierta: la línea de «ahora» y el programa en emisión se mueven
    // cada 30 s, y la ventana se adelanta cuando «ahora» se acerca a su final.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    var windowStart by remember { mutableLongStateOf(alignHalfHour(System.currentTimeMillis()) - BEFORE_MIN * 60_000L) }
    val windowEnd = windowStart + WINDOW_MIN * 60_000L
    LaunchedEffect(now >= windowEnd - BEFORE_MIN * 60_000L) {
        if (now >= windowEnd - BEFORE_MIN * 60_000L) windowStart = alignHalfHour(now) - BEFORE_MIN * 60_000L
    }
    // Las filas crecen con el tamaño de letra del sistema en vez de cortar el texto.
    val rowHeight = ROW_HEIGHT * LocalDensity.current.fontScale.coerceAtLeast(1f)
    var detail by remember { mutableStateOf<Pair<ChannelEntity, Program>?>(null) }
    val visibleCategories = categories.filterNot { it.locked && vm.isCategoryLocked(it.id) }
    val hScroll = rememberScrollState()
    val density = LocalDensity.current

    val channelCol = if (widthClass == WidthClass.Compact) 92.dp else 160.dp
    val visibleMinutes = when (widthClass) {
        WidthClass.Compact -> 60
        WidthClass.Medium -> 150
        WidthClass.Expanded -> 180
    }

    Column(Modifier.fillMaxSize().padding(insets)) {
        Box(Modifier.padding(horizontal = 16.dp)) { TopBar(nav, "Guía") }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleCategories, key = { it.id }) { cat ->
                FilterChip(cat.name, selected = cat.id == selected, onClick = { vm.selectedCategory.value = cat.id })
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            val gridWidth = maxWidth - channelCol
            val pxPerMin: Dp = gridWidth / visibleMinutes.toFloat()
            val fullWidth = pxPerMin * WINDOW_MIN
            fun scrollToNow() {
                val target = pxPerMin * ((System.currentTimeMillis() - windowStart) / 60_000f - 15f)
                scope.launch { hScroll.animateScrollTo(with(density) { target.roundToPx() }.coerceAtLeast(0)) }
            }
            // Al entrar, la parrilla empieza un cuarto de hora antes de ahora.
            LaunchedEffect(pxPerMin, windowStart) {
                val target = pxPerMin * ((System.currentTimeMillis() - windowStart) / 60_000f - 15f)
                hScroll.scrollTo(with(density) { target.roundToPx() }.coerceAtLeast(0))
            }

            Column(Modifier.fillMaxSize()) {
                // Cabecera: «Ahora» sobre la columna de canales y la escala de tiempos.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                    Box(Modifier.width(channelCol), contentAlignment = Alignment.Center) {
                        PillButton("Ahora", onClick = ::scrollToNow, icon = if (widthClass == WidthClass.Compact) null else Icons.Rounded.Schedule)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                        Box(Modifier.width(fullWidth).fillMaxHeight()) {
                            for (i in 0 until WINDOW_MIN / 30) {
                                LText(
                                    fmt.time(windowStart + i * 30 * 60_000L),
                                    style = Lumen.type.caption,
                                    color = c.textSecondary,
                                    modifier = Modifier.align(Alignment.CenterStart).offset(x = pxPerMin * (i * 30) + 6.dp),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(channels, key = { _, ch -> ch.streamId }) { index, ch ->
                            GuideRow(
                                playlist = playlist,
                                channel = ch,
                                from = windowStart,
                                to = windowEnd,
                                fmt = fmt,
                                now = now,
                                rowHeight = rowHeight,
                                channelCol = channelCol,
                                pxPerMin = pxPerMin,
                                fullWidth = fullWidth,
                                hScroll = hScroll,
                                onChannel = { playlist?.let { graph.playChannels(context, nav, it, channels, index) } },
                                onProgram = { prog -> detail = ch to prog },
                            )
                        }
                    }
                    // Línea de «ahora»: se coloca en la fase de layout (leer el scroll aquí recompondría la
                    // parrilla en cada fotograma) y se recorta a la zona de programas.
                    val nowPx = with(density) { (pxPerMin * ((now - windowStart) / 60_000f)).roundToPx() }
                    Box(Modifier.padding(start = channelCol).fillMaxSize().clipToBounds()) {
                        Box(
                            Modifier.fillMaxHeight().width(2.dp)
                                .offset { IntOffset(nowPx - hScroll.value, 0) }
                                .background(c.danger.copy(alpha = 0.85f)),
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
        OptionsSheet(
            title = prog.title,
            subtitle = "${ch.name} · ${fmt.range(prog.startAt, prog.stopAt)}" + if (prog.description.isNotBlank()) "\n${prog.description.take(240)}" else "",
            options = buildList {
                if (prog.isLive(now) || prog.startAt > now) add(SheetOption("Ver ${ch.name} en directo", Icons.Rounded.LiveTv) {
                    p?.let { graph.playChannels(context, nav, it, channels, channels.indexOf(ch).coerceAtLeast(0)) }
                })
                if (canCatchup && p != null) add(SheetOption("Ver desde el principio (catch-up)", Icons.Rounded.History) {
                    graph.play(context, nav, listOf(graph.streams.catchup(p, ch, prog)), 0)
                })
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
    now: Long,
    rowHeight: Dp,
    channelCol: Dp,
    pxPerMin: Dp,
    fullWidth: Dp,
    hScroll: ScrollState,
    onChannel: () -> Unit,
    onProgram: (Program) -> Unit,
) {
    val graph = LocalGraph.current
    val c = Lumen.colors
    val programs by produceState(emptyList<Program>(), channel.streamId, from, playlist?.id) {
        // Al desplazar deprisa las filas pasan de largo: solo piden la EPG las que se quedan a la vista.
        delay(EPG_ROW_DELAY_MS)
        value = playlist?.let { runCatching { graph.epg.programs(it, channel, from, to) }.getOrDefault(emptyList()) }.orEmpty()
    }
    Row(Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
        // Columna fija: no se desplaza en horizontal.
        TapSurface(onClick = onChannel, modifier = Modifier.width(channelCol).fillMaxHeight().padding(start = 8.dp, end = 4.dp), radius = 10.dp) {
            Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(channel.logo, channel.name, Modifier.size(width = 44.dp, height = 28.dp), radius = 5.dp, background = Color.Transparent)
                LText(channel.name, style = Lumen.type.caption, maxLines = 1)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            Box(Modifier.width(fullWidth).fillMaxHeight()) {
                if (programs.isEmpty()) {
                    Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.5f)), contentAlignment = Alignment.CenterStart) {
                        LText("Sin información", style = Lumen.type.caption, color = c.textTertiary, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                programs.forEach { prog ->
                    val start = maxOf(prog.startAt, from)
                    val end = minOf(prog.stopAt, to)
                    if (end <= start) return@forEach
                    val x = pxPerMin * ((start - from) / 60_000f)
                    val w = pxPerMin * ((end - start) / 60_000f) - 3.dp
                    val live = prog.isLive(now)
                    TapSurface(
                        onClick = { onProgram(prog) },
                        modifier = Modifier.offset(x = x).width(w.coerceAtLeast(6.dp)).fillMaxHeight().heightIn(min = 48.dp),
                        radius = 10.dp,
                        color = if (live) c.surfaceElevated else c.surface,
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            if (live) LText("Ahora", style = Lumen.type.caption, color = c.accent, maxLines = 1)
                            LText(prog.title, style = Lumen.type.callout, color = c.textPrimary, maxLines = 1)
                            LText(fmt.range(prog.startAt, prog.stopAt), style = Lumen.type.caption, color = c.textSecondary, maxLines = 1)
                        }
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
