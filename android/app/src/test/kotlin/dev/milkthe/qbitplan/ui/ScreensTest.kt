package dev.milkthe.qbitplan.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.github.takahirom.roborazzi.captureRoboImage
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.core.model.VersionPin
import dev.milkthe.qbitplan.core.update.AppVersion
import dev.milkthe.qbitplan.core.update.ReleaseResponse
import dev.milkthe.qbitplan.crash.CrashScreen
import dev.milkthe.qbitplan.data.FakeEdupageApi
import dev.milkthe.qbitplan.data.FakeReleaseApi
import dev.milkthe.qbitplan.data.InMemoryResponseCache
import dev.milkthe.qbitplan.data.MutableClock
import dev.milkthe.qbitplan.data.PlanDayTracker
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.StartDay
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.UpdateRepository
import dev.milkthe.qbitplan.data.fixture
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.common.LocalClock
import dev.milkthe.qbitplan.ui.onboarding.OnboardingScreen
import dev.milkthe.qbitplan.ui.onboarding.OnboardingViewModel
import dev.milkthe.qbitplan.ui.plan.PlanScreen
import dev.milkthe.qbitplan.ui.plan.PlanViewModel
import dev.milkthe.qbitplan.ui.search.DetailScreen
import dev.milkthe.qbitplan.ui.search.DetailViewModel
import dev.milkthe.qbitplan.ui.search.SearchScreen
import dev.milkthe.qbitplan.ui.search.SearchTab
import dev.milkthe.qbitplan.ui.search.SearchViewModel
import dev.milkthe.qbitplan.ui.settings.SettingsScreen
import dev.milkthe.qbitplan.ui.settings.SettingsViewModel
import dev.milkthe.qbitplan.ui.settings.VersionScreen
import dev.milkthe.qbitplan.ui.settings.VersionViewModel
import dev.milkthe.qbitplan.ui.substitutions.SubstitutionsScreen
import dev.milkthe.qbitplan.ui.substitutions.SubstitutionsViewModel
import dev.milkthe.qbitplan.ui.theme.AppTheme
import dev.milkthe.qbitplan.ui.theme.QbitTheme
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * Renders every screen against the real data stack fed with fixtures, on a small
 * low-end phone size. Run with `-Proborazzi.record=true` to write PNGs to build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xhdpi", sdk = [36])
class ScreensTest {

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 24, 9, 5).atZone(zone).toInstant(), zone)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // The version list as published on 2026-11-28 (188-190 visible); the fake serves plan 189 for any version.
    private val api = FakeEdupageApi().apply { versionsBody = fixture("ttviewer_2026-11-28.json") }
    private val repo = SchoolRepository(api, InMemoryResponseCache(), clock, scope)
    private val sync by lazy { SyncManager(repo, prefs, StartDay(repo, prefs, clock), clock, scope) }
    private val planDays by lazy { PlanDayTracker(StartDay(repo, prefs, clock), repo, prefs, clock, scope) }
    private val releases = FakeReleaseApi()
    private val updates = UpdateRepository(releases, InMemoryResponseCache(), AppVersion(1, 0, 1), clock, scope)
    private lateinit var prefs: UserPreferencesRepository

    @Before
    fun setUp() = runBlocking {
        prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        repo.refresh(listOf(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 25)), versionDate = LocalDate.of(2026, 11, 24))
        Unit
    }

    @After
    fun tearDown() = scope.cancel()

    private fun student() = runBlocking { prefs.setStudent("-210", "1at", GroupSelection(setOf("2. Grupa"))) }

    private fun teacher() = runBlocking { prefs.setTeacher("-161", "AG", "Anna Grzebień") }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalClock provides clock) {
                AppTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(color = QbitTheme.colors.background) { content() }
                }
            }
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    @Test
    fun planStudentLight() {
        student()
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = PlanViewModel(repo, prefs, sync, planDays, clock)) }
        waitForText("Anna Grzebień")
        capture("plan_student_light")
    }

    @Test
    fun planStudentDark() {
        student()
        show(dark = true) { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = PlanViewModel(repo, prefs, sync, planDays, clock)) }
        waitForText("Anna Grzebień")
        capture("plan_student_dark")
    }

    @Test
    fun lessonSheet() {
        student()
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = PlanViewModel(repo, prefs, sync, planDays, clock)) }
        waitForText("Informatyka")
        compose.onAllNodesWithText("Informatyka", substring = true).onFirst().performClick()
        waitForText("Sala")
        waitForText("Podstawy elektrotechniki i elektroniki")
    }

    @Test
    fun planWeek() {
        student()
        val vm = PlanViewModel(repo, prefs, sync, planDays, clock).apply { toggleWeek() }
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = vm) }
        waitForText("Pt")
        capture("plan_week")
    }

    @Test
    fun planTeacher() {
        teacher()
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = PlanViewModel(repo, prefs, sync, planDays, clock)) }
        waitForText("1at")
        capture("plan_teacher")
    }

    @Test
    fun substitutions() {
        student()
        val vm = SubstitutionsViewModel(repo, prefs, sync, planDays, clock)
        // Robolectric sometimes misses the first emission from a background flow across tests;
        // start from the loaded state so the render check is deterministic.
        runBlocking { vm.state.first { !it.loading } }
        show { SubstitutionsScreen(vm) }
        waitForText("Nieobecni")
        capture("substitutions")
    }

    @Test
    fun searchTeachers() {
        show { SearchScreen(onOpen = { _, _ -> }, vm = SearchViewModel(repo, prefs, sync, clock)) }
        waitForText("AA")
        capture("search_teachers")
    }

    @Test
    fun freeRooms() {
        val vm = SearchViewModel(repo, prefs, sync, clock).apply { selectTab(SearchTab.FREE_ROOMS) }
        show { SearchScreen(onOpen = { _, _ -> }, vm = vm) }
        waitForText("Wolne · ")
        capture("free_rooms")
    }

    @Test
    fun classDetail() {
        val handle = SavedStateHandle(mapOf("kind" to "CLASS", "id" to "-210"))
        show { DetailScreen(onBack = {}, onOpen = { _, _ -> }, vm = DetailViewModel(handle, repo, prefs, sync, clock)) }
        waitForText("Historia")
        capture("class_detail")
    }

    @Test
    fun settings() {
        student()
        show { SettingsScreen(onAbout = {}, onEditProfile = {}, onVersion = {}, vm = SettingsViewModel(repo, prefs, sync, updates)) }
        waitForText("Automatycznie · listopad2026_RC3")
        capture("settings")
    }

    @Test
    fun settingsWithUpdate() {
        student()
        releases.next = { ReleaseResponse.Fresh(fixture("github/latest_release.json"), etag = null) }
        runBlocking { updates.check() }
        show { SettingsScreen(onAbout = {}, onEditProfile = {}, onVersion = {}, vm = SettingsViewModel(repo, prefs, sync, updates)) }
        waitForText("Nowa wersja 1.0.2")
        capture("settings_update")
    }

    @Test
    fun crash() {
        show { CrashScreen(onRestart = {}, onCopy = {}) }
        waitForText("Coś poszło nie tak")
        capture("crash")
    }

    @Test
    fun onboarding() {
        show { OnboardingScreen(OnboardingViewModel(repo, prefs, sync, clock)) }
        waitForText("Kim jesteś?")
        capture("onboarding")
    }

    @Test
    fun versionPicker() {
        student()
        show { VersionScreen(onBack = {}, vm = VersionViewModel(repo, prefs, sync, planDays)) }
        waitForText("grudzien2026_RC1")
        capture("version_picker")
    }

    @Test
    fun planWithPinnedVersion() {
        student()
        runBlocking {
            val pin = VersionPin("188", newestAtPin = "190")
            prefs.setVersionPin(pin)
            repo.refresh(emptyList(), versionDate = LocalDate.of(2026, 11, 24), pin = pin)
        }
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = PlanViewModel(repo, prefs, sync, planDays, clock)) }
        waitForText("Wybrana ręcznie wersja planu: listopad2026_RC2")
        capture("plan_pinned_version")
    }

    @Test
    fun planOpensNextWeekAfterFridayLessons() {
        student()
        clock.now = LocalDateTime.of(2026, 11, 27, 19, 0).atZone(zone).toInstant()
        val vm = PlanViewModel(repo, prefs, sync, planDays, clock)
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = vm) }
        // The sync line is shown with the week; waiting on text also runs the main looper the view model uses.
        waitForText("Zsynchronizowano")
        val week = vm.week.value!!
        assertEquals(LocalDate.of(2026, 11, 30), week.dates[week.initialPage])
        capture("plan_next_week")
    }
}
