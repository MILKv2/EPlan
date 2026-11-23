package dev.milkthe.qbitplan.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.nearestSchoolDay
import dev.milkthe.qbitplan.core.schedule.periodAt
import dev.milkthe.qbitplan.core.schedule.schoolWeekOf
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.common.DayChip
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LessonItemMapper
import dev.milkthe.qbitplan.ui.common.dayOfWeekText
import dev.milkthe.qbitplan.ui.navigation.DetailRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** A related class / teacher the detail links to. */
@Immutable
data class Related(val kind: DetailKind, val id: String, val label: String)

/** What a room is used for right now: `null` = free. */
@Immutable
data class RoomNow(val classes: String, val subject: String)

@Immutable
data class DetailState(
    val loading: Boolean = true,
    val title: String = "",
    val fullName: String? = null,
    val roomNow: RoomNow? = null,
    val related: List<Related> = emptyList(),
    val lessons: List<LessonItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<DetailRoute>()
    val kind: DetailKind = DetailKind.valueOf(route.kind)

    val today: LocalDate = LocalDate.now(clock)
    val dates: List<LocalDate> = schoolWeekOf(nearestSchoolDay(today))
    val chips: List<DayChip> = dates.map { DayChip(it, it.dayOfWeekText(), it.dayOfMonth.toString(), it == today, false) }

    private val selected = MutableStateFlow(dates.indexOf(today).coerceAtLeast(0))
    val selectedIndex: StateFlow<Int> = selected.asStateFlow()

    val state: StateFlow<DetailState> = selected.flatMapLatest { index ->
        val date = dates[index]
        combine(repository.timetable, repository.substitutions(date), preferences.preferences) { tt, subs, prefs ->
            tt ?: return@combine DetailState(loading = false)
            val timetable = tt.value
            val planner = DayPlanner(timetable)
            val mapper = LessonItemMapper(timetable)
            when (kind) {
                DetailKind.TEACHER -> {
                    val teacher = timetable.teachers[route.id] ?: return@combine DetailState(loading = false)
                    val fullName = prefs.teacherFullName.takeIf { prefs.teacherId == teacher.id }
                    DetailState(
                        loading = false,
                        title = teacher.short,
                        fullName = fullName,
                        related = timetable.lessonsForTeacher(teacher.id).flatMap { it.classIds }.distinct()
                            .mapNotNull { timetable.classes[it] }.sortedBy { it.short }
                            .map { Related(DetailKind.CLASS, it.id, it.short) },
                        lessons = mapper.map(planner.forTeacher(teacher.id, fullName, date, subs?.value), showClasses = true, viewerTeacherId = teacher.id),
                    )
                }
                DetailKind.CLASS -> {
                    val cls = timetable.classes[route.id] ?: return@combine DetailState(loading = false)
                    DetailState(
                        loading = false,
                        title = cls.short,
                        related = timetable.lessonsForClass(cls.id).flatMap { it.teacherIds }.distinct()
                            .mapNotNull { timetable.teachers[it] }.sortedBy { it.short }
                            .map { Related(DetailKind.TEACHER, it.id, it.short) },
                        lessons = mapper.map(planner.forClass(cls.id, date, subs?.value), showClasses = false),
                    )
                }
                DetailKind.ROOM -> {
                    val room = timetable.classrooms[route.id] ?: return@combine DetailState(loading = false)
                    val plan = planner.forClassroom(room.id, date, subs?.value)
                    DetailState(
                        loading = false,
                        title = room.short,
                        roomNow = if (date == today) roomNow(timetable, plan, room.short, mapper) else null,
                        related = timetable.lessonsForClassroom(room.id).flatMap { it.classIds }.distinct()
                            .mapNotNull { timetable.classes[it] }.sortedBy { it.short }
                            .map { Related(DetailKind.CLASS, it.id, it.short) },
                        lessons = mapper.map(plan, showClasses = true),
                    )
                }
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    init {
        dates.forEach(sync::ensureSubstitutions)
    }

    fun select(index: Int) { selected.value = index }

    private fun roomNow(
        tt: Timetable,
        plan: List<dev.milkthe.qbitplan.core.schedule.PlannedLesson>,
        short: String,
        mapper: LessonItemMapper,
    ): RoomNow? {
        val period = periodAt(tt.periods, LocalTime.now(clock)) ?: return null
        val lesson = plan.firstOrNull { period in it.periods && it.status.takesPlace && it.classroom?.new?.split(", ")?.contains(short) == true }
            ?: return null
        return RoomNow(lesson.classNames.joinToString(", "), mapper.subjectName(lesson.subject.new))
    }
}
