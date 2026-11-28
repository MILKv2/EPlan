package dev.milkthe.qbitplan.push

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.core.model.VersionPin
import dev.milkthe.qbitplan.data.FakeEdupageApi
import dev.milkthe.qbitplan.data.InMemoryResponseCache
import dev.milkthe.qbitplan.data.MutableClock
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.StartDay
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.fixture
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimetableNotifierTest {

    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Warsaw")
    // Friday after lessons: the plan opens on Monday 30.11, when version 190 applies.
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 27, 18, 0).atZone(zone).toInstant(), zone)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api = FakeEdupageApi().apply { versionsBody = fixture("ttviewer_2026-11-28.json") }
    private val repo = SchoolRepository(api, InMemoryResponseCache(), clock, scope)
    private val alerts = RecordingAlerts()
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var sync: SyncManager

    private class RecordingAlerts : TimetableAlerts {
        val shown = mutableListOf<Pair<LocalDate, Boolean>>()
        override fun showNewTimetable(firstDay: LocalDate, downloaded: Boolean) { shown += firstDay to downloaded }
    }

    @Before
    fun setUp() = runBlocking {
        prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        prefs.setStudent("-210", "1at", GroupSelection(emptySet()))
        prefs.setNotificationsEnabled(true)
        sync = SyncManager(repo, prefs, StartDay(repo, prefs, clock), clock, scope)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun notifier() = TimetableNotifier(sync, prefs, alerts, clock)

    @Test
    fun `downloads the new version and names its first school day`() = runBlocking {
        notifier().onNewTimetable(LocalDate.of(2026, 11, 28)) // a Saturday
        assertEquals(listOf(LocalDate.of(2026, 11, 30) to true), alerts.shown)
        // First pick without a timetable is for today (189); with it the plan opens on Monday (190).
        assertEquals(listOf("189", "190"), api.timetableCalls)
        assertEquals("190", repo.currentTimetable()?.ttNum)
    }

    @Test
    fun `offline still announces it`() = runBlocking {
        api.offline = true
        notifier().onNewTimetable(LocalDate.of(2026, 11, 28))
        assertEquals(listOf(LocalDate.of(2026, 11, 30) to false), alerts.shown)
    }

    @Test
    fun `silent with notifications off or on a quiet weekend`() = runBlocking {
        prefs.setNotificationsEnabled(false)
        notifier().onNewTimetable(LocalDate.of(2026, 11, 28))
        prefs.setNotificationsEnabled(true)
        clock.now = LocalDateTime.of(2026, 11, 28, 9, 0).atZone(zone).toInstant()
        notifier().onNewTimetable(LocalDate.of(2026, 11, 28))
        assertEquals(emptyList(), alerts.shown)
        assertEquals("190", repo.currentTimetable()?.ttNum, "downloaded all the same")
    }

    @Test
    fun `a pin that a newer version made stale is dropped after the refresh`() = runBlocking {
        prefs.setVersionPin(VersionPin("188", newestAtPin = "189"))
        sync.refreshNow()
        assertNull(prefs.preferences.first().versionPin)
        assertEquals("190", repo.currentTimetable()?.ttNum)
    }
}
