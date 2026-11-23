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
 * Wiersz tabeli cache. Treść jest skompresowana gzipem (plan stały ma
 * ~680 KB JSON-a, po kompresji ~60 KB); duże teksty w jednym wierszu SQLite
 * mogą przekroczyć limit okna kursora na Androidzie (2 MB).
 */
@Entity(tableName = "cached_responses")
class CachedResponseEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val gzippedBody: ByteArray,
    val version: String?,
    /** Milisekundy od 1970-01-01 UTC. */
    val fetchedAt: Long,
)

/** DAO = interfejs zapytań; Room generuje implementację (przez KSP). */
@Dao
interface CachedResponseDao {
    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    fun observe(key: String): Flow<CachedResponseEntity?>

    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    suspend fun get(key: String): CachedResponseEntity?

    @Upsert // wstaw albo zastąp
    suspend fun upsert(entity: CachedResponseEntity)

    @Query("DELETE FROM cached_responses WHERE `key` LIKE :prefix || '%' AND `key` < :keyBelow")
    suspend fun deleteWithPrefixBelow(prefix: String, keyBelow: String)
}

@Database(entities = [CachedResponseEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedResponseDao(): CachedResponseDao
}
