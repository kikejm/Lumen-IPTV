package app.lumentv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.lumentv.core.net.isMeteredNetwork
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.di.AppGraph
import app.lumentv.player.PlayItem
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes

/*
 * Arranque de la reproducción en el móvil: mismo contrato que `Launch.kt` de la TV (cola en
 * `PlaybackSession` + ruta del reproductor), sin la tecla ☰, que aquí es la pulsación larga.
 */

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
    warnMobileData(context)
    playback.start(items, index)
    nav.go(Routes.PLAYER)
}

/** Ya se avisó en esta sesión de que se reproduce con datos móviles. */
private var mobileDataWarned = false

/** Una vez por sesión, al reproducir con datos móviles: cuánto gasta el vídeo (se puede desactivar en Ajustes). */
private fun AppGraph.warnMobileData(context: Context) {
    if (mobileDataWarned || !settings.current.mobileDataWarning || !context.isMeteredNetwork()) return
    mobileDataWarned = true
    Toast.makeText(context, "Usas datos móviles: una hora de vídeo en HD gasta entre 1,5 y 3 GB", Toast.LENGTH_LONG).show()
}

fun AppGraph.playChannels(context: Context, nav: Navigator, p: PlaylistEntity, channels: List<ChannelEntity>, index: Int) =
    play(context, nav, channels.map { streams.live(p, it) }, index)

/** Reanuda una entrada del historial («Sigue viendo»). */
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
