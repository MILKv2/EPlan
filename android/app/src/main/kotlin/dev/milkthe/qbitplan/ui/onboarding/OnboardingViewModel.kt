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

enum class OnboardingStep(val number: Int) { ROLE(1), CLASS(2), GROUPS(2), TEACHER(2), NOTIFICATIONS(3) }

@Immutable
data class Option(val id: String, val title: String, val subtitle: String? = null)

@Immutable
data class Selection(
    val step: OnboardingStep = OnboardingStep.ROLE,
    val mode: UserMode = UserMode.STUDENT,
    val classId: String? = null,
    val classQuery: String = "",
    val groups: Map<String, String?> = emptyMap(),
    val teacherId: String? = null,
    val teacherQuery: String = "",
    /** Picked suggestion; `null` = typing the name. */
    val pickedName: String? = null,
    val typedName: String = "",
) {
    val fullName: String get() = (pickedName ?: typedName).trim()
}

@Immutable
data class OnboardingState(
    val selection: Selection = Selection(),
    val hasTimetable: Boolean = false,
    val failed: Boolean = false,
    val classCount: Int = 0,
    val classes: List<Option> = emptyList(),
    val divisions: List<Division> = emptyList(),
    val teachers: List<Option> = emptyList(),
    val suggestedNames: List<String> = emptyList(),
) {
    val className: String? get() = classes.find { it.id == selection.classId }?.title
    val teacherShort: String? get() = teachers.find { it.id == selection.teacherId }?.title
}

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
        val classes = timetable?.classes?.values?.sortedBy { it.short }?.map { Option(it.id, it.short) }.orEmpty()
        val cq = sel.classQuery.trim().lowercase(PolishLocale)
        val teachers = timetable?.let { teacherOptions(it) }.orEmpty()
        val tq = sel.teacherQuery.trim().lowercase(PolishLocale)
        OnboardingState(
            selection = sel,
            hasTimetable = timetable != null,
            failed = s.error != null && !s.refreshing,
            classCount = classes.size,
            classes = classes.filter { cq.isEmpty() || cq in it.title.lowercase(PolishLocale) },
            divisions = if (timetable != null && sel.classId != null) divisionsOf(timetable, sel.classId) else emptyList(),
            teachers = teachers.filter { tq.isEmpty() || tq in it.title.lowercase(PolishLocale) || tq in it.subtitle.orEmpty().lowercase(PolishLocale) },
            suggestedNames = suggestions(allNames, teachers.find { it.id == sel.teacherId }?.title),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    init {
        viewModelScope.launch {
            if (repository.timetable.first() == null) sync.refresh()
        }
        nameDays.forEach(sync::ensureSubstitutions)
    }

    fun retry() = sync.refresh()

    fun chooseMode(mode: UserMode) = selection.update { it.copy(mode = mode) }

    fun chooseClass(id: String) = selection.update { it.copy(classId = id, groups = emptyMap()) }

    fun setClassQuery(query: String) = selection.update { it.copy(classQuery = query) }

    fun setGroup(division: String, name: String?) = selection.update { it.copy(groups = it.groups + (division to name)) }

    fun chooseTeacher(id: String) = selection.update { it.copy(teacherId = id, pickedName = null) }

    fun setTeacherQuery(query: String) = selection.update { it.copy(teacherQuery = query) }

    fun pickName(name: String?) = selection.update { it.copy(pickedName = name) }

    fun setTypedName(name: String) = selection.update { it.copy(typedName = name, pickedName = null) }

    fun next() {
        val sel = selection.value
        val next = when (sel.step) {
            OnboardingStep.ROLE -> if (sel.mode == UserMode.STUDENT) OnboardingStep.CLASS else OnboardingStep.TEACHER
            OnboardingStep.CLASS -> if (state.value.divisions.isEmpty()) OnboardingStep.NOTIFICATIONS else OnboardingStep.GROUPS
            OnboardingStep.GROUPS, OnboardingStep.TEACHER -> OnboardingStep.NOTIFICATIONS
            OnboardingStep.NOTIFICATIONS -> return
        }
        selection.update { it.copy(step = next) }
    }

    /** Returns false at the first step (let the system handle back). */
    fun back(): Boolean {
        val sel = selection.value
        val previous = when (sel.step) {
            OnboardingStep.ROLE -> return false
            OnboardingStep.CLASS, OnboardingStep.TEACHER -> OnboardingStep.ROLE
            OnboardingStep.GROUPS -> OnboardingStep.CLASS
            OnboardingStep.NOTIFICATIONS -> when {
                sel.mode == UserMode.TEACHER -> OnboardingStep.TEACHER
                state.value.divisions.isEmpty() -> OnboardingStep.CLASS
                else -> OnboardingStep.GROUPS
            }
        }
        selection.update { it.copy(step = previous) }
        return true
    }

    fun finish(notifications: Boolean) {
        val sel = selection.value
        viewModelScope.launch {
            val tt = repository.timetable.first()?.value ?: return@launch
            preferences.setNotificationsEnabled(notifications)
            when (sel.mode) {
                UserMode.STUDENT -> {
                    val cls = sel.classId?.let { tt.classes[it] } ?: return@launch
                    preferences.setStudent(cls.id, cls.short, sel.groups.values.filterNotNull().toSet())
                }
                UserMode.TEACHER -> {
                    val teacher = sel.teacherId?.let { tt.teachers[it] } ?: return@launch
                    preferences.setTeacher(teacher.id, teacher.short, sel.fullName)
                }
            }
        }
    }

    /** Shorts aren't unique, so each option carries the main subject to tell them apart. */
    private fun teacherOptions(tt: Timetable): List<Option> {
        val duplicates = tt.teachers.values.groupingBy { it.short }.eachCount().filterValues { it > 1 }.keys
        return tt.teachers.values.map { teacher ->
            val lessons = tt.lessonsForTeacher(teacher.id)
            val subjects = lessons.mapNotNull { tt.subjects[it.subjectId]?.short }.byFrequency()
            Option(teacher.id, teacher.short, if (teacher.short in duplicates) subjects.firstOrNull() else null)
        }.sortedWith(compareBy({ it.title.lowercase(PolishLocale) }, { it.subtitle }))
    }

    /** Names whose initials match the chosen short (e.g. "KW" ↔ Wojciech Książkiewicz). */
    private fun suggestions(names: List<String>, short: String?): List<String> {
        if (short == null) return emptyList()
        val letters = short.filter { it.isUpperCase() }.toSet()
        return names.filter { name -> name.split(' ', '-').mapNotNull { it.firstOrNull()?.uppercaseChar() }.toSet() == letters }.take(3)
    }
}
