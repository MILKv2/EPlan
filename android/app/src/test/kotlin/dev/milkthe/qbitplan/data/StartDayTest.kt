package dev.milkthe.qbitplan.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.substitutions.SubstitutionsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StartDayTest {

    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Warsaw")
    private val tuesday = LocalDate.of(2026, 11, 24)
    private val clock = MutableClock(tuesday.atTime(9, 5).atZone(zone).toInstant(), zone)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = SchoolRepository(FakeEdupageApi(), InMemoryResponseCache(), clock, scope)
    private lateinit var prefs: UserPreferencesRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        repo.refresh(listOf(tuesday))
        // 1at, 2. Grupa: last lesson that takes place on 2026-11-24 ends at 14:40 (period 9 is cancelled).
        prefs.setStudent("-210", "1at", GroupSelection(setOf("2. Grupa")))
        Unit
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun at(hour: Int, minute: Int) {
        clock.now = LocalDateTime.of(tuesday, LocalTime.of(hour, minute)).atZone(zone).toInstant()
    }

    private fun startDay() = runBlocking { StartDay(repo, prefs, clock)() }

    @Test
    fun `today until the last lesson is over`() {
        at(14, 39)
        assertEquals(tuesday, startDay())
    }

    @Test
    fun `next school day once the last lesson is over`() {
        at(14, 40)
        assertEquals(tuesday.plusDays(1), startDay())
    }

    @Test
    fun `substitutions open on tomorrow after the last lesson`() {
        at(16, 0)
        val start = StartDay(repo, prefs, clock)
        val vm = SubstitutionsViewModel(repo, prefs, SyncManager(repo, prefs, start, clock, scope), PlanDayTracker(start, repo, prefs, clock, scope), clock)
        val selected = runBlocking { withTimeout(15_000) { vm.selectedDate.filterNotNull().first() } }
        assertEquals(tuesday.plusDays(1), selected)
        assertEquals(1, vm.quickDays.value.days.indexOf(selected))
    }
}
