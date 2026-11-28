package dev.milkthe.qbitplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.takahirom.roborazzi.captureRoboImage
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.data.FakeEdupageApi
import dev.milkthe.qbitplan.data.InMemoryResponseCache
import dev.milkthe.qbitplan.data.MutableClock
import dev.milkthe.qbitplan.data.PlanDayTracker
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.StartDay
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.fixture
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.common.AppIcon
import dev.milkthe.qbitplan.ui.common.LocalClock
import dev.milkthe.qbitplan.ui.plan.PlanScreen
import dev.milkthe.qbitplan.ui.plan.PlanViewModel
import dev.milkthe.qbitplan.ui.search.SearchScreen
import dev.milkthe.qbitplan.ui.search.SearchTab
import dev.milkthe.qbitplan.ui.search.SearchViewModel
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

/**
 * Google Play listing graphics: the icon, the feature graphic and phone
 * screenshots at 1080 × 1920, since the screen tests' 360 × 760 dp is taller than Play's 2:1 limit.
 * Written to build/screenshots with `-Proborazzi.record=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [36])
class StoreGraphicsTest {

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 24, 9, 5).atZone(zone).toInstant(), zone)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api = FakeEdupageApi().apply { versionsBody = fixture("ttviewer_2026-11-28.json") }
    private val repo = SchoolRepository(api, InMemoryResponseCache(), clock, scope)
    private lateinit var prefs: UserPreferencesRepository
    private val sync by lazy { SyncManager(repo, prefs, StartDay(repo, prefs, clock), clock, scope) }
    private val planDays by lazy { PlanDayTracker(StartDay(repo, prefs, clock), repo, prefs, clock, scope) }

    @Before
    fun setUp() = runBlocking {
        prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        repo.refresh(listOf(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 25)), versionDate = LocalDate.of(2026, 11, 24))
        prefs.setStudent("-210", "1at", GroupSelection(setOf("2. Grupa")))
        Unit
    }

    @After
    fun tearDown() = scope.cancel()

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

    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/screenshots/store_$name.png")

    private fun planVm() = PlanViewModel(repo, prefs, sync, planDays, clock)

    @Test
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun icon() {
        // Square: Play rounds the corners itself.
        compose.setContent { AppIcon(size = 512.dp, cornerRadius = 0.dp) }
        capture("icon")
    }

    @Test
    @Config(qualifiers = "w1024dp-h500dp-land-mdpi")
    fun feature() {
        show {
            val colors = QbitTheme.colors
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(colors.accent.soft, colors.background))),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    AppIcon(size = 220.dp, cornerRadius = 52.dp)
                    Column {
                        Text("E-Plan", fontSize = 96.sp, lineHeight = 104.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                        Text("Plan lekcji i zastępstwa", fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, color = colors.accent.softForeground)
                        Text("Twój plan z EduPage, także offline", fontSize = 28.sp, lineHeight = 36.sp, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        waitForText("E-Plan")
        capture("feature")
    }

    @Test
    fun plan() {
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = planVm()) }
        waitForText("Anna Grzebień")
        capture("plan")
    }

    @Test
    fun dark() {
        show(dark = true) { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = planVm()) }
        waitForText("Anna Grzebień")
        capture("dark")
    }

    @Test
    fun week() {
        val vm = planVm().apply { toggleWeek() }
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = vm) }
        waitForText("Pt")
        capture("week")
    }

    @Test
    fun teacher() {
        runBlocking { prefs.setTeacher("-161", "AG", "Anna Grzebień") }
        show { PlanScreen(onEditProfile = {}, onOpenSettings = {}, vm = planVm()) }
        waitForText("1at")
        capture("teacher")
    }

    @Test
    fun substitutions() {
        val vm = SubstitutionsViewModel(repo, prefs, sync, planDays, clock)
        runBlocking { vm.state.first { !it.loading } }
        show { SubstitutionsScreen(vm) }
        waitForText("Nieobecni")
        capture("substitutions")
    }

    @Test
    fun freeRooms() {
        val vm = SearchViewModel(repo, prefs, sync, clock).apply { selectTab(SearchTab.FREE_ROOMS) }
        show { SearchScreen(onOpen = { _, _ -> }, vm = vm) }
        waitForText("Wolne · ")
        capture("free_rooms")
    }
}
