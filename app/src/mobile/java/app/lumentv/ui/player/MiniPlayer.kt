package app.lumentv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.lumentv.player.PlayItem
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.theme.Lumen

/**
 * Mini reproductor (Stitch 20-mini-reproductor): al salir del reproductor con algo sonando, su
 * `ExoPlayer` se «aparca» aquí y sigue reproduciéndose en una barra sobre la navegación. Tocarla
 * vuelve a pantalla completa con el mismo reproductor, sin recargar.
 *
 * Respeta la regla de una sola conexión por cuenta: si otra pantalla abre un stream
 * (`PlayerFactory.claimStream` para este reproductor), la barra se cierra sola.
 */
@Stable
class MiniPlayerController(
    /** Antes de liberar el reproductor: guarda en el historial el minuto en que se quedó (películas y episodios). */
    private val onRelease: (ExoPlayer, PlayItem) -> Unit = { _, _ -> },
) {
    var player: ExoPlayer? by mutableStateOf(null)
        private set
    var item: PlayItem? by mutableStateOf(null)
        private set

    fun dock(player: ExoPlayer, item: PlayItem) {
        if (this.player !== player) close()
        this.player = player
        this.item = item
    }

    /** Devuelve el reproductor aparcado (sin liberarlo) para que lo use el reproductor a pantalla completa. */
    fun take(): ExoPlayer? = player.also {
        player = null
        item = null
    }

    fun close() {
        val p = player
        val playing = item
        player = null
        item = null
        if (p != null && playing != null) runCatching { onRelease(p, playing) }
        p?.release()
    }
}

val LocalMiniPlayer = staticCompositionLocalOf { MiniPlayerController() }

@Composable
fun MiniPlayerBar(controller: MiniPlayerController, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val player = controller.player ?: return
    val item = controller.item ?: return
    val c = Lumen.colors
    var playing by androidx.compose.runtime.remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                // Parado por otro reproductor (una sola conexión por cuenta) o terminado: fuera la barra.
                if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) controller.close()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    TapSurface(onClick = onOpen, modifier = modifier.fillMaxWidth().height(64.dp), radius = 0.dp, color = c.surfaceElevated) {
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)).background(Color.Black)) {
                VideoSurface(player, Modifier.fillMaxHeight().aspectRatio(16f / 9f))
            }
            Column(Modifier.weight(1f)) {
                LText(item.title, style = Lumen.type.callout.copy(fontWeight = Lumen.type.headline.fontWeight), maxLines = 1)
                if (item.subtitle.isNotBlank()) LText(item.subtitle, style = Lumen.type.caption, color = c.textSecondary, maxLines = 1)
            }
            IconCircleButton(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pausa" else "Reproducir",
                onClick = { if (player.isPlaying) player.pause() else player.play() },
                color = Color.Transparent,
            )
            IconCircleButton(Icons.Rounded.Close, "Cerrar", onClick = { controller.close() }, color = Color.Transparent)
        }
    }
}
