package app.lumentv.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.tv.material3.Icon
import app.lumentv.MainActivity
import app.lumentv.core.util.TimeFormatter
import app.lumentv.core.util.formatDuration
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.epg.Program
import app.lumentv.data.settings.ResizeMode
import app.lumentv.player.PlayItem
import app.lumentv.player.PlayerFactory
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.MenuOption
import app.lumentv.ui.components.OptionsDialog
import app.lumentv.ui.components.PlayerLoading
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()
    val session = graph.playback
    val settings by graph.settings.settings.collectAsState()
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val fmt = remember(settings.use24h, settings.epgOffsetHours) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }
    val c = Lumen.colors

    var item by remember { mutableStateOf(session.current) }
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var programs by remember { mutableStateOf<List<Program>>(emptyList()) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var showMenu by remember { mutableStateOf(false) }
    var resize by remember { mutableStateOf(settings.resizeMode) }
    var retries by remember { mutableIntStateOf(0) }
    val inPip = activity?.isInPip ?: false
    val focus = remember { FocusRequester() }

    val player = remember { graph.players.create() }

    // Se guarda en el scope de la app: al salir del reproductor el scope de la pantalla ya está cancelado.
    fun saveHistory(it: PlayItem) {
        val known = playlist
        val pos = player.currentPosition.coerceAtLeast(0)
        val dur = player.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: 0
        graph.appScope.launch {
            val p = known ?: graph.playlists.activeNow() ?: return@launch
            graph.library.record(
                HistoryEntity(
                    playlistId = p.id,
                    kind = it.kind,
                    itemId = it.itemId,
                    title = it.title,
                    subtitle = it.subtitle,
                    image = it.image,
                    positionMs = if (it.kind == ContentKind.LIVE) 0 else pos,
                    durationMs = if (it.kind == ContentKind.LIVE) 0 else dur,
                    extra = it.extra,
                ),
            )
        }
    }

    fun load(target: PlayItem?) {
        target ?: return
        item = target
        error = null
        graph.players.claimStream(player)
        player.setMediaItem(PlayerFactory.mediaItem(target.url), target.startPositionMs)
        player.prepare()
        player.playWhenReady = true
        overlay = true
        overlayTick++
        saveHistory(target)
    }

    fun zap(delta: Int) {
        item?.let { saveHistory(it) }
        retries = 0
        load(session.move(delta))
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) retries = 0
                if (state == Player.STATE_ENDED) {
                    val current = item
                    if (current?.kind == ContentKind.EPISODE && session.hasNext()) zap(1)
                    else if (current?.kind == ContentKind.LIVE) load(current) // stream cortado: reconecta
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                val current = item
                // En directo los cortes son habituales, y un 403/426 suele ser la conexión anterior que el
                // proveedor aún no ha soltado: reintenta un par de veces antes de mostrar el error.
                if (current != null && retries < 2 && (current.kind == ContentKind.LIVE || e.isConnectionRefused())) {
                    retries++
                    scope.launch { delay(2_000); load(current) }
                } else {
                    error = humanError(e)
                }
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(listener)
        activity?.playerActive = true
        load(item)
        onDispose {
            item?.let { saveHistory(it) }
            activity?.playerActive = false
            player.removeListener(listener)
            player.release()
        }
    }

    // Posición/duración para la barra de progreso.
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
            if (++tick % 30 == 0) item?.takeIf { it.kind != ContentKind.LIVE }?.let { saveHistory(it) }
            delay(500)
        }
    }
    // EPG del canal actual.
    LaunchedEffect(item?.itemId, playlist?.id) {
        val p = playlist
        val ch = item?.channel
        programs = if (p != null && ch != null && item?.subtitle?.startsWith("Catch-up") != true) graph.epg.nowAndNext(p, ch, 3) else emptyList()
    }
    // Oculta el overlay a los 5 s de inactividad.
    LaunchedEffect(overlayTick, overlay, isPlaying) {
        if (overlay && isPlaying) { delay(5_000); overlay = false }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler(enabled = overlay && error == null) { overlay = false }

    fun showOverlay() { overlay = true; overlayTick++ }
    val isLive = item?.kind == ContentKind.LIVE && item?.subtitle?.startsWith("Catch-up") != true

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { if (isLive && session.queue.size > 1) zap(-1) else showOverlay(); true }
                    Key.DirectionDown, Key.ChannelDown -> { if (isLive && session.queue.size > 1) zap(1) else showOverlay(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { if (!isLive) player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); showOverlay(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { if (!isLive) player.seekTo(player.currentPosition + 30_000); showOverlay(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (error != null) { load(item); true }
                        else if (!overlay) { showOverlay(); true }
                        else { if (player.isPlaying) player.pause() else player.play(); showOverlay(); true }
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { if (player.isPlaying) player.pause() else player.play(); showOverlay(); true }
                    Key.Menu -> { showMenu = true; true }
                    else -> false
                }
            },
    ) {
        VideoSurface(player, Modifier.fillMaxSize(), resize)

        if (buffering && error == null) PlayerLoading(Modifier.align(Alignment.Center))

        error?.let { msg ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(48.dp))
                LText("No se puede reproducir", style = Lumen.type.title, color = Color.White)
                LText(msg, style = Lumen.type.body, color = Color.White.copy(alpha = 0.7f))
                LText("OK para reintentar · Atrás para salir", style = Lumen.type.caption, color = Color.White.copy(alpha = 0.6f))
            }
        }

        AnimatedVisibility(visible = overlay && !inPip, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Degradados superior e inferior para legibilidad.
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))

                Row(
                    Modifier.align(Alignment.TopStart).padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item?.let { Artwork(it.image, it.title, Modifier.size(width = 72.dp, height = 46.dp), radius = 8.dp, background = Color.White.copy(alpha = 0.1f)) }
                    Column {
                        item?.channel?.let { if (isLive) LText("${it.num}", style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f)) }
                        LText(item?.title.orEmpty(), style = Lumen.type.title, color = Color.White, maxLines = 1)
                        item?.subtitle?.takeIf { it.isNotBlank() }?.let { LText(it, style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isLive) {
                        programs.firstOrNull()?.let { now ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                LText(fmt.range(now.startAt, now.stopAt), style = Lumen.type.callout, color = c.accent)
                                LText(now.title, style = Lumen.type.headline, color = Color.White, maxLines = 1)
                            }
                            ProgressLine(now.progress(), Modifier.width(520.dp))
                            if (now.description.isNotBlank()) LText(now.description, style = Lumen.type.body, color = Color.White.copy(alpha = 0.7f), maxLines = 2, modifier = Modifier.fillMaxWidth(0.6f))
                        }
                        programs.getOrNull(1)?.let { next ->
                            LText("Después: ${fmt.time(next.startAt)} · ${next.title}", style = Lumen.type.callout, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                        }
                        Gap(height = 4.dp)
                        LText("☰ opciones", style = Lumen.type.caption, color = Color.White.copy(alpha = 0.5f))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            LText(formatDuration(position), style = Lumen.type.callout, color = Color.White)
                            ProgressLine(if (duration > 0) position.toFloat() / duration else 0f, Modifier.weight(1f), color = Color.White, height = 4.dp)
                            LText("-" + formatDuration((duration - position).coerceAtLeast(0)), style = Lumen.type.callout, color = Color.White)
                        }
                        LText("◀ -10 s  ·  ▶ +30 s  ·  OK pausa  ·  ☰ opciones", style = Lumen.type.caption, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (showMenu) {
        OptionsDialog("Opciones", buildList {
            audioTracks(tracks).forEach { (label, override, selected) ->
                add(MenuOption("Audio: $label", Icons.Rounded.Audiotrack, selected) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setOverrideForType(override).build()
                })
            }
            val subs = textTracks(tracks)
            if (subs.isNotEmpty()) {
                val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                add(MenuOption("Subtítulos: desactivados", Icons.Rounded.ClosedCaptionDisabled, disabled) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                })
                subs.forEach { (label, override, selected) ->
                    add(MenuOption("Subtítulos: $label", Icons.Rounded.ClosedCaption, selected && !disabled) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(override).build()
                    })
                }
            }
            ResizeMode.entries.forEach { mode ->
                add(MenuOption("Imagen: ${mode.label}", Icons.Rounded.AspectRatio, mode == resize) { resize = mode })
            }
            item?.let { current ->
                if (current.kind == ContentKind.LIVE || current.kind == ContentKind.VOD) {
                    add(MenuOption("Añadir/quitar de favoritos", Icons.Rounded.Favorite) {
                        playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, current.kind, current.itemId) } }
                    })
                }
                add(MenuOption("Abrir en reproductor externo", Icons.AutoMirrored.Rounded.OpenInNew) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW)
                                .setDataAndType(android.net.Uri.parse(current.url), "video/*")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                })
            }
            if (activity?.supportsPip == true) {
                add(MenuOption("Imagen en imagen (PiP)", Icons.Rounded.PictureInPicture) { activity.enterPip() })
            }
        }, onDismiss = { showMenu = false; runCatching { focus.requestFocus() } })
    }

    if (error != null) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Reintentar", onClick = { retries = 0; load(item) }, primary = true)
                PillButton("Salir", onClick = { nav.back() })
            }
        }
    }
}

private data class TrackOption(val label: String, val override: TrackSelectionOverride, val selected: Boolean)

private fun audioTracks(tracks: Tracks) = optionsFor(tracks, C.TRACK_TYPE_AUDIO)
private fun textTracks(tracks: Tracks) = optionsFor(tracks, C.TRACK_TYPE_TEXT)

private fun optionsFor(tracks: Tracks, type: Int): List<TrackOption> = buildList {
    tracks.groups.filter { it.type == type && it.isSupported }.forEach { group ->
        for (i in 0 until group.length) {
            val f = group.getTrackFormat(i)
            val lang = f.language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale("es")) }?.ifBlank { null }
            val label = listOfNotNull(f.label, lang, f.channelCount.takeIf { it > 0 && type == C.TRACK_TYPE_AUDIO }?.let { "$it ch" })
                .distinct().joinToString(" · ").ifBlank { "Pista ${size + 1}" }
            add(TrackOption(label, TrackSelectionOverride(group.mediaTrackGroup, i), group.isTrackSelected(i)))
        }
    }
}

private fun humanError(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (val code = e.httpCode()) {
        403, 426, 458, 509 -> "El proveedor rechazó la conexión (HTTP $code). Suele pasar si tu cuenta ya se está usando en otro dispositivo."
        null -> "El servidor rechazó la conexión."
        else -> "El servidor rechazó la conexión (HTTP $code). Puede que el canal esté caído."
    }
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sin conexión con el servidor. Comprueba la red o la VPN."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Formato no soportado. Prueba otro formato en Ajustes o un reproductor externo."
    else -> e.errorCodeName
}

@OptIn(UnstableApi::class)
private fun PlaybackException.httpCode(): Int? = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode

private fun PlaybackException.isConnectionRefused(): Boolean = httpCode() in setOf(403, 426, 458, 509)
