package app.lumentv.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.lumentv.data.settings.ResizeMode
import app.lumentv.ui.theme.Lumen

/**
 * Vídeo de ExoPlayer sin controles nativos (los controles se dibujan en Compose). Igual que en la
 * TV: nunca dentro de una superficie con capa propia, o el SurfaceView se ve negro. Al girar el
 * móvil esta vista se vuelve a crear, pero el reproductor (que vive en la pantalla) sigue sonando.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier, resizeMode: ResizeMode = ResizeMode.FIT) {
    // La pantalla no se apaga mientras el vídeo suena o carga; en pausa, parado o con error, sí.
    var awake by remember(player) { mutableStateOf(player?.keepsScreenOn() == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) { awake = p.keepsScreenOn() }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(true)
                // Al parar o reconectar se queda congelada la última imagen en vez de un fundido a negro.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.player = player
            view.keepScreenOn = awake
            view.resizeMode = when (resizeMode) {
                ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

private fun Player.keepsScreenOn(): Boolean =
    playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

/** Anillo blanco que gira, sobre vídeo (DESIGN.md: pista al 20 %). */
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(36.dp),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.2f),
        strokeWidth = 3.dp,
    )
}

/** Aviso sobre un vídeo pequeño mientras reconecta (`StreamReconnect`, compartido con la TV). */
@Composable
fun ReconnectPill(attempt: Int, modifier: Modifier = Modifier) {
    LText(
        "Reconectando ($attempt/$STREAM_MAX_RETRIES)",
        style = Lumen.type.caption,
        color = Color.White,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
