package dev.milkthe.qbitplan.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.upcomingSchoolDays
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LessonItemMapper
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
import javax.inject.Inject

/** One info line, e.g. "matem" → "KK". */
@Immutable
data class InfoLine(val label: String, val value: String)

@Immutable
data class DetailState(
    val loading: Boolean = true,
    val title: String = "",
    val subtitle: String? = null,
    val info: List<InfoLine> = emptyList(),
    val lessons: List<LessonItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<DetailRoute>()
    val kind: DetailKind = DetailKind.valueOf(route.kind)

    val today: LocalDate = LocalDate.now(clock)
    val dates: List<LocalDate> = upcomingSchoolDays(today, 5)

    private val selected = MutableStateFlow(dates.first())
    val selectedDate: StateFlow<LocalDate> = selected.asStateFlow()

    val state: StateFlow<DetailState> = selected.flatMapLatest { date ->
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
                        subtitle = fullName,
                        info = teacherInfo(timetable, teacher.id),
                        lessons = mapper.map(planner.forTeacher(teacher.id, fullName, date, subs?.value), showClasses = true, viewerTeacherId = teacher.id),
                    )
                }
                DetailKind.CLASS -> {
                    val cls = timetable.classes[route.id] ?: return@combine DetailState(loading = false)
                    DetailState(
                        loading = false,
                        title = cls.short,
                        subtitle = cls.classTeacherId?.let { timetable.teachers[it]?.short },
                        info = classInfo(timetable, cls.id, mapper),
                        lessons = mapper.map(planner.forClass(cls.id, date, subs?.value), showClasses = false),
                    )
                }
                DetailKind.ROOM -> {
                    val room = timetable.classrooms[route.id] ?: return@combine DetailState(loading = false)
                    DetailState(
                        loading = false,
                        title = room.short,
                        subtitle = room.name.takeIf { it != room.short },
                        lessons = mapper.map(planner.forClassroom(room.id, date, subs?.value), showClasses = true),
                    )
                }
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    init {
        sync.ensureSubstitutions(dates.first())
    }

    fun select(date: LocalDate) {
        selected.value = date
        sync.ensureSubstitutions(date)
    }

    /** Classes taught, per subject. */
    private fun teacherInfo(tt: Timetable, teacherId: String): List<InfoLine> =
        tt.lessonsForTeacher(teacherId)
            .groupBy { tt.subjects[it.subjectId]?.short.orEmpty() }
            .map { (subject, lessons) ->
                InfoLine(subject, lessons.flatMap { it.classIds }.mapNotNull { tt.classes[it]?.short }.distinct().sorted().joinToString(", "))
            }
            .sortedBy { it.label }

    /** Teachers of the class, per subject. */
    private fun classInfo(tt: Timetable, classId: String, mapper: LessonItemMapper): List<InfoLine> =
        tt.lessonsForClass(classId)
            .groupBy { tt.subjects[it.subjectId]?.short.orEmpty() }
            .map { (subject, lessons) ->
                InfoLine(mapper.subjectName(subject), lessons.flatMap { it.teacherIds }.mapNotNull { tt.teachers[it]?.short }.distinct().sorted().joinToString(", "))
            }
            .sortedBy { it.label }
}
