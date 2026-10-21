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

/** Crea instancias de ExoPlayer configuradas según los ajustes del reproductor. */
@OptIn(UnstableApi::class)
class PlayerFactory(private val context: Context, private val http: OkHttpClient, private val settings: SettingsRepository) {

    fun create(lightweight: Boolean = false): ExoPlayer {
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
