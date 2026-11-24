package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.update.AppVersion
import dev.milkthe.qbitplan.core.update.ReleaseResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateRepositoryTest {

    private val clock = MutableClock(Instant.parse("2026-12-02T07:00:00Z"), ZoneId.of("Europe/Warsaw"))
    private val api = FakeReleaseApi()
    private val cache = InMemoryResponseCache()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val release102 = fixture("github/latest_release.json") // v1.0.2

    private fun repo(installed: AppVersion = AppVersion(1, 0, 1)) = UpdateRepository(api, cache, installed, clock, scope)

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun `newer release is offered with its apk`() = runTest {
        api.next = { ReleaseResponse.Fresh(release102, "W/\"e1\"") }
        val repo = repo()

        assertIs<UpdateStatus.NeverChecked>(repo.status.first())
        assertTrue(repo.check())

        val release = assertNotNull(repo.available.first { it != null })
        assertEquals(AppVersion(1, 0, 2), release.version)
        assertEquals("https://github.com/MILKv2/QbitPlan/releases/download/v1.0.2/E-Plan-v1.0.2.apk", release.apkUrl)
        assertIs<UpdateStatus.Available>(repo.status.first())
    }

    @Test
    fun `same or older release is not offered`() = runTest {
        api.next = { ReleaseResponse.Fresh(release102, null) }

        for (installed in listOf(AppVersion(1, 0, 2), AppVersion(1, 1, 0))) {
            val repo = repo(installed)
            repo.check()
            assertNull(repo.available.value)
            assertIs<UpdateStatus.UpToDate>(repo.status.first())
        }
    }

    @Test
    fun `no release yet counts as up to date`() = runTest {
        api.next = { ReleaseResponse.NoRelease }
        val repo = repo()

        assertTrue(repo.check())
        assertIs<UpdateStatus.UpToDate>(repo.status.first())
    }

    @Test
    fun `sends the cached etag and keeps the body on 304`() = runTest {
        val repo = repo()
        api.next = { ReleaseResponse.Fresh(release102, "W/\"e1\"") }
        repo.check()

        clock.advance(Duration.ofHours(13))
        api.next = { ReleaseResponse.NotModified }
        assertTrue(repo.check())

        assertEquals(listOf(null, "W/\"e1\""), api.etags)
        assertEquals(clock.instant(), cache.get(UpdateRepository.KEY)!!.fetchedAt)
        assertEquals(AppVersion(1, 0, 2), repo.available.first { it != null }!!.version)
    }

    @Test
    fun `checkIfDue asks at most every 12 hours`() = runTest {
        val repo = repo()
        repo.checkIfDue()
        repo.checkIfDue()
        assertEquals(1, api.etags.size)

        clock.advance(Duration.ofHours(11))
        repo.checkIfDue()
        assertEquals(1, api.etags.size)

        clock.advance(Duration.ofHours(2))
        repo.checkIfDue()
        assertEquals(2, api.etags.size)
    }

    @Test
    fun `failure keeps the last answer and backs off for an hour`() = runTest {
        val repo = repo()
        api.next = { ReleaseResponse.Fresh(release102, null) }
        repo.check()

        clock.advance(Duration.ofHours(13))
        api.next = { throw IOException("offline") }
        repo.checkIfDue()
        assertEquals(2, api.etags.size)
        assertNotNull(repo.available.first { it != null }, "still offered from cache")

        clock.advance(Duration.ofMinutes(30))
        repo.checkIfDue()
        assertEquals(2, api.etags.size, "no retry within an hour")

        assertFalse(repo.check(), "manual check ignores the back-off")
        assertEquals(3, api.etags.size)
    }

    @Test
    fun `failure without any cached answer shows as failed`() = runTest {
        api.next = { throw IOException("offline") }
        val repo = repo()

        assertFalse(repo.check())
        assertIs<UpdateStatus.Failed>(repo.status.first())
    }
}
