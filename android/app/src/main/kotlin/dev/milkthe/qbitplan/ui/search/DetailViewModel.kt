package dev.milkthe.qbitplan.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.PlannedLesson
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
data class DetailHeader(
    val title: String = "",
    val fullName: String? = null,
    val roomNow: RoomNow? = null,
    val related: List<Related> = emptyList(),
)

sealed interface DetailDay {
    data object Loading : DetailDay

    @Immutable
    data class Ready(val lessons: List<LessonItem>) : DetailDay
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<DetailRoute>()
    val kind: DetailKind = DetailKind.valueOf(route.kind)

    val today: LocalDate = LocalDate.now(clock)
    val dates: List<LocalDate> = schoolWeekOf(nearestSchoolDay(today))
    val chips: List<DayChip> = dates.map { DayChip(it, it.dayOfWeekText(), it.dayOfMonth.toString(), it == today, false) }
    val initialPage: Int = dates.indexOf(today).coerceAtLeast(0)

    /** Full name the user gave for themselves, shown only on their own plan. */
    private val ownFullName: Flow<String?> = preferences.preferences.map { prefs ->
        prefs.teacherFullName.takeIf { kind == DetailKind.TEACHER && prefs.teacherId == route.id }
    }.distinctUntilChanged()

    val header: StateFlow<DetailHeader> = combine(repository.timetable, ownFullName, roomPlanToday()) { tt, fullName, roomToday ->
        tt ?: return@combine DetailHeader()
        val timetable = tt.value
        when (kind) {
            DetailKind.TEACHER -> {
                val teacher = timetable.teachers[route.id] ?: return@combine DetailHeader()
                DetailHeader(
                    title = teacher.short,
                    fullName = fullName,
                    related = timetable.lessonsForTeacher(teacher.id).flatMap { it.classIds }.distinct()
                        .mapNotNull { timetable.classes[it] }.sortedBy { it.short }
                        .map { Related(DetailKind.CLASS, it.id, it.short) },
                )
            }
            DetailKind.CLASS -> {
                val cls = timetable.classes[route.id] ?: return@combine DetailHeader()
                DetailHeader(
                    title = cls.short,
                    related = timetable.lessonsForClass(cls.id).flatMap { it.teacherIds }.distinct()
                        .mapNotNull { timetable.teachers[it] }.sortedBy { it.short }
                        .map { Related(DetailKind.TEACHER, it.id, it.short) },
                )
            }
            DetailKind.ROOM -> {
                val room = timetable.classrooms[route.id] ?: return@combine DetailHeader()
                DetailHeader(
                    title = room.short,
                    roomNow = roomToday?.let { roomNow(timetable, it, room.short, LessonItemMapper(timetable)) },
                    related = timetable.lessonsForClassroom(room.id).flatMap { it.classIds }.distinct()
                        .mapNotNull { timetable.classes[it] }.sortedBy { it.short }
                        .map { Related(DetailKind.CLASS, it.id, it.short) },
                )
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailHeader())

    private val days: Map<LocalDate, StateFlow<DetailDay>> = dates.associateWith { date ->
        combine(repository.timetable, repository.substitutions(date), ownFullName) { tt, subs, fullName ->
            tt ?: return@combine DetailDay.Ready(emptyList())
            val timetable = tt.value
            DetailDay.Ready(lessons(timetable, DayPlanner(timetable), LessonItemMapper(timetable), date, subs?.value, fullName))
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailDay.Loading)
    }

    init {
        dates.forEach(sync::ensureSubstitutions)
    }

    fun day(date: LocalDate): StateFlow<DetailDay> = days.getValue(date)

    private fun lessons(
        timetable: Timetable,
        planner: DayPlanner,
        mapper: LessonItemMapper,
        date: LocalDate,
        subs: DaySubstitutions?,
        fullName: String?,
    ): List<LessonItem> = when (kind) {
        DetailKind.TEACHER -> timetable.teachers[route.id]?.let { teacher ->
            mapper.map(planner.forTeacher(teacher.id, fullName, date, subs), showClasses = true, viewerTeacherId = teacher.id)
        }
        DetailKind.CLASS -> timetable.classes[route.id]?.let { mapper.map(planner.forClass(it.id, date, subs), showClasses = false) }
        DetailKind.ROOM -> timetable.classrooms[route.id]?.let { mapper.map(planner.forClassroom(it.id, date, subs), showClasses = true) }
    }.orEmpty()

    /** Today's plan of the room for the "now" subtitle; `null` for other kinds or on a weekend. */
    private fun roomPlanToday(): Flow<List<PlannedLesson>?> {
        if (kind != DetailKind.ROOM || today !in dates) return flowOf(null)
        return combine(repository.timetable, repository.substitutions(today)) { tt, subs ->
            tt?.value?.let { timetable -> timetable.classrooms[route.id]?.let { DayPlanner(timetable).forClassroom(it.id, today, subs?.value) } }
        }
    }

    private fun roomNow(tt: Timetable, plan: List<PlannedLesson>, short: String, mapper: LessonItemMapper): RoomNow? {
        val period = periodAt(tt.periods, LocalTime.now(clock)) ?: return null
        val lesson = plan.firstOrNull { period in it.periods && it.status.takesPlace && it.classroom?.new?.split(", ")?.contains(short) == true }
            ?: return null
        return RoomNow(lesson.classNames.joinToString(", "), mapper.subjectName(lesson.subject.new))
    }
}
