package app.lumentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.lumentv.ui.theme.Lumen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val STREAM_MAX_RETRIES = 5
private const val STREAM_RETRY_DELAY_MS = 3_000L
private const val STREAM_STALL_TIMEOUT_MS = 20_000L

/**
 * Reconexión de los directos que se ven en pequeño (previsualización y multipantalla), con los mismos límites que
 * el reproductor: un error, un fin de emisión o 20 s cargando provocan hasta [STREAM_MAX_RETRIES] intentos, uno cada
 * 3 s. Volver a sonar reinicia la cuenta; agotados los intentos, el reproductor se para y [failed] pasa a `true`.
 */
@Stable
class StreamReconnect internal constructor(
    private val player: Player,
    private val scope: CoroutineScope,
    private val isActive: () -> Boolean,
    private val reload: () -> Unit,
) {
    /** Intento en curso (1…5); 0 si el vídeo está bien. */
    var attempt by mutableIntStateOf(0)
        private set
    var failed by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun reset() {
        job?.cancel()
        job = null
        attempt = 0
        failed = false
    }

    fun retryNow() {
        reset()
        reload()
    }

    internal fun onFailure() {
        if (!isActive() || failed || job?.isActive == true) return
        if (attempt >= STREAM_MAX_RETRIES) {
            player.stop()
            failed = true
            return
        }
        attempt++
        job = scope.launch {
            delay(STREAM_RETRY_DELAY_MS)
            reload()
        }
    }

    internal fun onPlaying() {
        job?.cancel()
        attempt = 0
        failed = false
    }
}

/** Aviso sobre un vídeo pequeño mientras reconecta. */
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

/** `isActive`: si es `false`, los fallos los gestiona otro (TV en directo a pantalla completa). `reload`: vuelve a abrir el stream. */
@Composable
fun rememberStreamReconnect(player: Player, isActive: () -> Boolean = { true }, reload: () -> Unit): StreamReconnect {
    val scope = rememberCoroutineScope()
    val currentIsActive by rememberUpdatedState(isActive)
    val currentReload by rememberUpdatedState(reload)
    val reconnect = remember(player) { StreamReconnect(player, scope, { currentIsActive() }, { currentReload() }) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                when (state) {
                    Player.STATE_READY -> reconnect.onPlaying()
                    Player.STATE_ENDED -> reconnect.onFailure() // un directo que termina es un corte de emisión
                }
            }
            override fun onPlayerError(error: PlaybackException) = reconnect.onFailure()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // Un corte de conexión suele dejar al reproductor cargando indefinidamente, sin dar error.
    LaunchedEffect(buffering, reconnect.attempt, isActive()) {
        if (buffering) {
            delay(STREAM_STALL_TIMEOUT_MS)
            reconnect.onFailure()
        }
    }
    return reconnect
}
