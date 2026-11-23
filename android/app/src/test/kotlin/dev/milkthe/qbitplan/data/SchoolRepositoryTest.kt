package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.edupage.EdupageApi
import dev.milkthe.qbitplan.data.local.CachedEntry
import dev.milkthe.qbitplan.data.local.ResponseCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchoolRepositoryTest {

    private val fixtures = File(System.getProperty("fixtures.dir")!!)
    private fun fixture(path: String) = File(fixtures, path).readText()

    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = MutableClock(Instant.parse("2026-11-23T09:00:00Z"), zone) // Monday
    private val api = FakeApi()
    private val cache = InMemoryCache()
    private val repo = SchoolRepository(api, cache, clock)

    @Test
    fun `refresh downloads versions, timetable and substitutions`() = runTest {
        val result = repo.refresh()

        assertEquals(RefreshResult.Success, result)
        assertEquals(listOf(2026), api.versionCalls)
        assertEquals(listOf("189"), api.timetableCalls)
        assertEquals(listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 24)), api.substitutionCalls)

        val tt = assertNotNull(repo.timetable.first())
        assertEquals("189", tt.value.ttNum)
        assertEquals(clock.instant(), tt.fetchedAt)
        val subst = assertNotNull(repo.substitutions(LocalDate.of(2026, 11, 24)).first())
        assertEquals(55, subst.value.substitutions.size)
        assertEquals(clock.instant(), repo.lastRefresh.first())
    }

    @Test
    fun `timetable is not downloaded again while fresh and same version`() = runTest {
        repo.refresh()
        clock.advance(Duration.ofHours(1))
        repo.refresh()
        assertEquals(listOf("189"), api.timetableCalls)
        assertEquals(2, api.versionCalls.size, "versions fetched on every refresh")
    }

    @Test
    fun `timetable is downloaded again on new version or when stale`() = runTest {
        repo.refresh()
        api.defaultNum = "190"
        repo.refresh()
        assertEquals(listOf("189", "190"), api.timetableCalls)

        clock.advance(SchoolRepository.TIMETABLE_MAX_AGE.plusMinutes(1))
        repo.refresh()
        assertEquals(listOf("189", "190", "190"), api.timetableCalls)
    }

    @Test
    fun `offline keeps last data and reports failure`() = runTest {
        repo.refresh()
        api.offline = true
        clock.advance(Duration.ofHours(1))

        val result = repo.refresh()

        assertIs<RefreshResult.Failure>(result)
        assertIs<IOException>(result.error)
        assertEquals("189", repo.timetable.first()?.value?.ttNum)
        assertEquals(Instant.parse("2026-11-23T09:00:00Z"), repo.lastRefresh.first())
    }

    @Test
    fun `invalid response does not overwrite cache`() = runTest {
        repo.refresh()
        api.defaultNum = "190"
        api.brokenTimetable = true

        assertIs<RefreshResult.Failure>(repo.refresh())
        assertEquals("189", repo.timetable.first()?.value?.ttNum)
    }

    @Test
    fun `falls back to previous school year when current has no timetable`() = runTest {
        api.emptyYears += 2026
        repo.refresh()
        assertEquals(listOf(2026, 2025), api.versionCalls)
        assertEquals(listOf("189"), api.timetableCalls)
    }

    @Test
    fun `empty cache emits null`() = runTest {
        assertNull(repo.timetable.first())
        assertNull(repo.substitutions(LocalDate.of(2026, 11, 24)).first())
        assertNull(repo.lastRefresh.first())
    }

    @Test
    fun `old substitutions are pruned`() = runTest {
        cache.put(CachedEntry(SchoolRepository.substitutionsKey(LocalDate.of(2026, 11, 1)), "{}", null, clock.instant()))
        cache.put(CachedEntry(SchoolRepository.substitutionsKey(LocalDate.of(2026, 11, 20)), "{}", null, clock.instant()))
        repo.refresh()
        assertNull(cache.get(SchoolRepository.substitutionsKey(LocalDate.of(2026, 11, 1))))
        assertNotNull(cache.get(SchoolRepository.substitutionsKey(LocalDate.of(2026, 11, 20))))
    }

    @Test
    fun `default dates skip the weekend`() {
        clock.now = Instant.parse("2026-11-27T09:00:00Z") // Friday
        assertEquals(listOf(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 30)), repo.defaultSubstitutionDates())
        clock.now = Instant.parse("2026-11-28T09:00:00Z") // Saturday
        assertEquals(listOf(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 30)), repo.defaultSubstitutionDates())
    }

    private inner class FakeApi : EdupageApi {
        val versionCalls = mutableListOf<Int>()
        val timetableCalls = mutableListOf<String>()
        val substitutionCalls = mutableListOf<LocalDate>()
        var defaultNum = "189"
        var offline = false
        var brokenTimetable = false
        val emptyYears = mutableSetOf<Int>()

        override suspend fun fetchTimetableVersions(schoolYear: Int): String {
            if (offline) throw IOException("brak sieci")
            versionCalls += schoolYear
            val num = if (schoolYear in emptyYears) "" else defaultNum
            return """{"r":{"regular":{"default_num":"$num","timetables":[]}}}"""
        }

        override suspend fun fetchRegularTimetable(ttNum: String): String {
            if (offline) throw IOException("brak sieci")
            timetableCalls += ttNum
            return if (brokenTimetable) """{"r":{"oops":true}}""" else fixture("regulartt_189.json")
        }

        override suspend fun fetchSubstitutions(date: LocalDate): String {
            if (offline) throw IOException("brak sieci")
            substitutionCalls += date
            val file = File(fixtures, "substitutions/$date.json")
            return if (file.exists()) file.readText() else fixture("substitutions/2026-11-21.json")
        }
    }

    private class InMemoryCache : ResponseCache {
        private val entries = MutableStateFlow<Map<String, CachedEntry>>(emptyMap())
        override fun observe(key: String): Flow<CachedEntry?> = entries.map { it[key] }
        override suspend fun get(key: String) = entries.value[key]
        override suspend fun put(entry: CachedEntry) = entries.update { it + (entry.key to entry) }
        override suspend fun deleteWithPrefixBelow(prefix: String, keyBelow: String) =
            entries.update { map -> map.filterKeys { !(it.startsWith(prefix) && it < keyBelow) } }
    }

    private class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
        fun advance(d: Duration) { now = now.plus(d) }
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    }
}
