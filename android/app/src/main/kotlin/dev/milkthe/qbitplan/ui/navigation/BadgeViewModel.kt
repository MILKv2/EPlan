package dev.milkthe.qbitplan.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.data.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Number of the user's lessons that change today: the badge on the Zastępstwa tab. */
@HiltViewModel
class BadgeViewModel @Inject constructor(
    repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    val todayChanges: StateFlow<Int> = combine(repository.timetable, repository.substitutions(today), preferences.preferences) { tt, subs, prefs ->
        val timetable = tt?.value ?: return@combine 0
        val day = subs?.value ?: return@combine 0
        val planner = DayPlanner(timetable)
        val lessons = when (val profile = prefs.resolve(timetable)) {
            is Profile.Student -> planner.forClass(
                profile.schoolClass.id, today, day,
                selectedGroupNames = profile.groupNames.takeIf { prefs.onlyMyGroups && it.isNotEmpty() },
            )
            is Profile.TeacherProfile -> planner.forTeacher(profile.teacher.id, profile.fullName, today, day)
            null -> emptyList()
        }
        lessons.count { it.status != LessonStatus.NORMAL }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
