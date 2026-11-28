package dev.milkthe.qbitplan.push

import dev.milkthe.qbitplan.core.schedule.isSchoolDay
import dev.milkthe.qbitplan.core.schedule.nearestSchoolDay
import dev.milkthe.qbitplan.data.RefreshResult
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface TimetableAlerts {
    /** [firstDay]: the first school day of the new version; [downloaded]: the app already has it. */
    fun showNewTimetable(firstDay: LocalDate, downloaded: Boolean)
}

/**
 * Handles the worker's push that a new timetable version was published: downloads it (the
 * version in force is still picked by date, see SchoolRepository.refresh) and says from when it applies.
 */
@Singleton
class TimetableNotifier @Inject constructor(
    private val sync: SyncManager,
    private val preferences: UserPreferencesRepository,
    private val alerts: TimetableAlerts,
    private val clock: Clock,
) {
    suspend fun onNewTimetable(validFrom: LocalDate) {
        val prefs = preferences.preferences.first()
        if (!prefs.notificationsEnabled) return
        // FCM gives a high-priority message about 20 s.
        val downloaded = withTimeoutOrNull(FETCH_TIMEOUT_MS) { sync.refreshNow() } == RefreshResult.Success
        // Still downloaded on a quiet weekend, just not announced.
        if (prefs.quietWeekends && !LocalDate.now(clock).isSchoolDay()) return
        alerts.showNewTimetable(nearestSchoolDay(validFrom), downloaded)
    }

    private companion object {
        const val FETCH_TIMEOUT_MS = 15_000L
    }
}
