package app.lumentv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlaylistType { XTREAM, M3U }

/** Tipo de contenido de una categoría, favorito o entrada de historial. */
enum class ContentKind { LIVE, VOD, SERIES, EPISODE }

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    val serverUrl: String = "",
    val username: String = "",
    /** Contraseña cifrada con [app.lumentv.core.crypto.SecretBox]. */
    val passwordEnc: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val serverTimezone: String = "",
    val expiresAt: Long = 0,
    val maxConnections: Int = 0,
    val status: String = "",
    val lastSyncAt: Long = 0,
    val lastEpgSyncAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories", primaryKeys = ["playlistId", "kind", "categoryId"])
data class CategoryEntity(
    val playlistId: Long,
    val kind: ContentKind,
    val categoryId: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "channels",
    primaryKeys = ["playlistId", "streamId"],
    indices = [Index("playlistId", "categoryId"), Index("playlistId", "name")],
)
data class ChannelEntity(
    val playlistId: Long,
    val streamId: String,
    val num: Int,
    val name: String,
    val logo: String,
    val epgChannelId: String,
    val categoryId: String,
    val tvArchive: Boolean,
    val archiveDays: Int,
    /** URL completa (solo listas M3U); en Xtream se construye a partir de las credenciales. */
    val directUrl: String = "",
    val sortOrder: Int,
)

@Entity(
    tableName = "movies",
    primaryKeys = ["playlistId", "streamId"],
    indices = [Index("playlistId", "categoryId"), Index("playlistId", "name")],
)
data class MovieEntity(
    val playlistId: Long,
    val streamId: String,
    val name: String,
    val poster: String,
    val rating: String,
    val categoryId: String,
    val containerExt: String,
    val addedAt: Long,
    val directUrl: String = "",
    val sortOrder: Int,
)

@Entity(
    tableName = "series",
    primaryKeys = ["playlistId", "seriesId"],
    indices = [Index("playlistId", "categoryId"), Index("playlistId", "name")],
)
data class SeriesEntity(
    val playlistId: Long,
    val seriesId: String,
    val name: String,
    val cover: String,
    val plot: String,
    val genre: String,
    val releaseDate: String,
    val rating: String,
    val backdrop: String,
    val categoryId: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "programs",
    indices = [Index("playlistId", "channelKey", "startAt"), Index("stopAt")],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** `epg_channel_id` en minúsculas: une programas y canales. */
    val channelKey: String,
    val startAt: Long,
    val stopAt: Long,
    val title: String,
    val description: String,
)

@Entity(tableName = "favorites", primaryKeys = ["playlistId", "kind", "itemId"])
data class FavoriteEntity(
    val playlistId: Long,
    val kind: ContentKind,
    val itemId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history", primaryKeys = ["playlistId", "kind", "itemId"], indices = [Index("watchedAt")])
data class HistoryEntity(
    val playlistId: Long,
    val kind: ContentKind,
    val itemId: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Datos extra para reanudar (p. ej. extensión del contenedor o id de la serie). */
    val extra: String = "",
    val watchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "vpn_servers", indices = [Index(value = ["name"], unique = true)])
data class VpnServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nombre Proton, p. ej. `NL-FREE#12`. */
    val name: String,
    val countryCode: String,
    val endpoint: String,
    val isFree: Boolean,
    /** Contenido completo del `.conf` cifrado con SecretBox (incluye la clave privada). */
    val configEnc: String,
    val source: String,
    val favorite: Boolean = false,
    val lastConnectedAt: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
)
