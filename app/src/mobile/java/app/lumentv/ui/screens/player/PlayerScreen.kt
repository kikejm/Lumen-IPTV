package app.lumentv.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import app.lumentv.MobileActivity
import app.lumentv.core.util.TimeFormatter
import app.lumentv.core.util.formatDuration
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.epg.Program
import app.lumentv.data.settings.ResizeMode
import app.lumentv.player.PlayItem
import app.lumentv.player.PlayerFactory
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.LoadingSpinner
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.player.LocalMiniPlayer
import app.lumentv.ui.theme.Lumen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_RETRIES = 5
private const val RETRY_SECONDS = 3
private const val STALL_TIMEOUT_MS = 20_000L
private const val SEEK_MS = 10_000L

/**
 * Reproductor del móvil (Stitch 03-reproductor, 18-reproductor-vertical, 19-reconectando y
 * L19-error-reproductor). La lógica es la de la TV: cola de `PlaybackSession`, historial, una sola
 * conexión por cuenta y reconexión (5 intentos cada 3 s, con la última imagen congelada).
 *
 * - **Vertical**: vídeo 16:9 arriba y, debajo, el programa, botones, EPG y canales para zapear.
 * - **Horizontal**: pantalla completa sin barras del sistema y con gestos: toque = controles;
 *   deslizar en vertical a la izquierda = brillo y a la derecha = volumen; doble toque a un lado =
 *   ±10 s (películas y episodios); deslizar en horizontal en directo = canal anterior/siguiente.
 *
 * Girar el móvil cambia de modo sin cortar el vídeo: el `ExoPlayer` vive con la pantalla. El botón
 * de pantalla completa fuerza horizontal y lo libera al salir. Al volver atrás con algo sonando,
 * el reproductor pasa al mini reproductor (o, desde TV en directo, a la previsualización).
 */
@Composable
fun PlayerScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val activity = context as? MobileActivity
    val view = LocalView.current
    val density = LocalDensity.current
    val mini = LocalMiniPlayer.current
    val scope = rememberCoroutineScope()
    val session = graph.playback
    val settings by graph.settings.settings.collectAsState()
    val playlist by graph.playlists.active.collectAsState(initial = null)
    val fmt = remember(settings.use24h, settings.epgOffsetHours) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val inPip = activity?.isInPip ?: false
    val c = Lumen.colors

    var item by remember { mutableStateOf(session.current) }
    // Si Android cerró la app en segundo plano, al volver se restaura esta ruta sin cola: no hay nada
    // que reproducir y se quedaba cargando para siempre.
    LaunchedEffect(Unit) { if (item == null) nav.back() }
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
    var locked by remember { mutableStateOf(false) }
    var forcedLandscape by remember { mutableStateOf(false) }
    var forcedPortrait by remember { mutableStateOf(false) }

    // El reproductor llega aparcado en el mini reproductor, traspasado desde la previsualización de
    // TV en directo o se crea nuevo. En los dos primeros casos ya tiene el stream abierto.
    val player = remember { mini.take() ?: graph.players.takeHandOff() ?: graph.players.create() }
    var keepPlayer by remember { mutableStateOf(false) }

    var knownPosition by remember { mutableLongStateOf(0L) }
    var knownDuration by remember { mutableLongStateOf(0L) }
    var hasPlayed by remember { mutableStateOf(false) }

    fun snapshotProgress() {
        val d = player.duration
        if (d != C.TIME_UNSET && d > 0 && player.playerError == null && player.playbackState != Player.STATE_IDLE) {
            knownPosition = player.currentPosition.coerceAtLeast(0)
            knownDuration = d
        }
    }

    // En el scope de la app: al salir del reproductor el de la pantalla ya está cancelado.
    fun saveHistory(it: PlayItem) {
        val known = playlist
        snapshotProgress()
        val pos = knownPosition
        val dur = knownDuration
        if (it.kind != ContentKind.LIVE && dur == 0L) return
        // Un catch-up se apunta como el canal (con su nombre), no con el título del programa.
        val catchupChannel = it.channel?.takeIf { _ -> it.kind == ContentKind.LIVE && it.subtitle.startsWith("Catch-up") }
        graph.appScope.launch {
            val p = known ?: graph.playlists.activeNow() ?: return@launch
            graph.library.record(
                HistoryEntity(
                    playlistId = p.id,
                    kind = it.kind,
                    itemId = it.itemId,
                    title = catchupChannel?.name ?: it.title,
                    subtitle = if (catchupChannel != null) "" else it.subtitle,
                    image = catchupChannel?.logo ?: it.image,
                    positionMs = if (it.kind == ContentKind.LIVE) 0 else pos,
                    durationMs = if (it.kind == ContentKind.LIVE) 0 else dur,
                    extra = it.extra,
                ),
            )
        }
    }

    fun load(target: PlayItem?, reuse: Boolean = false) {
        target ?: return
        if (target.itemId != item?.itemId || target.kind != item?.kind) {
            knownPosition = target.startPositionMs
            knownDuration = 0
            hasPlayed = false
        }
        item = target
        error = null
        graph.players.claimStream(player)
        if (!reuse || !PlayerFactory.isLoaded(player, target.url)) {
            player.setMediaItem(PlayerFactory.mediaItem(target.url), target.startPositionMs)
            player.prepare()
        }
        player.playWhenReady = true
        overlay = true
        overlayTick++
        saveHistory(target)
    }

    var retryJob by remember { mutableStateOf<Job?>(null) }
    var retryIn by remember { mutableIntStateOf(0) }

    fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
        retryIn = 0
    }

    fun reload() {
        val current = item ?: return
        snapshotProgress()
        load(if (current.kind == ContentKind.LIVE) current else current.copy(startPositionMs = knownPosition))
    }

    fun scheduleRetry(e: PlaybackException?) {
        item ?: return
        if (retryJob?.isActive == true) return
        snapshotProgress()
        item?.let { saveHistory(it) }
        if (retries >= MAX_RETRIES) {
            player.stop()
            error = e?.let(::humanError) ?: "No responde: lleva más de ${STALL_TIMEOUT_MS / 1000} s sin cargar."
            return
        }
        retries++
        player.stop() // la última imagen se queda congelada (VideoSurface no la tapa)
        retryJob = scope.launch {
            for (s in RETRY_SECONDS downTo 1) {
                retryIn = s
                delay(1_000)
            }
            retryIn = 0
            retryJob = null
            reload()
        }
    }

    fun retryNow() {
        cancelRetry()
        retries = 0
        reload()
    }

    fun zap(delta: Int) {
        item?.let { saveHistory(it) }
        cancelRetry()
        retries = 0
        load(session.move(delta))
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    retries = 0
                    hasPlayed = true
                }
                if (state == Player.STATE_ENDED) {
                    val current = item
                    if (current?.kind == ContentKind.EPISODE && session.hasNext()) zap(1)
                    else if (current?.kind == ContentKind.LIVE) load(current) // stream cortado: reconecta
                }
            }
            override fun onPlayerError(e: PlaybackException) = scheduleRetry(e)
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(listener)
        activity?.pipPlayer = player
        activity?.playerActive = true
        buffering = player.playbackState != Player.STATE_READY
        isPlaying = player.isPlaying
        tracks = player.currentTracks
        load(item, reuse = true)
        onDispose {
            item?.let { saveHistory(it) }
            activity?.playerActive = false
            activity?.let { if (it.pipPlayer === player) it.pipPlayer = null }
            player.removeListener(listener)
            if (!keepPlayer) player.release()
        }
    }

    // Pantalla completa en horizontal: sin barras del sistema (aparecen al deslizar desde el borde).
    val fullscreen = landscape && !inPip
    DisposableEffect(fullscreen) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // Al salir: orientación libre y brillo del sistema otra vez.
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.let { a ->
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                a.window.attributes = a.window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            snapshotProgress()
            val stopped = player.playbackState == Player.STATE_IDLE
            position = if (stopped) knownPosition else player.currentPosition
            duration = player.duration.takeIf { !stopped && it != C.TIME_UNSET } ?: knownDuration
            if (++tick % 30 == 0) item?.takeIf { it.kind != ContentKind.LIVE }?.let { saveHistory(it) }
            delay(500)
        }
    }
    LaunchedEffect(item?.itemId, playlist?.id) {
        val p = playlist
        val ch = item?.channel
        programs = if (p != null && ch != null && item?.subtitle?.startsWith("Catch-up") != true) graph.epg.nowAndNext(p, ch, 4) else emptyList()
    }
    // Los controles se ocultan a los 4 s sin tocar nada (solo a pantalla completa).
    LaunchedEffect(overlayTick, overlay, isPlaying, fullscreen) {
        if (overlay && isPlaying && fullscreen) { delay(4_000); overlay = false }
    }

    val isLive = item?.kind == ContentKind.LIVE && item?.subtitle?.startsWith("Catch-up") != true
    LaunchedEffect(buffering, retries, error, hasPlayed) {
        if (!buffering || error != null || (!isLive && !hasPlayed)) return@LaunchedEffect
        delay(STALL_TIMEOUT_MS)
        if (buffering && retryJob == null) scheduleRetry(null)
    }

    fun showOverlay() { overlay = true; overlayTick++ }
    fun togglePlay() { if (player.isPlaying) player.pause() else player.play(); showOverlay() }
    fun seekBy(ms: Long) {
        if (isLive) return
        player.seekTo((player.currentPosition + ms).coerceIn(0, (player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE)))
        showOverlay()
    }
    fun toggleFullscreen() {
        val a = context as? Activity ?: return
        if (landscape) {
            // Con el móvil en horizontal, dejar la orientación libre no hacía nada: se fuerza vertical
            // y se libera cuando el móvil se pone de pie (`forcedPortrait`).
            forcedLandscape = false
            forcedPortrait = true
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            forcedPortrait = false
            forcedLandscape = true
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    DisposableEffect(forcedPortrait) {
        val a = context as? Activity
        if (!forcedPortrait || a == null) return@DisposableEffect onDispose {}
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return
                if (degrees < 25 || degrees > 335) {
                    a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    forcedPortrait = false
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable() else forcedPortrait = false
        onDispose { listener.disable() }
    }
    fun leave() {
        val current = item
        when {
            // De vuelta a TV en directo: la previsualización se queda este reproductor y el canal sigue sin recargar.
            isLive && error == null && current?.channel != null && nav.previousRoute == Routes.LIVE -> {
                keepPlayer = true
                graph.players.handOff(player)
            }
            // Con algo sonando, se aparca en el mini reproductor.
            current != null && error == null && (player.isPlaying || buffering) -> {
                keepPlayer = true
                mini.dock(player, current)
            }
        }
        nav.back()
    }

    // Atrás: con el candado puesto no hace nada; a pantalla completa forzada, vuelve a vertical;
    // si no, sale (y el vídeo sigue en el mini reproductor o en la previsualización).
    BackHandler(enabled = locked) { showOverlay() }
    BackHandler(enabled = !locked && forcedLandscape && landscape) { toggleFullscreen() }
    BackHandler(enabled = !locked && !(forcedLandscape && landscape)) { leave() }

    // Gestos a pantalla completa: brillo/volumen (vertical) y zapping (horizontal en directo).
    var gestureLabel by remember { mutableStateOf<Pair<ImageVector, Int>?>(null) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var brightness by remember { mutableFloatStateOf(initialBrightness(context)) }

    val video: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .background(Color.Black)
                .pointerInput(isLive, locked) {
                    detectTapGestures(
                        // Un toque muestra u oculta los controles (con candado, solo muestra el botón de desbloquear).
                        onTap = { if (overlay && !locked) overlay = false else showOverlay() },
                        onDoubleTap = { offset ->
                            if (!locked && !isLive) seekBy(if (offset.x < size.width / 2) -SEEK_MS else SEEK_MS)
                        },
                    )
                }
                .then(
                    if (!fullscreen) Modifier else Modifier.pointerInput(isLive, locked, session.queue.size) {
                        if (locked) return@pointerInput
                        var mode = 0 // 0 sin decidir, 1 brillo, 2 volumen, 3 zapping
                        var dx = 0f
                        var dy = 0f
                        var startVolume = 0
                        var startBrightness = 0f
                        val slop = with(density) { 16.dp.toPx() }
                        detectDragGestures(
                            onDragStart = { start ->
                                mode = 0; dx = 0f; dy = 0f
                                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                startBrightness = brightness
                                // El lado se decide por dónde empieza el dedo.
                                mode = if (start.x < size.width / 2) -1 else -2
                            },
                            onDragEnd = {
                                if (mode == 3 && abs(dx) > size.width / 6) zap(if (dx < 0) 1 else -1)
                                gestureLabel = null
                            },
                            onDragCancel = { gestureLabel = null },
                            onDrag = { change, amount ->
                                change.consume()
                                dx += amount.x
                                dy += amount.y
                                if (mode < 0) {
                                    when {
                                        abs(dy) > slop && abs(dy) > abs(dx) -> mode = if (mode == -1) 1 else 2
                                        abs(dx) > slop && isLive && session.queue.size > 1 -> mode = 3
                                    }
                                }
                                val fraction = -dy / (size.height * 0.8f)
                                when (mode) {
                                    1 -> {
                                        brightness = (startBrightness + fraction).coerceIn(0.02f, 1f)
                                        (context as? Activity)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
                                        gestureLabel = Icons.Rounded.BrightnessMedium to (brightness * 100).toInt()
                                    }
                                    2 -> {
                                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val v = (startVolume + fraction * max).toInt().coerceIn(0, max)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                        gestureLabel = Icons.Rounded.VolumeUp to (v * 100 / max.coerceAtLeast(1))
                                    }
                                }
                            },
                        )
                    },
                ),
        ) {
            VideoSurface(player, Modifier.fillMaxSize(), resize)
            if ((buffering || retryIn > 0) && error == null) LoadingSpinner(Modifier.align(Alignment.Center))
            gestureLabel?.let { (icon, value) -> GestureBadge(icon, value, Modifier.align(Alignment.Center)) }
            if (retryIn > 0 && error == null) {
                LText(
                    "Error de reproducción, vuelve a conectar en $retryIn s ($retries/$MAX_RETRIES)",
                    style = Lumen.type.callout,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            error?.let { msg -> ErrorPanel(msg, onRetry = ::retryNow, onOther = if (isLive && session.queue.size > 1) ({ zap(1) }) else null, onBack = ::leave) }
        }
    }

    when {
        // En PiP solo el vídeo.
        inPip -> Box(Modifier.fillMaxSize().background(Color.Black)) { VideoSurface(player, Modifier.fillMaxSize(), resize) }

        fullscreen -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            video(Modifier.fillMaxSize())
            AnimatedVisibility(visible = overlay && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                if (locked) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                        IconCircleButton(Icons.Rounded.Lock, "Desbloquear", onClick = { locked = false; showOverlay() }, modifier = Modifier.align(Alignment.CenterEnd), color = Color.Black.copy(alpha = 0.5f))
                    }
                } else {
                    FullscreenControls(
                        item = item, isLive = isLive, isPlaying = isPlaying, programs = programs, fmt = fmt,
                        position = position, duration = duration, canZap = isLive && session.queue.size > 1,
                        onBack = { if (forcedLandscape) toggleFullscreen() else leave() },
                        onTogglePlay = ::togglePlay,
                        onSeek = { player.seekTo(it); showOverlay() },
                        onSeekBy = ::seekBy,
                        onZap = ::zap,
                        onMenu = { showMenu = true },
                        onPip = if (activity?.supportsPip == true) ({ activity.enterPip() }) else null,
                        onLock = { locked = true; showOverlay() },
                        onExitFullscreen = ::toggleFullscreen,
                    )
                }
            }
        }

        else -> Column(Modifier.fillMaxSize().background(c.background).padding(insets)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                video(Modifier.fillMaxSize())
                androidx.compose.animation.AnimatedVisibility(visible = overlay && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                    PortraitVideoControls(
                        isLive = isLive, isPlaying = isPlaying, position = position, duration = duration,
                        onBack = ::leave, onTogglePlay = ::togglePlay, onSeek = { player.seekTo(it); showOverlay() },
                        onPip = if (activity?.supportsPip == true) ({ activity.enterPip() }) else null,
                        onFullscreen = ::toggleFullscreen,
                    )
                }
            }
            PortraitDetails(
                item = item, isLive = isLive, programs = programs, fmt = fmt,
                queue = session.queue, index = session.index,
                onPick = { i -> if (i != session.index) zap(i - session.index) },
                onFavorite = {
                    val current = item ?: return@PortraitDetails
                    playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, current.kind, current.itemId) } }
                },
                onMenu = { showMenu = true },
                onFullscreen = ::toggleFullscreen,
            )
        }
    }

    if (showMenu) {
        OptionsSheet("Opciones", buildList {
            audioTracks(tracks).forEach { t ->
                add(SheetOption("Audio: ${t.label}", Icons.Rounded.Audiotrack, detail = if (t.selected) "✓" else null) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(t.override).build()
                })
            }
            val subs = textTracks(tracks)
            if (subs.isNotEmpty()) {
                val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                add(SheetOption("Subtítulos: desactivados", Icons.Rounded.ClosedCaptionDisabled, detail = if (disabled) "✓" else null) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                })
                subs.forEach { t ->
                    add(SheetOption("Subtítulos: ${t.label}", Icons.Rounded.ClosedCaption, detail = if (t.selected && !disabled) "✓" else null) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(t.override).build()
                    })
                }
            }
            ResizeMode.entries.forEach { mode ->
                add(SheetOption("Imagen: ${mode.label}", Icons.Rounded.AspectRatio, detail = if (mode == resize) "✓" else null) { resize = mode })
            }
            item?.let { current ->
                if (current.kind == ContentKind.LIVE || current.kind == ContentKind.VOD) {
                    add(SheetOption("Añadir/quitar de favoritos", Icons.Rounded.Favorite) {
                        playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, current.kind, current.itemId) } }
                    })
                }
                add(SheetOption("Abrir en otro reproductor", Icons.AutoMirrored.Rounded.OpenInNew) {
                    // El reproductor de la app se cierra: si no, seguiría ocupando la conexión de la
                    // cuenta y sonando por debajo. Sin «acción del usuario» para que no salte el PiP.
                    saveHistory(current)
                    activity?.playerActive = false
                    player.stop()
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW)
                                .setDataAndType(android.net.Uri.parse(current.url), "video/*")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                        )
                    }
                    nav.back()
                })
            }
            if (activity?.supportsPip == true) add(SheetOption("Imagen en imagen (PiP)", Icons.Rounded.PictureInPicture) { activity.enterPip() })
        }, onDismiss = { showMenu = false })
    }
}

@Composable
private fun FullscreenControls(
    item: PlayItem?,
    isLive: Boolean,
    isPlaying: Boolean,
    programs: List<Program>,
    fmt: TimeFormatter,
    position: Long,
    duration: Long,
    canZap: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onZap: (Int) -> Unit,
    onMenu: () -> Unit,
    onPip: (() -> Unit)?,
    onLock: () -> Unit,
    onExitFullscreen: () -> Unit,
) {
    val c = Lumen.colors
    val scrimTop = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))
    val scrimBottom = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).align(Alignment.TopCenter).background(scrimTop))
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter).background(scrimBottom))
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Arriba: atrás, canal/título y acciones.
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverlayButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onBack)
                item?.let { Artwork(it.image, it.title, Modifier.size(width = 56.dp, height = 36.dp), radius = 6.dp, background = Color.White.copy(alpha = 0.1f)) }
                Column(Modifier.weight(1f)) {
                    LText(item?.title.orEmpty(), style = Lumen.type.headline, color = Color.White, maxLines = 1)
                    val sub = if (isLive) programs.firstOrNull()?.let { "${fmt.range(it.startAt, it.stopAt)} · ${it.title}" } else item?.subtitle
                    sub?.takeIf { it.isNotBlank() }?.let { LText(it, style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
                }
                onPip?.let { OverlayButton(Icons.Rounded.PictureInPicture, "Imagen en imagen", it) }
                OverlayButton(Icons.Rounded.MoreVert, "Opciones", onMenu)
                OverlayButton(Icons.Rounded.LockOpen, "Bloquear pantalla", onLock)
            }
            // Centro: anterior / -10 s, reproducir, +10 s / siguiente.
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (isLive) {
                    if (canZap) OverlayButton(Icons.Rounded.SkipPrevious, "Canal anterior", { onZap(-1) })
                } else {
                    OverlayButton(Icons.Rounded.Replay10, "10 s atrás", { onSeekBy(-SEEK_MS) })
                }
                TapSurface(onClick = onTogglePlay, modifier = Modifier.size(72.dp), radius = 36.dp, color = Color.White) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pausa" else "Reproducir", tint = Color.Black, modifier = Modifier.align(Alignment.Center).size(40.dp))
                }
                if (isLive) {
                    if (canZap) OverlayButton(Icons.Rounded.SkipNext, "Canal siguiente", { onZap(1) })
                } else {
                    OverlayButton(Icons.Rounded.Forward10, "10 s adelante", { onSeekBy(SEEK_MS) })
                }
            }
            // Abajo: progreso (programa en directo o película) y salir de pantalla completa.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isLive) {
                    programs.firstOrNull()?.let { now ->
                        ProgressLine(now.progress(), color = c.accent, height = 3.dp)
                        Row {
                            LText(fmt.time(now.startAt), style = Lumen.type.caption, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                            programs.getOrNull(1)?.let { next -> LText("Después: ${fmt.time(next.startAt)} · ${next.title}", style = Lumen.type.caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
                        }
                    }
                } else {
                    SeekBar(position, duration, onSeek)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LText(
                        if (isLive) (if (canZap) "Desliza a los lados para cambiar de canal · arriba/abajo: brillo y volumen" else "Arriba/abajo: brillo (izquierda) y volumen (derecha)")
                        else "Doble toque a un lado: ±10 s · arriba/abajo: brillo y volumen",
                        style = Lumen.type.caption,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    OverlayButton(Icons.Rounded.FullscreenExit, "Salir de pantalla completa", onExitFullscreen)
                }
            }
        }
    }
}

@Composable
private fun PortraitVideoControls(
    isLive: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPip: (() -> Unit)?,
    onFullscreen: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onBack)
            Box(Modifier.weight(1f))
            onPip?.let { OverlayButton(Icons.Rounded.PictureInPicture, "Imagen en imagen", it) }
            OverlayButton(Icons.Rounded.Fullscreen, "Pantalla completa", onFullscreen)
        }
        TapSurface(onClick = onTogglePlay, modifier = Modifier.align(Alignment.Center).size(56.dp), radius = 28.dp, color = Color.Black.copy(alpha = 0.5f)) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pausa" else "Reproducir", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        if (!isLive) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp)) { SeekBar(position, duration, onSeek) }
    }
}

@Composable
private fun PortraitDetails(
    item: PlayItem?,
    isLive: Boolean,
    programs: List<Program>,
    fmt: TimeFormatter,
    queue: List<PlayItem>,
    index: Int,
    onPick: (Int) -> Unit,
    onFavorite: () -> Unit,
    onMenu: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val c = Lumen.colors
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LText(item?.title.orEmpty(), style = Lumen.type.title, maxLines = 2)
                if (isLive) {
                    programs.firstOrNull()?.let { now ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LText(fmt.range(now.startAt, now.stopAt), style = Lumen.type.callout, color = c.accent)
                            LText(now.title, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                        ProgressLine(now.progress())
                        if (now.description.isNotBlank()) LText(now.description, style = Lumen.type.body, color = c.textSecondary, maxLines = 3)
                    } ?: LText("Sin información de programación", style = Lumen.type.callout, color = c.textTertiary)
                } else {
                    item?.subtitle?.takeIf { it.isNotBlank() }?.let { LText(it, style = Lumen.type.callout, color = c.textSecondary) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Favorito", onClick = onFavorite, icon = Icons.Rounded.Favorite)
                PillButton("Audio", onClick = onMenu, icon = Icons.Rounded.Audiotrack)
                PillButton("Completa", onClick = onFullscreen, icon = Icons.Rounded.Fullscreen)
            }
        }
        if (isLive && programs.size > 1) {
            item { LText("A CONTINUACIÓN", style = Lumen.type.caption, color = c.textSecondary) }
            programs.drop(1).forEachIndexed { i, prog ->
                item(key = "p-$i-${prog.startAt}") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LText(fmt.time(prog.startAt), style = Lumen.type.callout, color = c.textSecondary, modifier = Modifier.width(52.dp))
                        LText(prog.title, style = Lumen.type.callout, maxLines = 1)
                    }
                }
            }
        }
        if (queue.size > 1) {
            item { LText(if (isLive) "CANALES" else "EN LA COLA", style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(queue, key = { i, q -> "q-$i-${q.itemId}" }) { i, q ->
                val current = i == index
                TapSurface(onClick = { onPick(i) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), radius = 12.dp, color = if (current) c.surfaceElevated else Color.Transparent) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Artwork(q.image, q.title, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
                        LText(q.title, style = Lumen.type.callout, color = if (current) c.accent else c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                        if (current) Icon(Icons.Rounded.PlayArrow, contentDescription = "Sonando", tint = c.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item { LText("Gira el móvil para verlo a pantalla completa.", style = Lumen.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) position.toFloat() / duration else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LText(formatDuration(if (dragging != null) (fraction * duration).toLong() else position), style = Lumen.type.caption, color = Color.White)
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { onSeek((it * duration).toLong()) }; dragging = null },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.weight(1f),
        )
        LText("-" + formatDuration((duration - position).coerceAtLeast(0)), style = Lumen.type.caption, color = Color.White)
    }
}

@Composable
private fun OverlayButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconCircleButton(icon, description, onClick = onClick, color = Color.Black.copy(alpha = 0.45f))
}

@Composable
private fun GestureBadge(icon: ImageVector, value: Int, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        LText("$value %", style = Lumen.type.headline, color = Color.White)
    }
}

/**
 * Agotados los reintentos (Stitch L19-error-reproductor). En vertical el vídeo mide unos 200 dp de
 * alto: ahí el panel va sin icono, los botones bajan de línea si no caben y, si aun así no cabe, se desplaza.
 */
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.ErrorPanel(message: String, onRetry: () -> Unit, onOther: (() -> Unit)?, onBack: () -> Unit) {
    val c = Lumen.colors
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.6f)))
    BoxWithConstraints(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
        val small = maxHeight < 320.dp
        Column(
            Modifier.padding(if (small) 8.dp else 16.dp).clip(RoundedCornerShape(20.dp)).background(c.surface.copy(alpha = 0.9f))
                .verticalScroll(rememberScrollState()).padding(if (small) 12.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (small) 6.dp else 10.dp),
        ) {
            if (!small) Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.warning, modifier = Modifier.size(40.dp))
            LText("No se puede reproducir", style = if (small) Lumen.type.headline else Lumen.type.title, color = Color.White)
            LText(message, style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = if (small) 2 else 4)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton("Reintentar", onClick = onRetry, primary = true)
                onOther?.let { PillButton("Otro canal", onClick = it) }
                PillButton("Volver", onClick = onBack)
            }
        }
    }
}

private fun initialBrightness(context: Context): Float {
    val window = (context as? Activity)?.window
    val current = window?.attributes?.screenBrightness ?: -1f
    if (current >= 0f) return current
    // El ajuste del sistema va de 0 a 255 en la mayoría, pero muchos móviles usan escalas mayores
    // (1023, 2047, 4095…): dividir siempre entre 255 hacía que el primer gesto saltara al máximo.
    return runCatching {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        val max = BRIGHTNESS_SCALES.firstOrNull { raw <= it } ?: raw
        (raw.toFloat() / max).coerceIn(0.01f, 1f)
    }.getOrDefault(0.5f)
}

private val BRIGHTNESS_SCALES = listOf(255, 1023, 2047, 4095, 8191, 16383, 65535)

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
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "Se perdió la señal del directo."
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "El contenido ya no está disponible en el servidor."
    else -> "Error de reproducción (código ${e.errorCode})."
}

@OptIn(UnstableApi::class)
private fun PlaybackException.httpCode(): Int? = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
