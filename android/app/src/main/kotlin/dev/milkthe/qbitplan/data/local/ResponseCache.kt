package dev.milkthe.qbitplan.data.local

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Cache of raw EduPage responses, parsed on read: a parser fix applies to cached
 * data immediately and there is no schema mirroring the timetable.
 */
interface ResponseCache {
    fun observe(key: String): Flow<CachedEntry?>
    suspend fun get(key: String): CachedEntry?
    suspend fun put(entry: CachedEntry)
    /** Deletes keys starting with [prefix] that sort below [keyBelow]. */
    suspend fun deleteWithPrefixBelow(prefix: String, keyBelow: String)
}

/** [version] is extra info, e.g. the tt_num the response belongs to. */
data class CachedEntry(
    val key: String,
    val body: String,
    val version: String?,
    val fetchedAt: Instant,
)
