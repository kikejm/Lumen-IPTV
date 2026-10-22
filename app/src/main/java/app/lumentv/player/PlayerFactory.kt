package app.lumentv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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

/** Crea instancias de ExoPlayer configuradas según los ajustes del reproductor. */
@OptIn(UnstableApi::class)
class PlayerFactory(private val context: Context, private val http: OkHttpClient, private val settings: SettingsRepository) {

    /** Reproductores que comparten la única conexión de la cuenta (todos menos los de multipantalla). */
    private val exclusivePlayers: MutableSet<ExoPlayer> = Collections.newSetFromMap(WeakHashMap())

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
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT }
            .also { if (exclusive) synchronized(exclusivePlayers) { exclusivePlayers += it } }
    }

    /**
     * Muchas cuentas IPTV solo admiten una conexión: si la previsualización sigue abierta al entrar
     * a pantalla completa (o al revés), el proveedor responde 403/426. Llamar antes de `prepare()`.
     */
    fun claimStream(owner: ExoPlayer) {
        val others = synchronized(exclusivePlayers) { exclusivePlayers.filter { it !== owner } }
        others.forEach { runCatching { it.stop() } }
    }

    companion object {
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
