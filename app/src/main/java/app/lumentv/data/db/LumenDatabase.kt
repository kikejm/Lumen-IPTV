package app.lumentv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        ProgramEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        VpnServerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun catalog(): CatalogDao
    abstract fun programs(): ProgramDao
    abstract fun library(): LibraryDao
    abstract fun vpnServers(): VpnServerDao

    companion object {
        fun build(context: Context): LumenDatabase =
            Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db")
                // El catálogo es una caché re-descargable: ante un cambio de esquema sin migración, se recrea.
                .fallbackToDestructiveMigration()
                .build()
    }
}

class Converters {
    @TypeConverter fun kindToString(k: ContentKind): String = k.name
    @TypeConverter fun stringToKind(s: String): ContentKind = ContentKind.valueOf(s)
    @TypeConverter fun typeToString(t: PlaylistType): String = t.name
    @TypeConverter fun stringToType(s: String): PlaylistType = PlaylistType.valueOf(s)
}
