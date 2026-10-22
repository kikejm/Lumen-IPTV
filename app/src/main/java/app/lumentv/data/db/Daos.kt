package app.lumentv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun all(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun byId(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insert(p: PlaylistEntity): Long

    @Update
    suspend fun update(p: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CatalogDao {
    // --- Categorías ---
    @Query("SELECT * FROM categories WHERE playlistId = :pid AND kind = :kind ORDER BY sortOrder")
    fun observeCategories(pid: Long, kind: ContentKind): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :pid AND kind = :kind")
    suspend fun clearCategories(pid: Long, kind: ContentKind)

    // --- Canales ---
    @Query("SELECT * FROM channels WHERE playlistId = :pid ORDER BY sortOrder")
    fun observeAllChannels(pid: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND categoryId = :cat ORDER BY sortOrder")
    fun observeChannels(pid: Long, cat: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND categoryId = :cat ORDER BY sortOrder")
    suspend fun channels(pid: Long, cat: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :pid ORDER BY sortOrder")
    suspend fun allChannels(pid: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND streamId IN (:ids)")
    suspend fun channelsByIds(pid: Long, ids: List<String>): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND streamId = :id")
    suspend fun channel(pid: Long, id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :pid AND tvArchive = 1 ORDER BY sortOrder")
    fun observeArchiveChannels(pid: Long): Flow<List<ChannelEntity>>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :pid")
    fun observeChannelCount(pid: Long): Flow<Int>

    @Query("SELECT DISTINCT lower(epgChannelId) FROM channels WHERE playlistId = :pid AND epgChannelId != ''")
    suspend fun epgChannelKeys(pid: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :pid")
    suspend fun clearChannels(pid: Long)

    // --- Películas ---
    @Query("SELECT * FROM movies WHERE playlistId = :pid ORDER BY addedAt DESC")
    fun observeAllMovies(pid: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND categoryId = :cat ORDER BY sortOrder")
    fun observeMovies(pid: Long, cat: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND streamId = :id")
    suspend fun movie(pid: Long, id: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND streamId IN (:ids)")
    suspend fun moviesByIds(pid: Long, ids: List<String>): List<MovieEntity>

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :pid")
    fun observeMovieCount(pid: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(items: List<MovieEntity>)

    @Query("DELETE FROM movies WHERE playlistId = :pid")
    suspend fun clearMovies(pid: Long)

    // --- Series ---
    @Query("SELECT * FROM series WHERE playlistId = :pid ORDER BY sortOrder")
    fun observeAllSeries(pid: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND categoryId = :cat ORDER BY sortOrder")
    fun observeSeries(pid: Long, cat: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND seriesId = :id")
    suspend fun series(pid: Long, id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE playlistId = :pid AND seriesId IN (:ids)")
    suspend fun seriesByIds(pid: Long, ids: List<String>): List<SeriesEntity>

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :pid")
    fun observeSeriesCount(pid: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(items: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE playlistId = :pid")
    suspend fun clearSeries(pid: Long)

    // --- Búsqueda maestra ---
    @Query("SELECT * FROM channels WHERE playlistId = :pid AND name LIKE '%' || :q || '%' ORDER BY sortOrder LIMIT :limit")
    suspend fun searchChannels(pid: Long, q: String, limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :pid AND name LIKE '%' || :q || '%' ORDER BY addedAt DESC LIMIT :limit")
    suspend fun searchMovies(pid: Long, q: String, limit: Int): List<MovieEntity>

    @Query("SELECT * FROM series WHERE playlistId = :pid AND name LIKE '%' || :q || '%' ORDER BY sortOrder LIMIT :limit")
    suspend fun searchSeries(pid: Long, q: String, limit: Int): List<SeriesEntity>

    @Query("DELETE FROM categories WHERE playlistId = :pid")
    suspend fun clearAllCategories(pid: Long)
}

@Dao
interface ProgramDao {
    @Query(
        "SELECT * FROM programs WHERE playlistId = :pid AND channelKey = :key " +
            "AND stopAt > :from AND startAt < :to ORDER BY startAt",
    )
    suspend fun programs(pid: Long, key: String, from: Long, to: Long): List<ProgramEntity>

    @Query(
        "SELECT * FROM programs WHERE playlistId = :pid AND channelKey = :key " +
            "AND stopAt > :from AND startAt < :to ORDER BY startAt",
    )
    fun observePrograms(pid: Long, key: String, from: Long, to: Long): Flow<List<ProgramEntity>>

    @Insert
    suspend fun insert(items: List<ProgramEntity>)

    @Query("DELETE FROM programs WHERE playlistId = :pid")
    suspend fun clear(pid: Long)

    @Query("DELETE FROM programs WHERE stopAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM programs WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM favorites WHERE playlistId = :pid AND kind = :kind ORDER BY addedAt DESC")
    fun observeFavorites(pid: Long, kind: ContentKind): Flow<List<FavoriteEntity>>

    @Query("SELECT itemId FROM favorites WHERE playlistId = :pid AND kind = :kind")
    fun observeFavoriteIds(pid: Long, kind: ContentKind): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :pid AND kind = :kind AND itemId = :itemId")
    suspend fun removeFavorite(pid: Long, kind: ContentKind, itemId: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE playlistId = :pid AND kind = :kind AND itemId = :itemId")
    suspend fun isFavorite(pid: Long, kind: ContentKind, itemId: String): Int

    @Query("SELECT * FROM history WHERE playlistId = :pid ORDER BY watchedAt DESC LIMIT :limit")
    fun observeHistory(pid: Long, limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE playlistId = :pid AND kind = :kind ORDER BY watchedAt DESC LIMIT :limit")
    fun observeHistory(pid: Long, kind: ContentKind, limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE playlistId = :pid AND kind = :kind AND itemId = :itemId")
    suspend fun historyItem(pid: Long, kind: ContentKind, itemId: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(h: HistoryEntity)

    @Query("DELETE FROM history WHERE playlistId = :pid")
    suspend fun clearHistory(pid: Long)

    @Query("DELETE FROM history WHERE playlistId = :pid AND kind = :kind AND itemId = :itemId")
    suspend fun removeHistory(pid: Long, kind: ContentKind, itemId: String)
}

@Dao
interface VpnServerDao {
    @Query("SELECT * FROM vpn_servers ORDER BY countryCode, name")
    fun observeAll(): Flow<List<VpnServerEntity>>

    @Query("SELECT * FROM vpn_servers ORDER BY countryCode, name")
    suspend fun all(): List<VpnServerEntity>

    @Query("SELECT * FROM vpn_servers WHERE id = :id")
    suspend fun byId(id: Long): VpnServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: VpnServerEntity): Long

    @Query("SELECT * FROM vpn_servers WHERE name = :name")
    suspend fun byName(name: String): VpnServerEntity?

    @Query("UPDATE vpn_servers SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE vpn_servers SET lastConnectedAt = :ts WHERE id = :id")
    suspend fun markConnected(id: Long, ts: Long)

    @Query("DELETE FROM vpn_servers WHERE id = :id")
    suspend fun delete(id: Long)
}
