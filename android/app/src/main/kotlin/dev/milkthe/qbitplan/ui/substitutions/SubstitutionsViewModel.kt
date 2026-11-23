package dev.milkthe.qbitplan.ui.substitutions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.AbsenceNote
import dev.milkthe.qbitplan.core.model.Substitution
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.core.schedule.upcomingSchoolDays
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.SyncState
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.common.LessonItemMapper
import dev.milkthe.qbitplan.ui.common.PolishLocale
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.theme.toLessonStatus
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
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class SubstitutionRow(
    val key: String,
    val periods: String?,
    val status: LessonStatus,
    val groups: String?,
    val oldSubject: String?,
    val subject: String?,
    val oldTeacher: String?,
    val teacher: String?,
    val oldRoom: String?,
    val room: String?,
    val movedFrom: Int?,
    val movedTo: Int?,
    val withoutClasses: String?,
)

@Immutable
data class ClassSection(val className: String, val rows: List<SubstitutionRow>)

@Immutable
data class SubstitutionsState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val absentTeachers: List<String> = emptyList(),
    val unavailableRooms: List<String> = emptyList(),
    val absentClasses: List<String> = emptyList(),
    val message: String? = null,
    val pinned: List<ClassSection> = emptyList(),
    val sections: List<ClassSection> = emptyList(),
    val fetchedAt: Instant? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubstitutionsViewModel @Inject constructor(
    private val repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    clock: Clock,
) : ViewModel() {

    val today: LocalDate = LocalDate.now(clock)
    val dates: List<LocalDate> = upcomingSchoolDays(today, 5)

    private val selected = MutableStateFlow(dates.first())
    val selectedDate: StateFlow<LocalDate> = selected.asStateFlow()

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    val syncState: StateFlow<SyncState> = sync.state
    val lastRefresh: StateFlow<Instant?> = repository.lastRefresh.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<SubstitutionsState> = combine(
        selected.flatMapLatest { repository.substitutions(it) },
        repository.timetable,
        preferences.preferences,
        query,
    ) { day, tt, prefs, q ->
        if (day == null) return@combine SubstitutionsState(loading = false, missing = true)
        val subs = day.value
        val names = tt?.let { LessonItemMapper(it.value) }
        val needle = q.trim().lowercase(PolishLocale)

        val sections = subs.substitutions
            .filter { needle.isEmpty() || it.matches(needle, names) }
            .groupBy { it.className }
            .map { (cls, rows) -> ClassSection(cls, rows.mapIndexed { i, r -> r.toRow("$cls-$i", names) }) }
            .sortedWith(compareBy({ it.className.firstOrNull()?.digitToIntOrNull() ?: 9 }, { it.className }))

        val pinned = when (prefs.mode) {
            UserMode.STUDENT -> sections.filter { it.className == prefs.classShort }
            UserMode.TEACHER -> prefs.teacherFullName?.let { name ->
                subs.substitutions.filter { it.involvesTeacher(name) }
                    .groupBy { it.className }
                    .map { (cls, rows) -> ClassSection(cls, rows.mapIndexed { i, r -> r.toRow("mine-$cls-$i", names) }) }
            }.orEmpty()
            null -> emptyList()
        }

        SubstitutionsState(
            loading = false,
            absentTeachers = subs.absentTeachers.map { it.text() },
            unavailableRooms = subs.unavailableClassrooms.map { it.text() },
            absentClasses = subs.absentClasses.map { it.text() },
            message = subs.message,
            pinned = pinned,
            sections = if (prefs.mode == UserMode.STUDENT) sections.filterNot { it.className == prefs.classShort } else sections,
            fetchedAt = day.fetchedAt,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubstitutionsState())

    init {
        sync.ensureSubstitutions(selected.value)
    }

    fun select(date: LocalDate) {
        selected.value = date
        sync.ensureSubstitutions(date)
    }

    fun setQuery(value: String) { query.value = value }

    fun refresh() = sync.refreshSubstitutions(selected.value)

    private fun AbsenceNote.text() = periods?.let { "$name (${it.text()})" } ?: name

    private fun Substitution.matches(needle: String, names: LessonItemMapper?): Boolean =
        listOfNotNull(
            className, teacher?.old, teacher?.new, subject?.old, subject?.new,
            names?.subjectName(subject?.old), names?.subjectName(subject?.new),
            classroom?.old, classroom?.new,
        ).plus(groups).any { needle in it.lowercase(PolishLocale) }

    private fun Substitution.toRow(key: String, names: LessonItemMapper?): SubstitutionRow {
        fun name(short: String?) = short?.let { names?.subjectName(it) ?: it }
        val subj = subject
        val subjectChanged = subj != null && subj.isChanged && subj.old != null && subj.new != null
        val teacherChanged = teacher?.isChanged == true
        return SubstitutionRow(
            key = key,
            periods = periods?.text(),
            status = type.toLessonStatus(),
            groups = groups.joinToString(", ").ifEmpty { null },
            oldSubject = if (subjectChanged) name(subject?.old) else null,
            subject = name(subject?.new ?: subject?.old),
            oldTeacher = if (teacherChanged) teacher?.old else null,
            teacher = if (teacherChanged) teacher?.new else teacher?.old,
            oldRoom = classroom?.takeIf { it.isChanged }?.old,
            room = classroom?.new,
            movedFrom = movedFromPeriod,
            movedTo = movedToPeriod,
            withoutClasses = withoutClasses.joinToString(", ").ifEmpty { null },
        )
    }
}
