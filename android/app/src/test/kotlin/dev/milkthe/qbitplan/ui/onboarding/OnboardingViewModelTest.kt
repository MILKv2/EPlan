package dev.milkthe.qbitplan.ui.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.milkthe.qbitplan.data.FakeEdupageApi
import dev.milkthe.qbitplan.data.InMemoryResponseCache
import dev.milkthe.qbitplan.data.MutableClock
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.onboarding.OnboardingStep.CLASS
import dev.milkthe.qbitplan.ui.onboarding.OnboardingStep.GROUPS
import dev.milkthe.qbitplan.ui.onboarding.OnboardingStep.NAME
import dev.milkthe.qbitplan.ui.onboarding.OnboardingStep.NOTIFICATIONS
import dev.milkthe.qbitplan.ui.onboarding.OnboardingStep.ROLE
import dev.milkthe.qbitplan.ui.onboarding.OnboardingStep.TEACHER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 24, 9, 5).atZone(zone).toInstant(), zone)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = SchoolRepository(FakeEdupageApi(), InMemoryResponseCache(), clock, scope)
    private val sync = SyncManager(repo, clock, scope)
    private lateinit var prefs: UserPreferencesRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        repo.refresh(listOf(LocalDate.of(2026, 11, 24)))
        Unit
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun vm() = OnboardingViewModel(repo, prefs, sync, clock)

    private fun OnboardingViewModel.await(predicate: (OnboardingState) -> Boolean): OnboardingState =
        runBlocking { withTimeout(15_000) { state.first(predicate) } }

    @Test
    fun `first run counts every step ahead`() {
        val vm = vm()
        val start = vm.await { it.selection != null && it.hasTimetable }
        assertEquals(listOf(ROLE, CLASS, GROUPS, NOTIFICATIONS), start.steps)
        assertEquals(1, start.stepNumber)
        assertFalse(start.selection!!.editing)

        vm.setNotificationsAllowed(true)
        assertEquals(listOf(ROLE, CLASS, GROUPS), vm.await { NOTIFICATIONS !in it.steps }.steps)

        vm.chooseMode(UserMode.TEACHER)
        assertEquals(listOf(ROLE, TEACHER, NAME), vm.await { it.selection?.mode == UserMode.TEACHER }.steps)
    }

    @Test
    fun `editing a student opens their groups and saves on the last step`() {
        runBlocking { prefs.setStudent("-210", "1at", setOf("2. Grupa")) }
        val vm = vm()
        val state = vm.await { it.selection != null && it.divisions.isNotEmpty() }
        val sel = state.selection!!
        assertTrue(sel.editing)
        assertEquals(GROUPS, sel.step)
        assertEquals(listOf("2. Grupa"), sel.groups.values.filterNotNull())
        assertEquals(listOf(ROLE, CLASS, GROUPS), state.steps)
        assertEquals(3, state.stepNumber)
        assertTrue(state.isLastStep)

        assertTrue(vm.back())
        assertEquals(CLASS, vm.await { it.selection?.step == CLASS }.selection!!.step)
        vm.next()
        vm.await { it.selection?.step == GROUPS }
        vm.next()
        vm.await { it.finished }
        assertEquals(setOf("2. Grupa"), runBlocking { prefs.preferences.first().groupNames })
    }

    @Test
    fun `editing a teacher opens the name`() {
        runBlocking { prefs.setTeacher("-161", "AG", "Anna Grzebień") }
        val state = vm().await { it.selection != null }
        assertEquals(NAME, state.selection!!.step)
        assertEquals("Anna Grzebień", state.selection!!.fullName)
        assertEquals(listOf(ROLE, TEACHER, NAME), state.steps)
    }
}
