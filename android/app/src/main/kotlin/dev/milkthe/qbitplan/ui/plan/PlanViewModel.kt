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
import dev.milkthe.qbitplan.data.PlanDayTracker
import dev.milkthe.qbitplan.data.PlanDays
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.SyncState
import dev.milkthe.qbitplan.data.versionStatus
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.data.resolve
import dev.milkthe.qbitplan.ui.common.DayChip
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LessonItemMapper
import dev.milkthe.qbitplan.ui.common.dayOfWeekText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class PlanMode { DAY, WEEK }

@Immutable
data class PlanHeader(
    val profile: Profile? = null,
    val sync: SyncState = SyncState(),
    val lastRefresh: Instant? = null,
    val hasTimetable: Boolean = true,
    val isTeacher: Boolean = false,
    val onlyMyGroups: Boolean = true,
    /** Short name of a version pinned in Settings; `null` while automatic. */
    val pinnedVersion: String? = null,
)

sealed interface DayPlanState {
    data object Loading : DayPlanState
    data object NoData : DayPlanState

    @Immutable
    data class Ready(val lessons: List<LessonItem>, val periodStarts: Map<Int, LocalTime>) : DayPlanState {
        val hasChanges: Boolean get() = lessons.any { it.status != LessonStatus.NORMAL }
    }
}

/** The school week the plan shows, the page it opens on, and [today] for the chips and the "now" line. */
@Immutable
data class PlanWeek(val dates: List<LocalDate>, val initialPage: Int, val chips: List<DayChip>, val today: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: SchoolRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    tracker: PlanDayTracker,
    clock: Clock,
) : ViewModel() {

    private val openedOn: LocalDate = LocalDate.now(clock)

    private val weekendState = MutableStateFlow(!openedOn.isSchoolDay())
    val showWeekend: StateFlow<Boolean> = weekendState.asStateFlow()

    private val modeState = MutableStateFlow(PlanMode.DAY)
    val mode: StateFlow<PlanMode> = modeState.asStateFlow()

    private val prefs = preferences.preferences

    val header: StateFlow<PlanHeader> = combine(repository.timetable, prefs, sync.state, repository.lastRefresh, repository.versions) { tt, p, s, last, versions ->
        PlanHeader(
            profile = tt?.let { p.resolve(it.value) },
            sync = s,
            lastRefresh = last,
            hasTimetable = tt != null,
            isTeacher = p.mode == UserMode.TEACHER,
            onlyMyGroups = p.onlyMyGroups,
            pinnedVersion = versionStatus(versions, tt?.value, p.versionPin)?.takeIf { it.pinned }?.name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanHeader())

    private val days = ConcurrentHashMap<LocalDate, StateFlow<DayPlanState>>()

    /** Whether the user swiped away from the page the plan opened on; then only a return from the background moves it. */
    private var userMoved = false

    /**
     * `null` until today's lessons are known: once the last one is over, the plan opens on the
     * next school day. Follows [PlanDayTracker], so it moves on after the last lesson and at midnight.
     */
    val week: StateFlow<PlanWeek?> = tracker.days.filterNotNull()
        .runningFold(null as PlanWeek?) { current, days -> nextWeek(current, days) }
        .filterNotNull()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Empty until [week] is known. */
    val dayChips: StateFlow<List<DayChip>> = week.filterNotNull().flatMapLatest { w ->
        combine(w.dates.map(::day)) { states ->
            w.chips.mapIndexed { i, chip -> chip.copy(hasChanges = (states[i] as? DayPlanState.Ready)?.hasChanges == true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Usually the week the plan opens on; fetched before today's lessons are even parsed.
        schoolWeekOf(nearestSchoolDay(openedOn)).forEach(sync::ensureSubstitutions)
    }

    private fun nextWeek(current: PlanWeek?, days: PlanDays): PlanWeek {
        fun chips(dates: List<LocalDate>) = dates.map { DayChip(it, it.dayOfWeekText(), it.dayOfMonth.toString(), it == days.today, false) }
        if (current != null && userMoved && !days.resumed) return current.copy(chips = chips(current.dates), today = days.today)

        val dates = schoolWeekOf(days.start)
        dates.forEach(sync::ensureSubstitutions)
        if (current != null && current.today != days.today) weekendState.value = !days.today.isSchoolDay()
        val next = PlanWeek(dates, dates.indexOf(days.start), chips(dates), days.today)
        if (next != current) userMoved = false
        return next
    }

    fun onPageSettled(page: Int) {
        week.value?.let { userMoved = page != it.initialPage }
    }

    fun day(date: LocalDate): StateFlow<DayPlanState> = days.getOrPut(date) { dayPlan(date) }

    private fun dayPlan(date: LocalDate): StateFlow<DayPlanState> =
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

    fun toggleWeek() {
        weekendState.value = false
        modeState.value = if (modeState.value == PlanMode.DAY) PlanMode.WEEK else PlanMode.DAY
    }

    fun showDay() {
        weekendState.value = false
        modeState.value = PlanMode.DAY
    }

    fun setOnlyMyGroups(enabled: Boolean) = viewModelScope.launch { preferences.setOnlyMyGroups(enabled) }

    fun refresh() = sync.refresh()
}
