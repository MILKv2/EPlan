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
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
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
import kotlinx.coroutines.flow.flow
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
data class SearchEntry(val id: String, val title: String, val subtitle: String?, val highlighted: Boolean, private val haystack: String) {
    fun matches(query: String) = query.isEmpty() || query in haystack
}

@Immutable
data class SearchIndex(
    val teachers: List<SearchEntry> = emptyList(),
    val classes: List<SearchEntry> = emptyList(),
    val rooms: List<SearchEntry> = emptyList(),
)

@Immutable
data class RoomTile(val room: String, val usedBy: String?, val unavailable: Boolean)

@Immutable
data class FreeRoomsState(
    val loading: Boolean = true,
    val pickedLesson: Boolean = false,
    val lessons: List<Int> = emptyList(),
    val period: Int? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val free: List<RoomTile> = emptyList(),
    val busy: List<RoomTile> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    private val clock: Clock,
) : ViewModel() {

    private val tabState = MutableStateFlow(SearchTab.TEACHERS)
    val tab: StateFlow<SearchTab> = tabState.asStateFlow()

    private val queryState = MutableStateFlow("")
    val query: StateFlow<String> = queryState.asStateFlow()

    /** `null` = the lesson running now (or the next one). */
    private val pickedPeriod = MutableStateFlow<Int?>(null)

    val date: LocalDate = nearestSchoolDay(LocalDate.now(clock))

    private val index: Flow<SearchIndex> = combine(repository.timetable, preferences.preferences) { tt, prefs ->
        tt?.value?.let { buildIndex(it, prefs.classId) } ?: SearchIndex()
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

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

    val freeRooms: StateFlow<FreeRoomsState> = combine(
        repository.timetable, repository.substitutions(date), pickedPeriod, minuteTicks,
    ) { tt, subs, picked, now ->
        val timetable = tt?.value ?: return@combine FreeRoomsState(loading = false)
        val current = when (val state = bellStateAt(timetable.periods, now)) {
            is BellState.DuringLesson -> state.period.number
            is BellState.Break -> state.next.number
            BellState.AfterSchool -> timetable.periods.firstOrNull()?.number
        }
        val period = picked ?: current ?: return@combine FreeRoomsState(loading = false)
        val occupancy = DayPlanner(timetable).classroomOccupancy(date, period, subs?.value)
        FreeRoomsState(
            loading = false,
            pickedLesson = picked != null,
            lessons = timetable.periods.map { it.number },
            period = period,
            start = timetable.period(period)?.start,
            end = timetable.period(period)?.end,
            free = occupancy.filter { it.isFree }.map { RoomTile(it.room.short, null, false) },
            busy = occupancy.filterNot { it.isFree }.map { RoomTile(it.room.short, it.classNames.joinToString(", ").ifEmpty { null }, it.unavailable) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FreeRoomsState())

    init {
        sync.ensureSubstitutions(date)
    }

    fun selectTab(value: SearchTab) {
        tabState.value = value
        queryState.value = ""
    }

    fun setQuery(value: String) { queryState.value = value }

    fun pickLesson(period: Int?) { pickedPeriod.value = period }

    private fun buildIndex(tt: Timetable, myClassId: String?): SearchIndex {
        val teachers = tt.teachers.values.map { teacher ->
            val lessons = tt.lessonsForTeacher(teacher.id)
            val subjects = lessons.mapNotNull { tt.subjects[it.subjectId]?.short }.byFrequency()
            val classes = lessons.flatMap { it.classIds }.mapNotNull { tt.classes[it]?.short }.distinct().sorted()
            val subtitle = listOf(subjects.take(4).joinToString(", "), classes.take(6).joinToString(", "))
                .filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
            SearchEntry(teacher.id, teacher.short, subtitle, false, "${teacher.short} ${subtitle.orEmpty()}".lowercase(PolishLocale))
        }.sortedWith(compareBy({ it.title.lowercase(PolishLocale) }, { it.subtitle }))

        val classes = tt.classes.values.map { cls ->
            SearchEntry(cls.id, cls.short, null, cls.id == myClassId, "${cls.short} ${cls.name}".lowercase(PolishLocale))
        }.sortedBy { it.title }

        val usedRooms = tt.lessons.flatMap { it.classroomIds }.toSet()
        val rooms = tt.classrooms.values.filter { it.id in usedRooms }.map { room ->
            SearchEntry(room.id, room.short, null, false, "${room.short} ${room.name}".lowercase(PolishLocale))
        }.sortedBy { it.title }

        return SearchIndex(teachers, classes, rooms)
    }
}
