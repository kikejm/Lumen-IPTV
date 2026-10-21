package app.lumentv.ui.screens.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Icon
import app.lumentv.data.db.ChannelEntity
import app.lumentv.player.PlayerFactory
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.CategoryColumn
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.GlassPanel
import app.lumentv.ui.components.KeyHints
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.onMenuKey
import app.lumentv.ui.screens.live.LiveViewModel
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.delay

/** Disposición: filas con su peso vertical y nº de huecos (como el selector de Smarters). */
private data class MultiLayout(val rows: List<Pair<Float, Int>>) {
    val slots get() = rows.sumOf { it.second }
}

private val LAYOUTS = listOf(
    MultiLayout(listOf(1f to 2, 1f to 2)),
    MultiLayout(listOf(1.6f to 1, 1f to 3)),
    MultiLayout(listOf(1.3f to 1, 1f to 2)),
    MultiLayout(listOf(1f to 2, 1.3f to 1)),
    MultiLayout(listOf(1f to 2)),
    MultiLayout(listOf(1f to 1, 1f to 1)),
)

@Composable
fun MultiViewScreen(nav: Navigator) {
    var layout by remember { mutableStateOf<MultiLayout?>(null) }
    val l = layout
    if (l == null) LayoutPicker(nav) { layout = it } else MultiViewGrid(l)
}

@Composable
private fun LayoutPicker(nav: Navigator, onPick: (MultiLayout) -> Unit) {
    val c = Lumen.colors
    Column(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
    ) {
        TopBar(nav, Routes.MULTIVIEW, title = "Multipantalla")
        Gap(height = 24.dp)
        LText("Elige una disposición", style = Lumen.type.largeTitle)
        LText("En Fire TV Stick se recomiendan 2–3 señales; 4 a la vez pueden superar su capacidad de decodificación.", style = Lumen.type.body, color = c.textSecondary)
        Gap(height = 24.dp)
        LAYOUTS.chunked(3).forEach { rowLayouts ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                rowLayouts.forEach { lay ->
                    FocusSurface(onClick = { onPick(lay) }, modifier = Modifier.width(250.dp).aspectRatio(16f / 9f), radius = 16.dp) {
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lay.rows.forEach { (weight, count) ->
                                Row(Modifier.weight(weight).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    repeat(count) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c.textTertiary)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiViewGrid(layout: MultiLayout) {
    val graph = LocalGraph.current
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val channels = remember { mutableStateListOf<ChannelEntity?>().apply { repeat(layout.slots) { add(null) } } }
    val players = remember { List(layout.slots) { graph.players.create(lightweight = true) } }
    var focusedSlot by remember { mutableIntStateOf(0) }
    var pickerFor by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) { onDispose { players.forEach(ExoPlayer::release) } }
    LaunchedEffect(focusedSlot) { players.forEachIndexed { i, p -> p.volume = if (i == focusedSlot) 1f else 0f } }

    Column(Modifier.fillMaxSize().background(Color.Black).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var slot = 0
        layout.rows.forEach { (weight, count) ->
            Row(Modifier.weight(weight).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) {
                    val index = slot++
                    val ch = channels[index]
                    // El vídeo va en una caja normal y la superficie de foco transparente encima:
                    // si el SurfaceView del vídeo está dentro de la capa escalada/recortada de la
                    // superficie de foco, no se ve.
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111))) {
                        if (ch != null) VideoSurface(players[index], Modifier.fillMaxSize())
                        FocusSurface(
                            onClick = { pickerFor = index },
                            modifier = Modifier.fillMaxSize().onFocusChanged { if (it.isFocused) focusedSlot = index }.onMenuKey { pickerFor = index },
                            radius = 10.dp,
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                            focusedScale = 1f,
                        ) {
                            if (ch == null) {
                                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                    LText("Elegir canal", style = Lumen.type.callout, color = Color.White)
                                }
                            } else {
                                Row(
                                    Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (index == focusedSlot) Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(14.dp))
                                    LText(ch.name, style = Lumen.type.caption, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        KeyHints("OK" to "Cambiar canal", "◀▲▼▶" to "Mover audio", "Atrás" to "Salir", modifier = Modifier.padding(start = 8.dp))
    }

    pickerFor?.let { slotIndex ->
        ChannelPicker(onDismiss = { pickerFor = null }) { ch ->
            val p = playlist ?: return@ChannelPicker
            channels[slotIndex] = ch
            players[slotIndex].apply {
                setMediaItem(PlayerFactory.mediaItem(graph.streams.live(p, ch).url))
                prepare()
                playWhenReady = true
                volume = if (slotIndex == focusedSlot) 1f else 0f
            }
            pickerFor = null
        }
    }
}

@Composable
private fun ChannelPicker(onDismiss: () -> Unit, onPick: (ChannelEntity) -> Unit) {
    val graph = LocalGraph.current
    val vm: LiveViewModel = viewModel(key = "multiview-picker") { LiveViewModel(graph) }
    val categories by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val channels by vm.channels.collectAsState()
    val c = Lumen.colors
    val firstChannel = remember { FocusRequester() }
    LaunchedEffect(channels.isNotEmpty()) {
        if (channels.isNotEmpty()) { delay(100); runCatching { firstChannel.requestFocus() } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.width(760.dp).fillMaxHeight(0.85f)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryColumn(
                    items = categories.filterNot { it.locked && vm.isCategoryLocked(it.id) },
                    selectedId = selected,
                    onSelect = { vm.selectedCategory.value = it.id },
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                )
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(6.dp)) {
                    items(channels, key = { it.streamId }) { ch ->
                        FocusSurface(
                            onClick = { onPick(ch) },
                            modifier = Modifier.fillMaxWidth().then(if (ch.streamId == channels.first().streamId) Modifier.focusRequester(firstChannel) else Modifier), containerColor = Color.Transparent, focusedScale = 1.02f, radius = 10.dp) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Artwork(ch.logo, ch.name, Modifier.size(width = 48.dp, height = 32.dp), radius = 6.dp)
                                LText(ch.name, style = Lumen.type.callout, maxLines = 1, color = c.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
