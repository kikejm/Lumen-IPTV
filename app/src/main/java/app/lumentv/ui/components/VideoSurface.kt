package app.lumentv.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.lumentv.data.settings.ResizeMode

/** Vídeo de ExoPlayer sin controles nativos (los controles se dibujan en Compose). */
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
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
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
