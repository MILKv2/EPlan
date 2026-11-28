package dev.milkthe.qbitplan.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.data.FakeEdupageApi
import dev.milkthe.qbitplan.data.InMemoryResponseCache
import dev.milkthe.qbitplan.data.MutableClock
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Several pushes about one day, through the real notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationAlertsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 24, 7, 40).atZone(zone).toInstant(), zone)
    private val tuesday = LocalDate.of(2026, 11, 24)
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api = FakeEdupageApi()
    private lateinit var notifier: ChangeNotifier

    @Before
    fun setUp() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val repo = SchoolRepository(api, InMemoryResponseCache(), clock, scope)
        val prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        repo.refresh(substitutionDates = listOf(tuesday))
        prefs.setNotificationsEnabled(true)
        prefs.setStudent("-210", "1at", GroupSelection(setOf("2. Grupa")))
        notifier = ChangeNotifier(repo, prefs, NotificationAlerts(app, clock), clock)
    }

    @After
    fun tearDown() = scope.cancel()

    /** Period 4 is covered by Anna Grzebień, period 5 by Dorota Dziadosz in the fixture. */
    private fun push(period4: String, period5: String) = runBlocking {
        api.editSubstitutions = { it.replace("Anna Grzebień", period4).replace("Dorota Dziadosz", period5) }
        notifier.onDayChanged(tuesday)
    }

    private fun shownLines(): List<String> =
        manager.activeNotifications.single().notification.extras.notificationLines().map { it.text }

    @Test
    fun `later changes add to the unread notification`() {
        push("Beata Kukla", "Dorota Dziadosz")
        push("Beata Kukla", "Joanna Guzik")
        val lines = shownLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("4.") && "Beata Kukla" in lines[0])
        assertTrue(lines[1].startsWith("5.") && "Joanna Guzik" in lines[1])
    }

    @Test
    fun `a lesson changed twice shows only its latest substitute`() {
        push("Beata Kukla", "Dorota Dziadosz")
        push("Renata Rysz", "Dorota Dziadosz")
        assertTrue("Renata Rysz" in shownLines().single())
    }

    @Test
    fun `after the notification is gone a new one starts fresh`() {
        push("Beata Kukla", "Dorota Dziadosz")
        manager.cancelAll()
        push("Beata Kukla", "Joanna Guzik")
        assertTrue(shownLines().single().startsWith("5."))
    }
}
