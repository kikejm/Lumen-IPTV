package app.lumentv.player

import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.MovieEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.epg.Program
import app.lumentv.data.playlist.PlaylistRepository
import app.lumentv.data.settings.SettingsRepository
import app.lumentv.data.xtream.Episode
import app.lumentv.data.xtream.XtreamClient

/** Un elemento reproducible ya resuelto a URL. */
data class PlayItem(
    val kind: ContentKind,
    val itemId: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val url: String,
    /** Canal asociado (directo o catch-up) para mostrar EPG y hacer zapping. */
    val channel: ChannelEntity? = null,
    val startPositionMs: Long = 0,
    /** Datos para reanudar desde el historial (p. ej. `seriesId|ext`). */
    val extra: String = "",
)

/**
 * Cola de reproducción compartida entre pantallas. Se rellena antes de navegar al reproductor:
 * en directo contiene todos los canales de la categoría (zapping con ▲▼), en series los episodios
 * de la temporada (auto-siguiente).
 */
class PlaybackSession {
    var queue: List<PlayItem> = emptyList()
        private set
    var index: Int = 0

    val current: PlayItem? get() = queue.getOrNull(index)

    fun start(items: List<PlayItem>, startIndex: Int) {
        queue = items
        index = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    fun move(delta: Int): PlayItem? {
        if (queue.isEmpty()) return null
        index = (index + delta).mod(queue.size)
        return current
    }

    fun hasNext() = index < queue.size - 1
}

/** Construye URLs de reproducción a partir de la lista activa. */
class StreamResolver(
    private val playlists: PlaylistRepository,
    private val xtream: XtreamClient,
    private val settings: SettingsRepository,
) {
    fun live(p: PlaylistEntity, ch: ChannelEntity): PlayItem = PlayItem(
        kind = ContentKind.LIVE,
        itemId = ch.streamId,
        title = ch.name,
        image = ch.logo,
        url = ch.directUrl.ifEmpty { xtream.liveUrl(playlists.credentials(p), ch.streamId, settings.current.liveFormat.ext) },
        channel = ch,
    )

    fun movie(p: PlaylistEntity, m: MovieEntity, ext: String = m.containerExt, startMs: Long = 0): PlayItem = PlayItem(
        kind = ContentKind.VOD,
        itemId = m.streamId,
        title = m.name,
        image = m.poster,
        url = m.directUrl.ifEmpty { xtream.movieUrl(playlists.credentials(p), m.streamId, ext) },
        startPositionMs = startMs,
        extra = ext,
    )

    fun episode(p: PlaylistEntity, seriesId: String, seriesName: String, cover: String, ep: Episode, startMs: Long = 0): PlayItem = PlayItem(
        kind = ContentKind.EPISODE,
        itemId = ep.id,
        title = seriesName,
        subtitle = "T${ep.season} · E${ep.number}" + (if (ep.title.isNotBlank()) " · ${ep.title}" else ""),
        image = ep.image.ifEmpty { cover },
        url = xtream.episodeUrl(playlists.credentials(p), ep.id, ep.containerExt),
        startPositionMs = startMs,
        extra = "$seriesId|${ep.containerExt}",
    )

    fun catchup(p: PlaylistEntity, ch: ChannelEntity, program: Program): PlayItem {
        val minutes = ((program.stopAt - program.startAt) / 60_000).toInt().coerceAtLeast(1)
        return PlayItem(
            kind = ContentKind.LIVE,
            itemId = ch.streamId,
            title = program.title.ifBlank { ch.name },
            subtitle = "Catch-up · ${ch.name}",
            image = ch.logo,
            url = xtream.timeshiftUrl(playlists.credentials(p), ch.streamId, program.startAt, minutes, p.serverTimezone),
            channel = ch,
        )
    }
}
