package dev.milkthe.qbitplan.ui.substitutions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.AbsenceNote
import dev.milkthe.qbitplan.core.model.Substitution
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.core.schedule.isSchoolDay
import dev.milkthe.qbitplan.core.schedule.nearestSchoolDay
import dev.milkthe.qbitplan.core.schedule.nextSchoolDay
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
import kotlinx.coroutines.flow.Flow
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
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class SubstitutionRow(
    val key: String,
    val period: String,
    val status: LessonStatus,
    val title: String,
    val oldTitle: String?,
    val group: String?,
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
    val isEmpty: Boolean = false,
    val mineTitle: String? = null,
    val isTeacher: Boolean = false,
    val mine: List<SubstitutionRow> = emptyList(),
    val others: List<ClassSection> = emptyList(),
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

    /** The two quick days: today (or the next school day on a weekend) and the school day after it. */
    val quickDays: List<LocalDate> = nearestSchoolDay(today).let { listOf(it, nextSchoolDay(it)) }

    private val selected = MutableStateFlow(quickDays.first())
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
        val isTeacher = prefs.mode == UserMode.TEACHER

        val mineRows = when (prefs.mode) {
            UserMode.STUDENT -> subs.substitutions.filter { it.className == prefs.classShort }
            UserMode.TEACHER -> prefs.teacherFullName?.let { name -> subs.substitutions.filter { it.involvesTeacher(name) } }.orEmpty()
            null -> emptyList()
        }
        val others = subs.substitutions
            .filter { !(prefs.mode == UserMode.STUDENT && it.className == prefs.classShort) }
            .filter { needle.isEmpty() || it.className.lowercase(PolishLocale).contains(needle) }
            .groupBy { it.className }
            .map { (cls, rows) -> ClassSection(cls, rows.mapIndexed { i, r -> r.toRow("$cls-$i", names, withClass = false) }) }
            .sortedWith(compareBy({ it.className.firstOrNull()?.digitToIntOrNull() ?: 9 }, { it.className }))

        SubstitutionsState(
            loading = false,
            absentTeachers = subs.absentTeachers.map { it.text() },
            unavailableRooms = subs.unavailableClassrooms.map { it.text() },
            absentClasses = subs.absentClasses.map { it.text() },
            isEmpty = subs.substitutions.isEmpty(),
            mineTitle = if (isTeacher) null else prefs.classShort,
            isTeacher = isTeacher,
            mine = mineRows.mapIndexed { i, r -> r.toRow("mine-$i", names, withClass = isTeacher) },
            others = others,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubstitutionsState())

    init {
        quickDays.forEach(sync::ensureSubstitutions)
    }

    fun select(date: LocalDate) {
        selected.value = date
        sync.ensureSubstitutions(date)
    }

    fun setQuery(value: String) { query.value = value }

    fun refresh() = sync.refreshSubstitutions(selected.value)

    /** School days of [month] that have substitutions in the cache (no network). */
    fun daysWithChanges(month: YearMonth): Flow<Set<LocalDate>> {
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { it.isSchoolDay() }
        return combine(days.map { repository.substitutions(it) }) { all ->
            days.filterIndexed { i, _ -> all[i]?.value?.substitutions?.isNotEmpty() == true }.toSet()
        }.flowOn(Dispatchers.Default)
    }

    private fun AbsenceNote.text() = periods?.let { "$name (${it.text()})" } ?: name

    private fun Substitution.toRow(key: String, names: LessonItemMapper?, withClass: Boolean): SubstitutionRow {
        fun name(short: String?) = short?.let { names?.subjectName(it) ?: it }
        val subj = subject
        val subjectChanged = subj != null && subj.isChanged && subj.old != null && subj.new != null
        val title = name(subj?.new ?: subj?.old).orEmpty()
        val teacherChanged = teacher?.isChanged == true
        return SubstitutionRow(
            key = key,
            period = periods?.text().orEmpty(),
            status = type.toLessonStatus(),
            title = if (withClass) "$className · $title" else title,
            oldTitle = if (subjectChanged) name(subj.old) else null,
            group = groups.joinToString(", ").ifEmpty { null },
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
