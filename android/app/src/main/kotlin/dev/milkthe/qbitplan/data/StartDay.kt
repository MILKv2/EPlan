package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.isSchoolDay
import dev.milkthe.qbitplan.core.schedule.planStartDay
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** The day the plan and the substitutions open on: today until the profile's last lesson is over, then the next school day. */
class StartDay @Inject constructor(
    private val repository: SchoolRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): LocalDate {
        val today = LocalDate.now(clock)
        return planStartDay(today, LocalTime.now(clock), lastLessonEnd(today))
    }

    /** A day of only cancelled lessons still ends after the last of them. */
    private suspend fun lastLessonEnd(date: LocalDate): LocalTime? = withContext(Dispatchers.Default) {
        if (!date.isSchoolDay()) return@withContext null
        val timetable = repository.timetable.first()?.value ?: return@withContext null
        val prefs = preferences.preferences.first()
        val substitutions = repository.substitutions(date).first()?.value
        val planner = DayPlanner(timetable)
        val lessons = when (val profile = prefs.resolve(timetable)) {
            is Profile.Student -> planner.forClass(
                profile.schoolClass.id, date, substitutions,
                selectedGroupNames = profile.groupNames.takeIf { prefs.onlyMyGroups && it.isNotEmpty() },
            )
            is Profile.TeacherProfile -> planner.forTeacher(profile.teacher.id, profile.fullName, date, substitutions)
            null -> return@withContext null
        }
        lessons.filter { it.status.takesPlace }.ifEmpty { lessons }
            .mapNotNull { timetable.period(it.periods.last)?.end }
            .maxOrNull()
    }
}
