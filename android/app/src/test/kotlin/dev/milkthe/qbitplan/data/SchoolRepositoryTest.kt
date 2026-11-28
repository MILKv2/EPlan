package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.model.VersionPin
import dev.milkthe.qbitplan.data.local.CachedEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchoolRepositoryTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = MutableClock(Instant.parse("2026-11-23T09:00:00Z"), zone) // Monday
    private val api = FakeEdupageApi()
    private val cache = InMemoryResponseCache()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repo = SchoolRepository(api, cache, clock, scope)

    @AfterTest
    fun tearDown() = scope.cancel()

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

    @Test
    fun `the version in force on the plan's day is downloaded, not default_num`() = runTest {
        api.versionsBody = fixture("ttviewer_2026-11-28.json") // default_num 190, from 2026-11-28; 189 still visible
        repo.refresh(substitutionDates = emptyList(), versionDate = LocalDate.of(2026, 11, 27))
        repo.refresh(substitutionDates = emptyList(), versionDate = LocalDate.of(2026, 11, 30))
        assertEquals(listOf("189", "190"), api.timetableCalls)

        val versions = assertNotNull(repo.versions.first())
        assertEquals(2026, versions.schoolYear)
        assertEquals("190", versions.versions.defaultTtNum)
    }

    @Test
    fun `a pinned version wins until a newer one is published`() = runTest {
        api.versionsBody = fixture("ttviewer_2026-11-28.json")
        val monday = LocalDate.of(2026, 11, 30)
        repo.refresh(substitutionDates = emptyList(), versionDate = monday, pin = VersionPin("188", newestAtPin = "190"))
        repo.refresh(substitutionDates = emptyList(), versionDate = monday, pin = VersionPin("188", newestAtPin = "189"))
        assertEquals(listOf("188", "190"), api.timetableCalls)
    }
}
