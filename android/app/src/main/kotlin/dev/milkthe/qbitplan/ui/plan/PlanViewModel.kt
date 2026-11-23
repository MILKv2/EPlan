package dev.milkthe.qbitplan.ui.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.core.schedule.isSchoolDay
import dev.milkthe.qbitplan.core.schedule.nearestSchoolDay
import dev.milkthe.qbitplan.core.schedule.schoolWeekOf
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.SyncState
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.data.resolve
import dev.milkthe.qbitplan.ui.common.DayChip
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LessonItemMapper
import dev.milkthe.qbitplan.ui.common.dayOfWeekText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class PlanMode { DAY, WEEK }

@Immutable
data class PlanHeader(
    val profile: Profile? = null,
    val sync: SyncState = SyncState(),
    val lastRefresh: Instant? = null,
    val hasTimetable: Boolean = true,
    val isTeacher: Boolean = false,
)

sealed interface DayPlanState {
    data object Loading : DayPlanState
    data object NoData : DayPlanState

    @Immutable
    data class Ready(val lessons: List<LessonItem>, val periodStarts: Map<Int, LocalTime>) : DayPlanState {
        val hasChanges: Boolean get() = lessons.any { it.status != LessonStatus.NORMAL }
    }
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    clock: Clock,
) : ViewModel() {

    val today: LocalDate = LocalDate.now(clock)

    /** Monday–Friday of this school week; on a weekend, next week. */
    val dates: List<LocalDate> = schoolWeekOf(nearestSchoolDay(today))
    val initialPage: Int = dates.indexOf(today).coerceAtLeast(0)

    private val weekendState = MutableStateFlow(!today.isSchoolDay())
    val showWeekend: StateFlow<Boolean> = weekendState.asStateFlow()

    private val modeState = MutableStateFlow(PlanMode.DAY)
    val mode: StateFlow<PlanMode> = modeState.asStateFlow()

    private val prefs = preferences.preferences

    val header: StateFlow<PlanHeader> = combine(repository.timetable, prefs, sync.state, repository.lastRefresh) { tt, p, s, last ->
        PlanHeader(
            profile = tt?.let { p.resolve(it.value) },
            sync = s,
            lastRefresh = last,
            hasTimetable = tt != null,
            isTeacher = p.mode == UserMode.TEACHER,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanHeader())

    private val days: Map<LocalDate, StateFlow<DayPlanState>> = dates.associateWith { date ->
        combine(repository.timetable, repository.substitutions(date), prefs) { tt, subs, p ->
            if (tt == null) return@combine DayPlanState.NoData
            val timetable = tt.value
            val planner = DayPlanner(timetable)
            val profile = p.resolve(timetable)
            val lessons = when (profile) {
                is Profile.Student -> planner.forClass(
                    profile.schoolClass.id, date, subs?.value,
                    selectedGroupNames = profile.groupNames.takeIf { p.onlyMyGroups && it.isNotEmpty() },
                )
                is Profile.TeacherProfile -> planner.forTeacher(profile.teacher.id, profile.fullName, date, subs?.value)
                null -> emptyList()
            }
            DayPlanState.Ready(
                lessons = LessonItemMapper(timetable).map(
                    lessons,
                    showClasses = p.mode == UserMode.TEACHER,
                    viewerTeacherId = (profile as? Profile.TeacherProfile)?.teacher?.id,
                ),
                periodStarts = timetable.periods.associate { it.number to it.start },
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayPlanState.Loading)
    }

    val dayChips: StateFlow<List<DayChip>> = combine(dates.map { days.getValue(it) }) { states ->
        dates.mapIndexed { i, date ->
            DayChip(
                date = date,
                weekday = date.dayOfWeekText(),
                day = date.dayOfMonth.toString(),
                isToday = date == today,
                hasChanges = (states[i] as? DayPlanState.Ready)?.hasChanges == true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dates.map { DayChip(it, it.dayOfWeekText(), it.dayOfMonth.toString(), it == today, false) })

    init {
        dates.forEach(sync::ensureSubstitutions)
    }

    fun day(date: LocalDate): StateFlow<DayPlanState> = days.getValue(date)

    fun toggleWeek() {
        weekendState.value = false
        modeState.value = if (modeState.value == PlanMode.DAY) PlanMode.WEEK else PlanMode.DAY
    }

    fun showDay() {
        weekendState.value = false
        modeState.value = PlanMode.DAY
    }

    fun refresh() = sync.refresh()
}
