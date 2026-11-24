package dev.milkthe.qbitplan.push

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.data.FakeEdupageApi
import dev.milkthe.qbitplan.data.InMemoryResponseCache
import dev.milkthe.qbitplan.data.MutableClock
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.common.LessonItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import kotlin.test.assertTrue

class ChangeNotifierTest {

    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 23, 18, 0).atZone(zone).toInstant(), zone) // Monday evening
    private val tuesday = LocalDate.of(2026, 11, 24)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api = FakeEdupageApi()
    private val repo = SchoolRepository(api, InMemoryResponseCache(), clock, scope)
    private val alerts = RecordingAlerts()
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var notifier: ChangeNotifier

    private class RecordingAlerts : ChangeAlerts {
        val shown = mutableListOf<Pair<LocalDate, List<LessonItem>>>()
        val unspecified = mutableListOf<LocalDate>()
        override fun show(date: LocalDate, lessons: List<LessonItem>) { shown += date to lessons }
        override fun showUnspecified(date: LocalDate) { unspecified += date }
    }

    @Before
    fun setUp() = runBlocking {
        prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        repo.refresh(substitutionDates = emptyList()) // timetable only; the day isn't cached yet
        prefs.setNotificationsEnabled(true)
        notifier = ChangeNotifier(repo, prefs, alerts, clock)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a student hears about their groups' changes once`() = runBlocking {
        prefs.setStudent("-210", "1at", setOf("2. Grupa"))
        notifier.onDayChanged(tuesday)
        val (date, lessons) = alerts.shown.single()
        assertEquals(tuesday, date)
        assertEquals(listOf(4, 5, 9), lessons.map { it.periods.first })
        assertEquals(LessonStatus.CANCELLED, lessons.last().status)

        // The day is cached now; the same push again says nothing.
        notifier.onDayChanged(tuesday)
        assertEquals(1, alerts.shown.size)
    }

    @Test
    fun `a teacher hears about the lesson they cover`() = runBlocking {
        prefs.setTeacher("-161", "AG", "Anna Grzebień")
        notifier.onDayChanged(tuesday)
        assertTrue(alerts.shown.single().second.any { it.isCovering && it.periods.first == 4 })
    }

    @Test
    fun `offline gives a plain notice instead of details`() = runBlocking {
        prefs.setStudent("-210", "1at", emptySet())
        api.offline = true
        notifier.onDayChanged(tuesday)
        assertEquals(listOf(tuesday), alerts.unspecified)
        assertTrue(alerts.shown.isEmpty())
    }

    @Test
    fun `quiet weekend and switched-off notifications stay silent`() = runBlocking {
        prefs.setStudent("-210", "1at", emptySet())
        clock.now = LocalDateTime.of(2026, 11, 22, 18, 0).atZone(zone).toInstant() // Sunday
        notifier.onDayChanged(tuesday)
        clock.now = LocalDateTime.of(2026, 11, 23, 18, 0).atZone(zone).toInstant()
        prefs.setNotificationsEnabled(false)
        notifier.onDayChanged(tuesday)
        assertTrue(alerts.shown.isEmpty() && alerts.unspecified.isEmpty())
    }
}
