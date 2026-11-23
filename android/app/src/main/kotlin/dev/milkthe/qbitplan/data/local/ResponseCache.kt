package dev.milkthe.qbitplan.data.local

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Cache surowych odpowiedzi EduPage. Trzymamy odpowiedzi „jak z serwera”
 * i parsujemy je przy odczycie, dzięki czemu:
 * - poprawka w parserze działa od razu na danych z cache (bez migracji bazy),
 * - nie potrzebujemy tabel odwzorowujących cały plan.
 */
interface ResponseCache {
    /** Emituje wpis przy każdej zmianie (null = brak). */
    fun observe(key: String): Flow<CachedEntry?>
    suspend fun get(key: String): CachedEntry?
    suspend fun put(entry: CachedEntry)
    /** Usuwa wpisy, których klucz zaczyna się od [prefix] i jest mniejszy niż [keyBelow]. */
    suspend fun deleteWithPrefixBelow(prefix: String, keyBelow: String)
}

/**
 * Jeden wpis cache. [version] to dodatkowa informacja, np. numer wersji
 * planu (tt_num), z której pochodzi odpowiedź.
 */
data class CachedEntry(
    val key: String,
    val body: String,
    val version: String?,
    val fetchedAt: Instant,
)
