package app.lumentv.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.lumentv.data.settings.ResizeMode
import kotlinx.coroutines.delay

/** Segundos sonando sin pintar un fotograma antes de reenganchar la superficie y, si no basta, recargar. */
private const val STUCK_REATTACH_S = 3
private const val STUCK_RELOAD_S = 7

/** Vídeo de ExoPlayer sin controles nativos (los controles se dibujan en Compose). */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier, resizeMode: ResizeMode = ResizeMode.FIT) {
    val viewRef = remember { arrayOfNulls<PlayerView>(1) }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(true)
                // Al parar o reconectar se queda congelada la última imagen en vez de un fundido a negro.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                keepScreenOn = true
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }.also { viewRef[0] = it }
        },
        update = { view ->
            view.player = player
            view.resizeMode = when (resizeMode) {
                ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        onRelease = { it.player = null; viewRef[0] = null },
        modifier = modifier,
    )

    // En el Fire TV, tras un rato, la imagen puede quedarse congelada o en negro mientras sigue el audio
    // (al pasar a otra superficie se arregla). Si sonando con pista de vídeo no se pinta ningún fotograma,
    // se reengancha la superficie; si sigue igual, se recarga el stream.
    val exo = player as? ExoPlayer ?: return
    LaunchedEffect(exo) {
        var lastRendered = -1
        var stuckFor = 0
        // Solo vigila tras ver vídeo de verdad (≥ 5 fps): un canal de radio con imagen fija no se recarga sin fin.
        var armed = false
        while (true) {
            delay(1_000)
            val counters = exo.videoDecoderCounters
            if (!exo.isPlaying || counters == null || !exo.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
                lastRendered = -1
                stuckFor = 0
                armed = false
                continue
            }
            counters.ensureUpdated()
            val rendered = counters.renderedOutputBufferCount
            val delta = if (lastRendered < 0) -1 else rendered - lastRendered
            lastRendered = rendered
            if (delta >= 5) armed = true
            stuckFor = if (armed && delta == 0) stuckFor + 1 else 0
            when (stuckFor) {
                STUCK_REATTACH_S -> viewRef[0]?.let { view ->
                    Log.w("VideoSurface", "Vídeo parado con audio: se reengancha la superficie")
                    view.player = null
                    view.player = exo
                }
                STUCK_RELOAD_S -> {
                    Log.w("VideoSurface", "Vídeo sigue parado: se recarga el stream")
                    exo.stop()
                    exo.prepare()
                    stuckFor = 0
                    armed = false
                }
            }
        }
    }
}
