package dev.milkthe.qbitplan.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Bodies are gzipped: the regular timetable is ~680 KB of JSON (~60 KB compressed)
 * and large rows can exceed Android's 2 MB CursorWindow.
 */
@Entity(tableName = "cached_responses")
class CachedResponseEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val gzippedBody: ByteArray,
    val version: String?,
    /** Epoch millis. */
    val fetchedAt: Long,
)

@Dao
interface CachedResponseDao {
    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    fun observe(key: String): Flow<CachedResponseEntity?>

    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    suspend fun get(key: String): CachedResponseEntity?

    @Upsert
    suspend fun upsert(entity: CachedResponseEntity)

    @Query("DELETE FROM cached_responses WHERE `key` LIKE :prefix || '%' AND `key` < :keyBelow")
    suspend fun deleteWithPrefixBelow(prefix: String, keyBelow: String)
}

@Database(entities = [CachedResponseEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedResponseDao(): CachedResponseDao
}
