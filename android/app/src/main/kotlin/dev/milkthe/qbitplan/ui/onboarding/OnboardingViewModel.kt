package dev.milkthe.qbitplan.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.schedule.knownTeacherNames
import dev.milkthe.qbitplan.core.schedule.previousSchoolDays
import dev.milkthe.qbitplan.core.schedule.upcomingSchoolDays
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.ui.common.PolishLocale
import dev.milkthe.qbitplan.ui.common.byFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class OnboardingStep { MODE, CLASS, GROUPS, TEACHER, TEACHER_NAME }

@Immutable
data class Option(val id: String, val title: String, val subtitle: String? = null)

@Immutable
data class Selection(
    val step: OnboardingStep = OnboardingStep.MODE,
    val mode: UserMode? = null,
    val classId: String? = null,
    val groups: Map<String, String?> = emptyMap(),
    val teacherId: String? = null,
    val teacherQuery: String = "",
    val fullName: String = "",
)

@Immutable
data class OnboardingState(
    val selection: Selection = Selection(),
    val hasTimetable: Boolean = false,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
    val classes: List<Option> = emptyList(),
    val divisions: List<Division> = emptyList(),
    val teachers: List<Option> = emptyList(),
    val names: List<String> = emptyList(),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: SchoolRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    clock: Clock,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())

    // Full names exist only in substitutions, so look at a few recent and upcoming days.
    private val nameDays: List<LocalDate> = LocalDate.now(clock).let { previousSchoolDays(it, 5) + upcomingSchoolDays(it, 2) }

    private val names: Flow<List<String>> = combine(nameDays.map { repository.substitutions(it) }) { days ->
        knownTeacherNames(days.filterNotNull().map { it.value })
    }

    val state: StateFlow<OnboardingState> = combine(repository.timetable, sync.state, selection, names) { tt, s, sel, allNames ->
        val timetable = tt?.value
        OnboardingState(
            selection = sel,
            hasTimetable = timetable != null,
            refreshing = s.refreshing,
            failed = s.error != null,
            classes = timetable?.classes?.values?.sortedBy { it.short }?.map { Option(it.id, it.short) }.orEmpty(),
            divisions = if (timetable != null && sel.classId != null) divisionsOf(timetable, sel.classId) else emptyList(),
            teachers = timetable?.let { teacherOptions(it, sel.teacherQuery) }.orEmpty(),
            names = rankNames(allNames, timetable?.teachers?.get(sel.teacherId)?.short),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    init {
        viewModelScope.launch {
            if (repository.timetable.first() == null) sync.refresh()
        }
    }

    fun retry() = sync.refresh()

    fun chooseMode(mode: UserMode) = selection.update {
        it.copy(mode = mode, step = if (mode == UserMode.STUDENT) OnboardingStep.CLASS else OnboardingStep.TEACHER)
    }

    fun chooseClass(id: String) = selection.update { it.copy(classId = id, groups = emptyMap()) }

    fun setGroups(groups: Map<String, String?>) = selection.update { it.copy(groups = groups) }

    fun chooseTeacher(id: String) = selection.update { it.copy(teacherId = id) }

    fun setTeacherQuery(query: String) = selection.update { it.copy(teacherQuery = query) }

    fun setFullName(name: String) = selection.update { it.copy(fullName = name) }

    fun next() {
        val sel = selection.value
        when (sel.step) {
            OnboardingStep.CLASS -> if (state.value.divisions.isEmpty()) finish() else go(OnboardingStep.GROUPS)
            OnboardingStep.GROUPS -> finish()
            OnboardingStep.TEACHER -> {
                nameDays.forEach(sync::ensureSubstitutions)
                go(OnboardingStep.TEACHER_NAME)
            }
            OnboardingStep.TEACHER_NAME -> finish()
            OnboardingStep.MODE -> Unit
        }
    }

    /** Returns false when already at the first step (let the system handle back). */
    fun back(): Boolean {
        val previous = when (selection.value.step) {
            OnboardingStep.MODE -> return false
            OnboardingStep.CLASS, OnboardingStep.TEACHER -> OnboardingStep.MODE
            OnboardingStep.GROUPS -> OnboardingStep.CLASS
            OnboardingStep.TEACHER_NAME -> OnboardingStep.TEACHER
        }
        go(previous)
        return true
    }

    private fun go(step: OnboardingStep) = selection.update { it.copy(step = step) }

    private fun finish() {
        val sel = selection.value
        viewModelScope.launch {
            val tt = repository.timetable.first()?.value ?: return@launch
            when (sel.mode) {
                UserMode.STUDENT -> {
                    val cls = sel.classId?.let { tt.classes[it] } ?: return@launch
                    preferences.setStudent(cls.id, cls.short, sel.groups.values.filterNotNull().toSet())
                }
                UserMode.TEACHER -> {
                    val teacher = sel.teacherId?.let { tt.teachers[it] } ?: return@launch
                    preferences.setTeacher(teacher.id, teacher.short, sel.fullName)
                }
                null -> Unit
            }
        }
    }

    /** Shorts aren't unique, so each option lists subjects and classes to tell them apart. */
    private fun teacherOptions(tt: Timetable, query: String): List<Option> {
        val needle = query.trim().lowercase(PolishLocale)
        return tt.teachers.values.map { teacher ->
            val lessons = tt.lessonsForTeacher(teacher.id)
            val subjects = lessons.mapNotNull { tt.subjects[it.subjectId]?.short }.byFrequency().take(4)
            val classes = lessons.flatMap { it.classIds }.mapNotNull { tt.classes[it]?.short }.distinct().sorted().take(6)
            Option(teacher.id, teacher.short, listOf(subjects.joinToString(", "), classes.joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null })
        }
            .filter { needle.isEmpty() || needle in it.title.lowercase(PolishLocale) || needle in it.subtitle.orEmpty().lowercase(PolishLocale) }
            .sortedWith(compareBy({ it.title.lowercase(PolishLocale) }, { it.subtitle }))
    }

    /** Names whose initials match the chosen short (e.g. "KW" ↔ Wojciech Książkiewicz) go first. */
    private fun rankNames(names: List<String>, short: String?): List<String> {
        if (short == null) return names
        val letters = short.filter { it.isUpperCase() }.toSet()
        return names.sortedByDescending { name ->
            name.split(' ', '-').mapNotNull { it.firstOrNull()?.uppercaseChar() }.toSet() == letters
        }
    }
}
