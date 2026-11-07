package app.lumentv.data.library

import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.FavoriteEntity
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.db.LumenDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Favoritos, historial y posiciones de reanudación. */
class LibraryRepository(private val db: LumenDatabase) {
    private val dao = db.library()

    fun favoriteIds(pid: Long, kind: ContentKind): Flow<Set<String>> = dao.observeFavoriteIds(pid, kind).map { it.toSet() }
    fun favorites(pid: Long, kind: ContentKind) = dao.observeFavorites(pid, kind)

    suspend fun isFavorite(pid: Long, kind: ContentKind, itemId: String): Boolean = dao.isFavorite(pid, kind, itemId) > 0

    suspend fun toggleFavorite(pid: Long, kind: ContentKind, itemId: String): Boolean {
        val isFav = dao.isFavorite(pid, kind, itemId) > 0
        if (isFav) dao.removeFavorite(pid, kind, itemId) else dao.addFavorite(FavoriteEntity(pid, kind, itemId))
        return !isFav
    }

    fun history(pid: Long, limit: Int = 30): Flow<List<HistoryEntity>> = dao.observeHistory(pid, limit)
    fun history(pid: Long, kind: ContentKind, limit: Int = 30): Flow<List<HistoryEntity>> = dao.observeHistory(pid, kind, limit)

    suspend fun historyItem(pid: Long, kind: ContentKind, itemId: String) = dao.historyItem(pid, kind, itemId)

    suspend fun record(entry: HistoryEntity) {
        dao.upsertHistory(entry.copy(watchedAt = System.currentTimeMillis()))
        if (entry.kind == ContentKind.LIVE) dao.trimHistory(entry.playlistId, ContentKind.LIVE, MAX_LIVE_HISTORY)
    }

    /**
     * Canal visto en multipantalla. Va al mismo historial de canales que TV en directo (el reproductor
     * lo apunta igual), así que «Historial» es el mismo en TV en directo y en el selector de multipantalla.
     */
    suspend fun recordChannel(pid: Long, ch: ChannelEntity) =
        record(HistoryEntity(playlistId = pid, kind = ContentKind.LIVE, itemId = ch.streamId, title = ch.name, image = ch.logo))

    suspend fun clearHistory(pid: Long) = dao.clearHistory(pid)

    suspend fun removeHistory(pid: Long, kind: ContentKind, itemId: String) = dao.removeHistory(pid, kind, itemId)

    companion object {
        /** El historial de canales guarda solo los últimos vistos. */
        const val MAX_LIVE_HISTORY = 10
    }
}
