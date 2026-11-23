package dev.milkthe.qbitplan.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.schedule.BellState
import dev.milkthe.qbitplan.core.schedule.DayPlanner
import dev.milkthe.qbitplan.core.schedule.bellStateAt
import dev.milkthe.qbitplan.core.schedule.nearestSchoolDay
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.ui.common.PolishLocale
import dev.milkthe.qbitplan.ui.common.byFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class SearchTab { TEACHERS, CLASSES, ROOMS, FREE_ROOMS }

enum class DetailKind { TEACHER, CLASS, ROOM }

@Immutable
data class SearchEntry(val id: String, val title: String, val subtitle: String?, private val haystack: String) {
    fun matches(query: String) = query.isEmpty() || query in haystack
}

@Immutable
data class SearchIndex(
    val teachers: List<SearchEntry> = emptyList(),
    val classes: List<SearchEntry> = emptyList(),
    val rooms: List<SearchEntry> = emptyList(),
)

/** [period] is the lesson the result is for; [isNext] when "now" falls in a break. */
@Immutable
data class FreeRoomsState(
    val time: LocalTime? = null,
    val period: Int? = null,
    val isNext: Boolean = false,
    val rooms: List<String> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SchoolRepository,
    private val sync: SyncManager,
    private val clock: Clock,
) : ViewModel() {

    private val tabState = MutableStateFlow(SearchTab.TEACHERS)
    val tab: StateFlow<SearchTab> = tabState.asStateFlow()

    private val queryState = MutableStateFlow("")
    val query: StateFlow<String> = queryState.asStateFlow()

    /** `null` = now. */
    private val pickedTime = MutableStateFlow<LocalTime?>(null)

    val date: LocalDate = nearestSchoolDay(LocalDate.now(clock))

    private val index: Flow<SearchIndex> = repository.timetable
        .map { tt -> tt?.value?.let(::buildIndex) ?: SearchIndex() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val results: StateFlow<SearchIndex> = combine(index, queryState) { idx, q ->
        val needle = q.trim().lowercase(PolishLocale)
        SearchIndex(
            teachers = idx.teachers.filter { it.matches(needle) },
            classes = idx.classes.filter { it.matches(needle) },
            rooms = idx.rooms.filter { it.matches(needle) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchIndex())

    private val minuteTicks: Flow<LocalTime> = flow {
        while (true) {
            emit(LocalTime.now(clock))
            delay(60_000)
        }
    }

    val freeRooms: StateFlow<FreeRoomsState> = pickedTime
        .flatMapLatest { picked -> if (picked != null) flowOf(picked to false) else minuteTicks.map { it to true } }
        .flatMapLatest { (time, isNow) ->
            combine(repository.timetable, repository.substitutions(date)) { tt, subs ->
                tt ?: return@combine FreeRoomsState(time = time.takeUnless { isNow }, loading = false)
                val timetable = tt.value
                val state = bellStateAt(timetable.periods, time)
                val (period, isNext) = when (state) {
                    is BellState.DuringLesson -> state.period.number to false
                    is BellState.Break -> if (isNow) state.next.number to true else null to false
                    BellState.AfterSchool -> null to false
                }
                FreeRoomsState(
                    time = time.takeUnless { isNow },
                    period = period,
                    isNext = isNext,
                    rooms = period?.let { p -> DayPlanner(timetable).freeClassrooms(date, p, subs?.value).map { it.short } }.orEmpty(),
                    loading = false,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FreeRoomsState())

    init {
        sync.ensureSubstitutions(date)
    }

    fun selectTab(value: SearchTab) { tabState.value = value }
    fun setQuery(value: String) { queryState.value = value }
    fun pickTime(value: LocalTime?) { pickedTime.value = value }

    private fun buildIndex(tt: Timetable): SearchIndex {
        fun shorts(ids: Iterable<String>, lookup: (String) -> String?) = ids.mapNotNull(lookup).distinct().sorted()

        val teachers = tt.teachers.values.map { teacher ->
            val lessons = tt.lessonsForTeacher(teacher.id)
            val subjects = lessons.mapNotNull { tt.subjects[it.subjectId]?.short }.byFrequency()
            val classes = shorts(lessons.flatMap { it.classIds }) { tt.classes[it]?.short }
            val subtitle = listOf(subjects.take(4).joinToString(", "), classes.take(6).joinToString(", "))
                .filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
            SearchEntry(teacher.id, teacher.short, subtitle, "${teacher.short} ${subtitle.orEmpty()}".lowercase(PolishLocale))
        }.sortedWith(compareBy({ it.title.lowercase(PolishLocale) }, { it.subtitle }))

        val classes = tt.classes.values.map { cls ->
            val tutor = cls.classTeacherId?.let { tt.teachers[it]?.short }
            SearchEntry(cls.id, cls.short, tutor, "${cls.short} ${cls.name}".lowercase(PolishLocale))
        }.sortedBy { it.title }

        val rooms = tt.classrooms.values.map { room ->
            SearchEntry(room.id, room.short, room.name.takeIf { it != room.short }, "${room.short} ${room.name}".lowercase(PolishLocale))
        }.sortedBy { it.title }

        return SearchIndex(teachers, classes, rooms)
    }
}
