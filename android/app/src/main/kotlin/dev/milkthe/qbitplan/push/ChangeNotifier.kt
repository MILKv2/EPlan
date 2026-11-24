package dev.milkthe.qbitplan.push

import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.PlannedLesson
import dev.milkthe.qbitplan.core.schedule.changedLessons
import dev.milkthe.qbitplan.core.schedule.isSchoolDay
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.RefreshResult
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.data.resolve
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LessonItemMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface ChangeAlerts {
    /** [lessons]: the changed lessons of the user's plan on [date]. */
    fun show(date: LocalDate, lessons: List<LessonItem>)

    /** The day changed but couldn't be fetched to say how. */
    fun showUnspecified(date: LocalDate)
}

/**
 * Handles a push that [date]'s substitutions changed for the user's class or teacher topic:
 * fetches the day and tells what changed in their own plan (their groups only), by comparing
 * the plan built from the cached day with the plan built from the fresh one.
 */
@Singleton
class ChangeNotifier @Inject constructor(
    private val repository: SchoolRepository,
    private val preferences: UserPreferencesRepository,
    private val alerts: ChangeAlerts,
    private val clock: Clock,
) {
    suspend fun onDayChanged(date: LocalDate) {
        val prefs = preferences.preferences.first()
        if (!prefs.notificationsEnabled) return
        if (prefs.quietWeekends && !LocalDate.now(clock).isSchoolDay()) return
        val timetable = repository.timetable.first()?.value ?: return alerts.showUnspecified(date)
        val profile = prefs.resolve(timetable) ?: return

        val before = repository.cachedSubstitutions(date)?.value
        // FCM gives a high-priority message about 20 s; leave room to post the notification.
        val refreshed = withTimeoutOrNull(FETCH_TIMEOUT_MS) { repository.refreshSubstitutions(date) }
        if (refreshed != RefreshResult.Success) return alerts.showUnspecified(date)
        val after = repository.cachedSubstitutions(date)?.value

        val changes = withContext(Dispatchers.Default) {
            val planner = DayPlanner(timetable)
            fun plan(subs: DaySubstitutions?): List<PlannedLesson> = when (profile) {
                is Profile.Student -> planner.forClass(
                    profile.schoolClass.id, date, subs,
                    selectedGroupNames = profile.groupNames.takeIf { prefs.onlyMyGroups && it.isNotEmpty() },
                )
                is Profile.TeacherProfile -> planner.forTeacher(profile.teacher.id, profile.fullName, date, subs)
            }
            val teacherId = (profile as? Profile.TeacherProfile)?.teacher?.id
            LessonItemMapper(timetable).map(changedLessons(plan(before), plan(after)), showClasses = teacherId != null, viewerTeacherId = teacherId)
        }
        if (changes.isNotEmpty()) alerts.show(date, changes)
    }

    private companion object {
        const val FETCH_TIMEOUT_MS = 12_000L
    }
}
