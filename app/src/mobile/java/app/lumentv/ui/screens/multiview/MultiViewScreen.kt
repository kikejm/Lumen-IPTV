package app.lumentv.ui.screens.multiview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.lumentv.data.db.ChannelEntity
import app.lumentv.player.PlayerFactory
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.AdaptiveSheet
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.LoadingSpinner
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.ReconnectPill
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.StreamReconnect
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.components.rememberStreamReconnect
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.player.LocalMiniPlayer
import app.lumentv.ui.screens.live.LiveViewModel
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.launch

private val SLOT_RADIUS = 14.dp

/**
 * Multipantalla (Stitch L11-multipantalla): dos canales a la vez en el móvil y cuatro en tableta.
 * Tocar un vídeo le da el audio; pulsación larga o «⋯» = cambiar canal, ampliar o quitar. Cada
 * pantalla reconecta por su cuenta (`StreamReconnect`, igual que en la TV). Los reproductores no
 * son exclusivos (`exclusive = false`): la cuenta IPTV tiene que admitir varias conexiones.
 */
@Composable
fun MultiViewScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val widthClass = LocalWidthClass.current
    val mini = LocalMiniPlayer.current
    // El número de pantallas se fija al entrar: girar la tableta no corta ninguna.
    val slots = remember { if (widthClass == WidthClass.Expanded) 4 else 2 }
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val channels = remember { mutableStateListOf<ChannelEntity?>().apply { repeat(slots) { add(null) } } }
    val players = remember { List(slots) { graph.players.create(lightweight = true, exclusive = false) } }
    var audioSlot by remember { mutableIntStateOf(0) }
    var expandedSlot by remember { mutableStateOf<Int?>(null) }
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    var menuFor by remember { mutableStateOf<Int?>(null) }

    // Lo que sonara en el mini reproductor ocuparía otra conexión de la cuenta: se cierra.
    LaunchedEffect(Unit) { mini.close() }
    DisposableEffect(Unit) { onDispose { players.forEach(ExoPlayer::release) } }
    val reconnects = List(slots) { i ->
        rememberStreamReconnect(players[i]) {
            players[i].seekToDefaultPosition()
            players[i].prepare()
        }
    }
    LaunchedEffect(audioSlot) {
        players.forEachIndexed { i, p -> p.volume = if (i == audioSlot) 1f else 0f }
        // Foco de audio solo para el que suena: primero lo sueltan los demás.
        players.forEachIndexed { i, p -> if (i != audioSlot) graph.players.setAudioFocus(p, false) }
        graph.players.setAudioFocus(players[audioSlot], true)
    }
    BackHandler(enabled = expandedSlot != null) { expandedSlot = null }

    fun load(slot: Int, ch: ChannelEntity) {
        val p = playlist ?: return
        channels[slot] = ch
        reconnects[slot].reset()
        players[slot].apply {
            setMediaItem(PlayerFactory.mediaItem(graph.streams.live(p, ch).url))
            prepare()
            playWhenReady = true
        }
        audioSlot = slot
        // Historial de canales compartido con TV en directo.
        graph.appScope.launch { graph.library.recordChannel(p.id, ch) }
    }
    fun clear(slot: Int) {
        channels[slot] = null
        reconnects[slot].reset()
        players[slot].stop()
        players[slot].clearMediaItems()
        if (expandedSlot == slot) expandedSlot = null
    }
    fun tap(slot: Int) {
        when {
            channels[slot] == null -> pickerFor = slot
            reconnects[slot].failed -> reconnects[slot].retryNow()
            else -> audioSlot = slot
        }
    }

    val slot: @Composable (Int, Modifier) -> Unit = { i, modifier ->
        Slot(
            player = players[i],
            channel = channels[i],
            reconnect = reconnects[i],
            hasAudio = i == audioSlot,
            expanded = i == expandedSlot,
            showBadge = slots > 1 && expandedSlot == null,
            onTap = { tap(i) },
            onMenu = { if (channels[i] == null) pickerFor = i else menuFor = i },
            modifier = modifier,
        )
    }

    Column(Modifier.fillMaxSize().padding(insets)) {
        Box(Modifier.padding(horizontal = 16.dp)) { TopBar(nav, "Multipantalla", showBack = true, showActions = false) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val landscape = maxWidth > maxHeight
            val expanded = expandedSlot
            when {
                expanded != null -> slot(expanded, Modifier.fillMaxSize())
                slots == 4 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 2) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (col in 0 until 2) slot(row * 2 + col, Modifier.weight(1f).fillMaxSize())
                        }
                    }
                }
                landscape -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until slots) slot(i, Modifier.weight(1f).aspectRatio(16f / 9f))
                }
                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                    for (i in 0 until slots) slot(i, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
            }
        }
        LText(
            if (expandedSlot != null) "Toca ⋯ o vuelve atrás para ver todas las pantallas"
            else "Toca un vídeo para oír su audio · Mantén pulsado para cambiar de canal",
            style = Lumen.type.callout,
            color = Lumen.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }

    menuFor?.let { i ->
        val ch = channels[i]
        OptionsSheet(
            ch?.name ?: "Pantalla ${i + 1}",
            listOfNotNull(
                SheetOption("Cambiar canal", Icons.Rounded.SwapHoriz) { pickerFor = i },
                if (i != audioSlot) SheetOption("Oír esta pantalla", Icons.AutoMirrored.Rounded.VolumeUp) { audioSlot = i } else null,
                if (expandedSlot == i) SheetOption("Ver todas las pantallas", Icons.Rounded.FullscreenExit) { expandedSlot = null }
                else SheetOption("Ampliar", Icons.Rounded.Fullscreen) { expandedSlot = i; audioSlot = i },
                if (reconnects[i].failed) SheetOption("Reintentar", Icons.Rounded.Refresh) { reconnects[i].retryNow() } else null,
                SheetOption("Quitar canal", Icons.Rounded.Close, destructive = true) { clear(i) },
            ),
            onDismiss = { menuFor = null },
        )
    }
    pickerFor?.let { i ->
        ChannelPicker(onDismiss = { pickerFor = null }) { ch ->
            load(i, ch)
            pickerFor = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Slot(
    player: ExoPlayer,
    channel: ChannelEntity?,
    reconnect: StreamReconnect,
    hasAudio: Boolean,
    expanded: Boolean,
    showBadge: Boolean,
    onTap: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier,
) {
    val c = Lumen.colors
    var state by remember(player) { mutableIntStateOf(player.playbackState) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { state = playbackState }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val shape = RoundedCornerShape(if (expanded) 0.dp else SLOT_RADIUS)
    // El vídeo va debajo y la capa táctil transparente encima: nunca el SurfaceView dentro de una superficie con capa propia.
    Box(
        modifier
            .clip(shape)
            .background(if (channel == null) c.surface else Color.Black)
            .then(if (hasAudio && showBadge && channel != null) Modifier.border(2.dp, Color.White, shape) else Modifier),
    ) {
        if (channel != null) VideoSurface(player, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = null,
                    indication = ripple(color = Color.White),
                    onLongClick = onMenu,
                    onClick = onTap,
                ),
        ) {
            if (channel == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(c.surfaceElevated), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(28.dp))
                    }
                    LText("Elegir canal", style = Lumen.type.callout, color = c.textSecondary)
                }
                return@Box
            }
            when {
                reconnect.failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    LText("No disponible", style = Lumen.type.headline, color = Color.White)
                    LText("Toca para reintentar", style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f))
                }
                reconnect.attempt > 0 -> ReconnectPill(reconnect.attempt, Modifier.align(Alignment.Center))
                state != Player.STATE_READY -> LoadingSpinner(Modifier.align(Alignment.Center))
            }
            if (showBadge) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (hasAudio) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = null,
                        tint = if (hasAudio) c.accent else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                    LText(if (hasAudio) "AUDIO" else "SILENCIO", style = Lumen.type.caption, color = Color.White, maxLines = 1)
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    LText(
                        channel.name,
                        style = Lumen.type.callout,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                IconCircleButton(Icons.Rounded.MoreVert, "Opciones de ${channel.name}", onClick = onMenu, color = Color.Black.copy(alpha = 0.4f))
            }
        }
    }
}

/** Elegir canal para una pantalla: chips de categoría y lista (las categorías bloqueadas no salen). */
@Composable
private fun ChannelPicker(onDismiss: () -> Unit, onPick: (ChannelEntity) -> Unit) {
    val graph = LocalGraph.current
    val vm: LiveViewModel = viewModel(key = "multiview-picker") { LiveViewModel(graph) }
    val categories by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val channels by vm.channels.collectAsState()
    val c = Lumen.colors
    AdaptiveSheet(onDismiss) {
        LText("Elegir canal", style = Lumen.type.headline)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(categories.filterNot { it.locked && vm.isCategoryLocked(it.id) }, key = { it.id }) { cat ->
                FilterChip(cat.name, cat.id == selected, onClick = { vm.selectedCategory.value = cat.id })
            }
        }
        if (channels.isEmpty()) {
            EmptyState(Icons.Rounded.LiveTv, "Sin canales", "Elige otra categoría.")
        } else {
            // Alto fijo: la hoja lateral de horizontal ya se desplaza y no admite una lista de alto infinito.
            LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(channels, key = { it.streamId }) { ch ->
                    TapSurface(onClick = { onPick(ch) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), radius = LumenDimens.radiusSmall, color = Color.Transparent) {
                        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Artwork(ch.logo, ch.name, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
                            LText(ch.name, style = Lumen.type.body, color = c.textPrimary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
