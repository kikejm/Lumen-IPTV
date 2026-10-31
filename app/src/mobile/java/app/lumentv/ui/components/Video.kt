package app.lumentv.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.lumentv.data.settings.ResizeMode

/**
 * Vídeo de ExoPlayer sin controles nativos (los controles se dibujan en Compose). Igual que en la
 * TV: nunca dentro de una superficie con capa propia, o el SurfaceView se ve negro. Al girar el
 * móvil esta vista se vuelve a crear, pero el reproductor (que vive en la pantalla) sigue sonando.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier, resizeMode: ResizeMode = ResizeMode.FIT) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(true)
                // Al parar o reconectar se queda congelada la última imagen en vez de un fundido a negro.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                keepScreenOn = true
            }
        },
        update = { view ->
            view.player = player
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
