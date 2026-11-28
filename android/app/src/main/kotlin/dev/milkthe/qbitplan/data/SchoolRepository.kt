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
import dev.milkthe.qbitplan.core.model.VersionPin
import dev.milkthe.qbitplan.core.model.choose
import dev.milkthe.qbitplan.core.schedule.nearestSchoolDay
import dev.milkthe.qbitplan.core.schedule.nextSchoolDay
import dev.milkthe.qbitplan.data.local.CachedEntry
import dev.milkthe.qbitplan.data.local.ResponseCache
import dev.milkthe.qbitplan.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class Cached<T>(val value: T, val fetchedAt: Instant)

/** The version list and the school year it was read for. */
data class SchoolVersions(val versions: TimetableVersions, val schoolYear: Int)

sealed interface RefreshResult {
    data object Success : RefreshResult
    data class Failure(val error: Throwable) : RefreshResult
}

/**
 * Offline-first: the UI always reads from the cache and [refresh] only updates it,
 * so without network the last data stays visible and [RefreshResult.Failure] drives the banner.
 */
@Singleton
class SchoolRepository @Inject constructor(
    private val api: EdupageApi,
    private val cache: ResponseCache,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val refreshMutex = Mutex()

    // Parsed once and kept for the process lifetime: re-parsing ~680 KB per collector
    // is noticeable on low-end phones.
    val timetable: Flow<Cached<Timetable>?> = cache.observe(KEY_TIMETABLE)
        .distinctUntilChanged { old, new -> old?.fetchedAt == new?.fetchedAt }
        .map { entry -> entry?.parseOrNull { RegularTimetableParser.parse(it.body, it.version.orEmpty()) } }
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.Lazily, replay = 1)

    private val substitutionFlows = ConcurrentHashMap<LocalDate, Flow<Cached<DaySubstitutions>?>>()

    fun substitutions(date: LocalDate): Flow<Cached<DaySubstitutions>?> = substitutionFlows.getOrPut(date) {
        cache.observe(substitutionsKey(date))
            .distinctUntilChanged { old, new -> old?.fetchedAt == new?.fetchedAt }
            .map { entry -> entry?.parseOrNull { SubstitutionsParser.parse(date, it.body) } }
            .flowOn(Dispatchers.Default)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    /** The cached day as stored right now; [substitutions] may still replay an older value just after a refresh. */
    suspend fun cachedSubstitutions(date: LocalDate): Cached<DaySubstitutions>? = withContext(Dispatchers.Default) {
        cache.get(substitutionsKey(date))?.parseOrNull { SubstitutionsParser.parse(date, it.body) }
    }

    /** Versions are fetched on every refresh, so this is the last successful refresh. */
    val lastRefresh: Flow<Instant?> = cache.observe(KEY_VERSIONS).map { it?.fetchedAt }

    val versions: Flow<SchoolVersions?> = cache.observe(KEY_VERSIONS)
        .distinctUntilChanged { old, new -> old?.fetchedAt == new?.fetchedAt }
        .map { entry ->
            entry?.parseOrNull {
                // Older caches stored default_num here, not the year.
                val year = it.version?.toIntOrNull()?.takeIf { y -> y in 2000..2999 } ?: schoolYearOf(LocalDate.now(clock))
                SchoolVersions(TimetableVersionsParser.parse(it.body), year)
            }?.value
        }
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * New versions are published every few weeks, so the version list is fetched every time;
     * the timetable only when the chosen version changed or the cached copy is stale.
     * [versionDate]: the day the plan opens on, which picks the version in force (see [choose]).
     */
    suspend fun refresh(
        substitutionDates: List<LocalDate> = defaultSubstitutionDates(),
        versionDate: LocalDate = nearestSchoolDay(LocalDate.now(clock)),
        pin: VersionPin? = null,
    ): RefreshResult =
        refreshMutex.withLock {
            runRefresh {
                val ttNum = fetchVersions(versionDate, pin)
                    ?: throw EdupageException("No timetable published for this school year")

                val cached = cache.get(KEY_TIMETABLE)
                if (cached == null || cached.version != ttNum || cached.isOlderThan(TIMETABLE_MAX_AGE)) {
                    val raw = api.fetchRegularTimetable(ttNum)
                    RegularTimetableParser.parse(raw, ttNum) // validate before overwriting the cache
                    cache.put(CachedEntry(KEY_TIMETABLE, raw, ttNum, clock.instant()))
                }

                substitutionDates.forEach { fetchSubstitutions(it) }

                val oldest = LocalDate.now(clock).minusDays(SUBSTITUTIONS_KEEP_DAYS)
                cache.deleteWithPrefixBelow(SUBSTITUTIONS_PREFIX, substitutionsKey(oldest))
            }
        }

    suspend fun refreshSubstitutions(date: LocalDate): RefreshResult = runRefresh { fetchSubstitutions(date) }

    /** Returns the version to download. */
    private suspend fun fetchVersions(versionDate: LocalDate, pin: VersionPin?): String? {
        var year = schoolYearOf(LocalDate.now(clock))
        var raw = api.fetchTimetableVersions(year)
        var choice = TimetableVersionsParser.parse(raw).choose(versionDate, year, pin)
        if (choice == null) {
            // Early in the school year the new year may have no timetable yet.
            year -= 1
            raw = api.fetchTimetableVersions(year)
            choice = TimetableVersionsParser.parse(raw).choose(versionDate, year, pin)
        }
        cache.put(CachedEntry(KEY_VERSIONS, raw, year.toString(), clock.instant()))
        return choice?.ttNum
    }

    private suspend fun fetchSubstitutions(date: LocalDate) {
        val raw = api.fetchSubstitutions(date)
        SubstitutionsParser.parse(date, raw) // validate before overwriting the cache
        cache.put(CachedEntry(substitutionsKey(date), raw, null, clock.instant()))
    }

    private inline fun runRefresh(block: () -> Unit): RefreshResult = try {
        block()
        RefreshResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RefreshResult.Failure(e)
    }

    /** Today and the next school day. */
    fun defaultSubstitutionDates(): List<LocalDate> {
        val today = LocalDate.now(clock)
        return listOf(today, nextSchoolDay(today))
    }

    private fun CachedEntry.isOlderThan(age: Duration) = fetchedAt.plus(age).isBefore(clock.instant())

    // A cache entry the parser no longer accepts is treated as missing.
    private inline fun <T> CachedEntry.parseOrNull(parse: (CachedEntry) -> T): Cached<T>? =
        runCatching { Cached(parse(this), fetchedAt) }.getOrNull()

    companion object {
        const val KEY_VERSIONS = "versions"
        const val KEY_TIMETABLE = "timetable"
        const val SUBSTITUTIONS_PREFIX = "subst:"

        /** ISO date in the key, so string comparison = date comparison (used for pruning). */
        fun substitutionsKey(date: LocalDate) = "$SUBSTITUTIONS_PREFIX$date"

        /** Re-download even without a version change: the school may edit a version in place. */
        val TIMETABLE_MAX_AGE: Duration = Duration.ofHours(12)
        const val SUBSTITUTIONS_KEEP_DAYS = 7L
    }
}
