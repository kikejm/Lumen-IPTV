package app.lumentv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.lumentv.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.util.Collections
import java.util.WeakHashMap

/**
 * Crea instancias de ExoPlayer configuradas según los ajustes del reproductor.
 *
 * `handleAudioFocus`: pedir el foco de audio de Android (pausa con llamadas, no se mezcla con otras
 * apps). Solo en el móvil; la TV sigue como estaba.
 */
@OptIn(UnstableApi::class)
class PlayerFactory(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val handleAudioFocus: Boolean = false,
) {

    /** Reproductores que comparten la única conexión de la cuenta (todos menos los de multipantalla). */
    private val exclusivePlayers: MutableSet<ExoPlayer> = Collections.newSetFromMap(WeakHashMap())

    /** Todos los reproductores creados (también los de multipantalla), para pausarlos en segundo plano. */
    private val allPlayers: MutableSet<ExoPlayer> = Collections.newSetFromMap(WeakHashMap())

    /** Los que estaban sonando (o iban a sonar) al pasar la app a segundo plano: se reanudan al volver. */
    private val pausedInBackground: MutableSet<ExoPlayer> = Collections.newSetFromMap(WeakHashMap())

    /** La app no se ve (y no está en imagen en imagen): ningún reproductor puede sonar. */
    var inBackground: Boolean = false
        private set

    /**
     * `exclusive`: antes de abrir un stream con [claimStream], los demás reproductores exclusivos se
     * paran. Multipantalla pasa `false` porque ahí se quieren varias señales a la vez.
     */
    fun create(lightweight: Boolean = false, exclusive: Boolean = true): ExoPlayer {
        val s = settings.current
        val httpFactory = OkHttpDataSource.Factory(http).setUserAgent(s.userAgent)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val bufferMs = (if (lightweight) 5 else s.bufferSeconds) * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ bufferMs.coerceAtLeast(2_500),
                /* maxBufferMs = */ (bufferMs * 3).coerceAtLeast(10_000),
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .build()
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(s.decoderFallback)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // Multipantalla pide el foco solo para el vídeo que tiene el audio (`setAudioFocus`): si lo
        // pidieran todos, se lo quitarían entre ellos y se pausarían.
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .apply { if (handleAudioFocus) setAudioAttributes(MEDIA_AUDIO, exclusive) }
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addListener(BackgroundGuard(this))
            }
            .also {
                synchronized(allPlayers) { allPlayers += it }
                if (exclusive) synchronized(exclusivePlayers) { exclusivePlayers += it }
            }
    }

    /** Multipantalla: el vídeo que tiene el audio pide el foco y los demás lo sueltan. */
    fun setAudioFocus(player: ExoPlayer, enabled: Boolean) {
        if (handleAudioFocus) runCatching { player.setAudioAttributes(MEDIA_AUDIO, enabled) }
    }

    /**
     * La app pasa a segundo plano sin imagen en imagen (Inicio, otra app, pantalla apagada o ventana
     * PiP cerrada): se pausa todo. Mientras dure, nada vuelve a sonar aunque un reintento lo pida.
     */
    fun enterBackground() {
        inBackground = true
        snapshot(allPlayers).forEach { p ->
            if (p.playWhenReady) {
                synchronized(pausedInBackground) { pausedInBackground += p }
                runCatching { p.playWhenReady = false }
            }
        }
    }

    /** La app vuelve a verse. `resume`: reanudar lo que se pausó (no tras cerrar la ventana PiP). */
    fun enterForeground(resume: Boolean) {
        inBackground = false
        val paused = snapshot(pausedInBackground)
        synchronized(pausedInBackground) { pausedInBackground.clear() }
        // Uno liberado o parado (otro stream se quedó la conexión) ya no se toca.
        if (resume) paused.filter { it.playbackState != Player.STATE_IDLE }.forEach { runCatching { it.playWhenReady = true } }
    }

    private fun snapshot(set: MutableSet<ExoPlayer>): List<ExoPlayer> = synchronized(set) { set.toList() }

    /** Si algo pone a sonar un reproductor con la app en segundo plano (reintento, reconexión), se pausa y se apunta. */
    private inner class BackgroundGuard(private val player: ExoPlayer) : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && inBackground) {
                synchronized(pausedInBackground) { pausedInBackground += player }
                player.playWhenReady = false
            }
        }
    }

    /**
     * Muchas cuentas IPTV solo admiten una conexión: si la previsualización sigue abierta al entrar
     * a pantalla completa (o al revés), el proveedor responde 403/426. Llamar antes de `prepare()`.
     */
    fun claimStream(owner: ExoPlayer) {
        val others = synchronized(exclusivePlayers) { exclusivePlayers.filter { it !== owner } }
        others.forEach { runCatching { it.stop() } }
    }

    private val main = Handler(Looper.getMainLooper())
    private var handedOff: ExoPlayer? = null
    private val releaseUnclaimed = Runnable { handedOff?.release(); handedOff = null }

    /**
     * El reproductor a pantalla completa (al que se llega con «Reanudar» desde Inicio) entrega el suyo a la
     * previsualización de TV en directo para que el canal siga sin recargarse. Si nadie lo recoge enseguida, se libera.
     */
    fun handOff(player: ExoPlayer) {
        main.removeCallbacks(releaseUnclaimed)
        handedOff?.takeIf { it !== player }?.release()
        handedOff = player
        main.postDelayed(releaseUnclaimed, 3_000)
    }

    fun takeHandOff(): ExoPlayer? {
        main.removeCallbacks(releaseUnclaimed)
        return handedOff.also { handedOff = null }
    }

    companion object {
        private val MEDIA_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        /** El reproductor ya tiene ese stream abierto (cargando o sonando) y sin error: no hace falta volver a cargarlo. */
        fun isLoaded(player: Player, url: String): Boolean =
            (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY) && player.playerError == null &&
                player.currentMediaItem?.localConfiguration?.uri?.toString() == url

        fun mediaItem(url: String): MediaItem {
            val path = Uri.parse(url).path.orEmpty().lowercase()
            val builder = MediaItem.Builder().setUri(url)
            when {
                path.endsWith(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                path.endsWith(".ts") -> builder.setMimeType(MimeTypes.VIDEO_MP2T)
            }
            return builder.build()
        }
    }
}
