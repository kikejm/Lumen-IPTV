package app.lumentv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.di.AppGraph
import app.lumentv.player.PlayItem
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes

/** Intercepta el botón ☰ (Menú) del mando del Fire TV. */
fun Modifier.onMenuKey(action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.key == Key.Menu) {
        if (e.type == KeyEventType.KeyUp) action()
        true
    } else {
        false
    }
}

/** Arranca la reproducción de una cola y navega al reproductor (o abre el reproductor externo). */
fun AppGraph.play(context: Context, nav: Navigator, items: List<PlayItem>, index: Int) {
    if (items.isEmpty()) return
    val external = settings.current.externalPlayerPackage
    if (external.isNotBlank()) {
        val item = items[index]
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.url), "video/*")
            .setPackage(external)
            .putExtra("title", item.title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
        Toast.makeText(context, "Reproductor externo no disponible, usando el interno", Toast.LENGTH_SHORT).show()
    }
    playback.start(items, index)
    nav.go(Routes.PLAYER)
}

fun AppGraph.playChannels(context: Context, nav: Navigator, p: PlaylistEntity, channels: List<ChannelEntity>, index: Int) =
    play(context, nav, channels.map { streams.live(p, it) }, index)

/** Reanuda una entrada del historial ("Sigue viendo"). */
suspend fun AppGraph.resume(context: Context, nav: Navigator, p: PlaylistEntity, h: HistoryEntity) {
    when (h.kind) {
        ContentKind.LIVE -> {
            val ch = catalog.channel(p.id, h.itemId) ?: return
            val siblings = catalog.channelsIn(p.id, ch.categoryId)
            playChannels(context, nav, p, siblings, siblings.indexOfFirst { it.streamId == ch.streamId }.coerceAtLeast(0))
        }
        ContentKind.VOD -> {
            val m = catalog.movie(p.id, h.itemId) ?: return
            play(context, nav, listOf(streams.movie(p, m, h.extra.ifBlank { m.containerExt }, h.positionMs)), 0)
        }
        ContentKind.EPISODE -> {
            val seriesId = h.extra.substringBefore('|')
            if (seriesId.isNotBlank()) nav.go(Routes.series(seriesId))
        }
        ContentKind.SERIES -> nav.go(Routes.series(h.itemId))
    }
}
