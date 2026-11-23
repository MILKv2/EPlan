package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.edupage.EdupageApi
import dev.milkthe.qbitplan.core.edupage.EdupageException
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.core.edupage.SubstitutionsParser
import dev.milkthe.qbitplan.core.edupage.TimetableVersionsParser
import dev.milkthe.qbitplan.core.edupage.schoolYearOf
import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.model.TimetableVersions
import dev.milkthe.qbitplan.data.local.CachedEntry
import dev.milkthe.qbitplan.data.local.ResponseCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Dane z cache razem z chwilą pobrania (do banera „dane z …”). */
data class Cached<T>(val value: T, val fetchedAt: Instant)

/** Wynik odświeżania. `sealed` = zamknięty zbiór wariantów (when musi obsłużyć wszystkie). */
sealed interface RefreshResult {
    data object Success : RefreshResult
    data class Failure(val error: Throwable) : RefreshResult
}

/**
 * Jedyne źródło danych o planie i zastępstwach dla reszty aplikacji.
 *
 * Zasada offline-first: UI zawsze czyta z cache (Flow), a [refresh] tylko
 * dociąga nowe dane z EduPage i zapisuje je do cache. Brak sieci = UI dalej
 * pokazuje ostatnie dane, a wynik [RefreshResult.Failure] pozwala pokazać baner.
 */
@Singleton
class SchoolRepository @Inject constructor(
    private val api: EdupageApi,
    private val cache: ResponseCache,
    private val clock: Clock,
) {
    private val refreshMutex = Mutex() // dwa równoległe odświeżenia nie mają sensu

    /** Plan stały z cache; emituje ponownie po każdej zmianie. */
    val timetable: Flow<Cached<Timetable>?> = cache.observe(KEY_TIMETABLE)
        .distinctUntilChanged { old, new -> old?.fetchedAt == new?.fetchedAt }
        .map { entry -> entry?.parseOrNull { RegularTimetableParser.parse(it.body, it.version.orEmpty()) } }
        .flowOn(Dispatchers.Default) // parsowanie 680 KB poza wątkiem UI

    /** Zastępstwa na dzień z cache. */
    fun substitutions(date: LocalDate): Flow<Cached<DaySubstitutions>?> = cache.observe(substitutionsKey(date))
        .distinctUntilChanged { old, new -> old?.fetchedAt == new?.fetchedAt }
        .map { entry -> entry?.parseOrNull { SubstitutionsParser.parse(date, it.body) } }
        .flowOn(Dispatchers.Default)

    /** Chwila ostatniego udanego odświeżenia (lista wersji pobierana jest zawsze). */
    val lastRefresh: Flow<Instant?> = cache.observe(KEY_VERSIONS).map { it?.fetchedAt }

    /**
     * Pobiera aktualną wersję planu (przy każdym odświeżeniu, bo `default_num`
     * się zmienia), plan stały, jeśli jest nowa wersja albo stary jest
     * przeterminowany, oraz zastępstwa na [substitutionDates].
     */
    suspend fun refresh(substitutionDates: List<LocalDate> = defaultSubstitutionDates()): RefreshResult =
        refreshMutex.withLock {
            runRefresh {
                val ttNum = fetchVersions().defaultTtNum
                    ?: throw EdupageException("Szkoła nie opublikowała planu na ten rok")

                val cached = cache.get(KEY_TIMETABLE)
                if (cached == null || cached.version != ttNum || cached.isOlderThan(TIMETABLE_MAX_AGE)) {
                    val raw = api.fetchRegularTimetable(ttNum)
                    RegularTimetableParser.parse(raw, ttNum) // walidacja: zła odpowiedź nie nadpisze cache
                    cache.put(CachedEntry(KEY_TIMETABLE, raw, ttNum, clock.instant()))
                }

                substitutionDates.forEach { fetchSubstitutions(it) }

                val oldest = LocalDate.now(clock).minusDays(SUBSTITUTIONS_KEEP_DAYS)
                cache.deleteWithPrefixBelow(SUBSTITUTIONS_PREFIX, substitutionsKey(oldest))
            }
        }

    /** Odświeża zastępstwa tylko na jeden dzień (np. po wyborze dnia w zakładce Zastępstwa). */
    suspend fun refreshSubstitutions(date: LocalDate): RefreshResult = runRefresh { fetchSubstitutions(date) }

    private suspend fun fetchVersions(): TimetableVersions {
        val year = schoolYearOf(LocalDate.now(clock))
        var raw = api.fetchTimetableVersions(year)
        var versions = TimetableVersionsParser.parse(raw)
        if (versions.defaultTtNum == null) {
            // Początek roku szkolnego: nowy rok może nie mieć jeszcze planu.
            raw = api.fetchTimetableVersions(year - 1)
            versions = TimetableVersionsParser.parse(raw)
        }
        cache.put(CachedEntry(KEY_VERSIONS, raw, versions.defaultTtNum, clock.instant()))
        return versions
    }

    private suspend fun fetchSubstitutions(date: LocalDate) {
        val raw = api.fetchSubstitutions(date)
        SubstitutionsParser.parse(date, raw) // walidacja
        cache.put(CachedEntry(substitutionsKey(date), raw, null, clock.instant()))
    }

    private inline fun runRefresh(block: () -> Unit): RefreshResult = try {
        block()
        RefreshResult.Success
    } catch (e: CancellationException) {
        throw e // anulowanie korutyny musi przejść dalej
    } catch (e: Exception) {
        RefreshResult.Failure(e)
    }

    /** Dziś i najbliższy dzień nauki (w piątek/weekend: poniedziałek). */
    fun defaultSubstitutionDates(): List<LocalDate> {
        val today = LocalDate.now(clock)
        var next = today.plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) next = next.plusDays(1)
        return listOf(today, next)
    }

    private fun CachedEntry.isOlderThan(age: Duration) = fetchedAt.plus(age).isBefore(clock.instant())

    // Uszkodzony wpis w cache (np. po zmianie formatu) traktujemy jak brak danych.
    private inline fun <T> CachedEntry.parseOrNull(parse: (CachedEntry) -> T): Cached<T>? =
        runCatching { Cached(parse(this), fetchedAt) }.getOrNull()

    companion object {
        const val KEY_VERSIONS = "versions"
        const val KEY_TIMETABLE = "timetable"
        const val SUBSTITUTIONS_PREFIX = "subst:"

        /** Klucz z datą ISO, więc porównanie tekstowe = porównanie dat. */
        fun substitutionsKey(date: LocalDate) = "$SUBSTITUTIONS_PREFIX$date"

        /** Nawet bez zmiany wersji pobieramy plan ponownie co tyle (szkoła może poprawić wersję). */
        val TIMETABLE_MAX_AGE: Duration = Duration.ofHours(12)
        const val SUBSTITUTIONS_KEEP_DAYS = 7L
    }
}
