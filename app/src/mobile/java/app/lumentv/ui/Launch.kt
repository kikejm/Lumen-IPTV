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
import app.lumentv.data.db.PlaylistType
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
        // Como en la TV: reproduce el episodio con la ficha de la serie debajo (Atrás vuelve a ella).
        ContentKind.EPISODE -> {
            val seriesId = h.extra.substringBefore('|')
            if (seriesId.isBlank()) return
            val series = catalog.seriesById(p.id, seriesId)
            // Los episodios se piden antes de navegar: al salir de Inicio se cancela este ámbito.
            val info = if (p.type == PlaylistType.XTREAM) runCatching { xtream.seriesInfo(playlists.credentials(p), seriesId) }.getOrNull() else null
            val eps = info?.seasons?.values?.firstOrNull { list -> list.any { it.id == h.itemId } }
            nav.go(Routes.series(seriesId))
            // Sin episodios (lista M3U o fallo de red) se queda en la ficha de la serie.
            if (info == null || eps == null) return
            var index = eps.indexOfFirst { it.id == h.itemId }
            var startMs = h.positionMs
            // Visto hasta el final: el siguiente de la temporada, desde el principio.
            if (h.durationMs > 0 && h.positionMs >= h.durationMs * 95 / 100 && index + 1 < eps.size) {
                index++
                startMs = 0
            }
            val name = series?.name ?: info.name
            val cover = series?.cover ?: info.cover
            val items = eps.mapIndexed { i, ep -> streams.episode(p, seriesId, name, cover, ep, if (i == index) startMs else 0) }
            play(context, nav, items, index)
        }
        ContentKind.SERIES -> nav.go(Routes.series(h.itemId))
    }
}
