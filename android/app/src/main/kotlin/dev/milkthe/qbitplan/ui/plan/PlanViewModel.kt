package dev.milkthe.qbitplan.ui.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.Period
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.nearestSchoolDay
import dev.milkthe.qbitplan.core.schedule.schoolWeekOf
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.SyncState
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.data.resolve
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LessonItemMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class PlanMode { DAY, WEEK }

@Immutable
data class PlanHeader(
    val profile: Profile? = null,
    val hasGroups: Boolean = false,
    val onlyMyGroups: Boolean = true,
    val mode: PlanMode = PlanMode.DAY,
    val sync: SyncState = SyncState(),
    val lastRefresh: Instant? = null,
    val hasTimetable: Boolean = true,
)

sealed interface DayPlanState {
    data object Loading : DayPlanState
    data object NoData : DayPlanState

    @Immutable
    data class Ready(
        val lessons: List<LessonItem>,
        val periods: List<Period>,
        val substitutionsLoaded: Boolean,
        val message: String?,
    ) : DayPlanState
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    clock: Clock,
) : ViewModel() {

    val today: LocalDate = LocalDate.now(clock)
    private val firstDay = nearestSchoolDay(today)
    val dates: List<LocalDate> = schoolWeekOf(firstDay) + schoolWeekOf(firstDay.plusWeeks(1))
    val initialPage: Int = dates.indexOf(firstDay).coerceAtLeast(0)

    private val onlyMyGroups = MutableStateFlow(true)
    private val mode = MutableStateFlow(PlanMode.DAY)
    private val prefs = preferences.preferences

    val header: StateFlow<PlanHeader> = combine(
        repository.timetable, prefs, combine(onlyMyGroups, mode, ::Pair), sync.state, repository.lastRefresh,
    ) { tt, p, (mine, m), s, last ->
        val profile = tt?.let { p.resolve(it.value) }
        PlanHeader(
            profile = profile,
            hasGroups = profile is Profile.Student && profile.groupNames.isNotEmpty(),
            onlyMyGroups = mine,
            mode = m,
            sync = s,
            lastRefresh = last,
            hasTimetable = tt != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanHeader())

    private val days = HashMap<LocalDate, StateFlow<DayPlanState>>()

    /** Called from composition for visible pages only; flows are cached per date. */
    fun day(date: LocalDate): StateFlow<DayPlanState> = days.getOrPut(date) {
        sync.ensureSubstitutions(date)
        combine(repository.timetable, repository.substitutions(date), prefs, onlyMyGroups) { tt, subs, p, mine ->
            if (tt == null) return@combine DayPlanState.NoData
            val timetable = tt.value
            val planner = DayPlanner(timetable)
            val profile = p.resolve(timetable)
            val lessons = when (profile) {
                is Profile.Student -> planner.forClass(
                    profile.schoolClass.id, date, subs?.value,
                    selectedGroupNames = profile.groupNames.takeIf { mine && it.isNotEmpty() },
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
                periods = timetable.periods,
                substitutionsLoaded = subs != null,
                message = subs?.value?.message,
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayPlanState.Loading)
    }

    fun setOnlyMyGroups(value: Boolean) { onlyMyGroups.value = value }
    fun setMode(value: PlanMode) { mode.value = value }
    fun refresh() = sync.refresh()
}
