package dev.milkthe.qbitplan.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.model.normalizeName
import dev.milkthe.qbitplan.core.schedule.knownTeacherNames
import dev.milkthe.qbitplan.core.schedule.previousSchoolDays
import dev.milkthe.qbitplan.core.schedule.shortFitsName
import dev.milkthe.qbitplan.core.schedule.teacherIdsByName
import dev.milkthe.qbitplan.core.schedule.upcomingSchoolDays
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferences
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.data.resolve
import dev.milkthe.qbitplan.ui.common.PolishLocale
import dev.milkthe.qbitplan.ui.common.byFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class OnboardingStep { ROLE, CLASS, GROUPS, TEACHER, NAME, NOTIFICATIONS }

/** [keywords]: also matched by the search, not shown (a teacher's full name). */
@Immutable
data class Option(val id: String, val title: String, val subtitle: String? = null, val keywords: String = "")

@Immutable
data class Selection(
    val step: OnboardingStep = OnboardingStep.ROLE,
    /** Changing an existing profile from Settings: nothing to ask about notifications, the last step saves. */
    val editing: Boolean = false,
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
    /** `null` until the saved profile is read. */
    val selection: Selection? = null,
    /** Steps of the current path; changes with the role, the class (groups or not) and the notification permission. */
    val steps: List<OnboardingStep> = listOf(OnboardingStep.ROLE),
    val hasTimetable: Boolean = false,
    val failed: Boolean = false,
    /** The profile is saved; an editor closes itself. */
    val finished: Boolean = false,
    val classCount: Int = 0,
    val classes: List<Option> = emptyList(),
    val divisions: List<Division> = emptyList(),
    val teachers: List<Option> = emptyList(),
    val suggestedNames: List<String> = emptyList(),
) {
    val className: String? get() = classes.find { it.id == selection?.classId }?.title
    val teacherShort: String? get() = teachers.find { it.id == selection?.teacherId }?.title
    val stepNumber: Int get() = selection?.let { steps.indexOf(it.step) + 1 } ?: 1
    val previousStep: OnboardingStep? get() = selection?.let { steps.getOrNull(steps.indexOf(it.step) - 1) }
    val isLastStep: Boolean get() = selection != null && steps.lastOrNull() == selection.step
}

/**
 * First-run onboarding, and the profile editor in Settings: with a saved profile it starts
 * on the last step (groups or name) with everything filled in.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: SchoolRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    clock: Clock,
) : ViewModel() {

    private val selection = MutableStateFlow<Selection?>(null)
    private val notificationsAllowed = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)

    // Full names exist only in substitutions, so look at a few recent and upcoming days.
    private val nameDays: List<LocalDate> = LocalDate.now(clock).let { previousSchoolDays(it, 5) + upcomingSchoolDays(it, 2) }

    /** [owners]: teacher id by normalized name, where substitutions tell whose name it is. */
    private class Names(val all: List<String> = emptyList(), val owners: Map<String, String> = emptyMap())

    private val names: StateFlow<Names> = combine(repository.timetable, combine(nameDays.map { repository.substitutions(it) }) { it.toList() }) { tt, days ->
        val loaded = days.filterNotNull().map { it.value }
        Names(knownTeacherNames(loaded), tt?.value?.let { teacherIdsByName(it, loaded) }.orEmpty())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Names())

    private class Catalog(val classes: List<Option>, val teachers: List<Option>)

    // Built once per timetable version and name list, not on every keystroke.
    private val catalog: Flow<Catalog?> = combine(repository.timetable, names) { tt, n ->
        tt?.value?.let { Catalog(classOptions(it), teacherOptions(it, n)) }
    }.flowOn(Dispatchers.Default)

    private val divisions: Flow<List<Division>> = combine(repository.timetable, selection.map { it?.classId }.distinctUntilChanged()) { tt, classId ->
        if (tt != null && classId != null) divisionsOf(tt.value, classId) else emptyList()
    }.flowOn(Dispatchers.Default)

    private val status = combine(sync.state, notificationsAllowed, finished) { s, allowed, done -> Triple(s, allowed, done) }

    val state: StateFlow<OnboardingState> = combine(catalog, status, selection, names, divisions) { cat, (s, allowed, done), sel, allNames, divs ->
        val cq = sel?.classQuery.orEmpty().trim().lowercase(PolishLocale)
        val tq = sel?.teacherQuery.orEmpty().trim().lowercase(PolishLocale)
        val classes = cat?.classes.orEmpty()
        val teachers = cat?.teachers.orEmpty()
        OnboardingState(
            selection = sel,
            steps = sel?.let { stepsFor(it, divs, allowed) } ?: listOf(OnboardingStep.ROLE),
            hasTimetable = cat != null,
            failed = s.error != null && !s.refreshing,
            finished = done,
            classCount = classes.size,
            classes = if (cq.isEmpty()) classes else classes.filter { cq in it.title.lowercase(PolishLocale) },
            divisions = divs,
            teachers = if (tq.isEmpty()) teachers else teachers.filter {
                tq in it.title.lowercase(PolishLocale) || tq in it.subtitle.orEmpty().lowercase(PolishLocale) || tq in it.keywords
            },
            suggestedNames = suggestions(allNames, teachers.find { it.id == sel?.teacherId }),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    init {
        viewModelScope.launch {
            val prefs = preferences.preferences.first()
            selection.value = if (prefs.isOnboarded) editSelection(prefs) else Selection()
        }
        viewModelScope.launch {
            if (repository.timetable.first() == null) sync.refresh()
        }
        nameDays.forEach(sync::ensureSubstitutions)
    }

    fun retry() = sync.refresh()

    /** Fed by the screen: the system permission can change while the app is open. */
    fun setNotificationsAllowed(allowed: Boolean) { notificationsAllowed.value = allowed }

    fun chooseMode(mode: UserMode) = edit { it.copy(mode = mode) }

    fun chooseClass(id: String) = edit { it.copy(classId = id, groups = emptyMap()) }

    fun setClassQuery(query: String) = edit { it.copy(classQuery = query) }

    fun setGroup(division: String, name: String?) = edit { it.copy(groups = it.groups + (division to name)) }

    fun chooseTeacher(id: String) = edit { it.copy(teacherId = id, pickedName = null) }

    fun setTeacherQuery(query: String) = edit { it.copy(teacherQuery = query) }

    fun pickName(name: String?) = edit { it.copy(pickedName = name) }

    fun setTypedName(name: String) = edit { it.copy(typedName = name, pickedName = null) }

    fun next() {
        viewModelScope.launch {
            val sel = selection.value ?: return@launch
            val steps = stepsFor(sel, divisions.first(), notificationsAllowed.value)
            when (val next = steps.getOrNull(steps.indexOf(sel.step) + 1)) {
                null -> if (sel.step != OnboardingStep.NOTIFICATIONS) finish(notifications = notificationsAllowed.value)
                else -> edit { it.copy(step = next) }
            }
        }
    }

    /** Returns false at the first step (let the system or the navigation handle back). */
    fun back(): Boolean {
        val previous = state.value.previousStep ?: return false
        edit { it.copy(step = previous) }
        return true
    }

    fun finish(notifications: Boolean) {
        val sel = selection.value ?: return
        viewModelScope.launch {
            val tt = repository.timetable.first()?.value ?: return@launch
            when (sel.mode) {
                UserMode.STUDENT -> {
                    val cls = sel.classId?.let { tt.classes[it] } ?: return@launch
                    preferences.setStudent(cls.id, cls.short, sel.groups.values.filterNotNull().toSet())
                }
                UserMode.TEACHER -> {
                    // The name is the surer identity: two teachers can share a short.
                    val teacher = names.value.owners[normalizeName(sel.fullName)]?.let { tt.teachers[it] }
                        ?: sel.teacherId?.let { tt.teachers[it] }
                        ?: return@launch
                    preferences.setTeacher(teacher.id, teacher.short, sel.fullName)
                }
            }
            if (!sel.editing) preferences.setNotificationsEnabled(notifications)
            finished.value = true
        }
    }

    private inline fun edit(crossinline change: (Selection) -> Selection) = selection.update { it?.let(change) }

    private fun stepsFor(sel: Selection, divisions: List<Division>, notificationsAllowed: Boolean): List<OnboardingStep> = buildList {
        add(OnboardingStep.ROLE)
        when (sel.mode) {
            UserMode.STUDENT -> {
                add(OnboardingStep.CLASS)
                // Until a class is picked, assume it has groups: most do.
                if (sel.classId == null || divisions.isNotEmpty()) add(OnboardingStep.GROUPS)
            }
            UserMode.TEACHER -> {
                add(OnboardingStep.TEACHER)
                add(OnboardingStep.NAME)
            }
        }
        // Stays while it's shown, even if the permission was granted in system settings meanwhile.
        if (!sel.editing && (!notificationsAllowed || sel.step == OnboardingStep.NOTIFICATIONS)) add(OnboardingStep.NOTIFICATIONS)
    }

    private suspend fun editSelection(prefs: UserPreferences): Selection = when (prefs.mode) {
        UserMode.TEACHER -> Selection(
            step = OnboardingStep.NAME,
            editing = true,
            mode = UserMode.TEACHER,
            teacherId = prefs.teacherId,
            typedName = prefs.teacherFullName.orEmpty(),
        )
        else -> {
            val tt = repository.timetable.first()?.value
            val cls = tt?.let { prefs.resolve(it) as? Profile.Student }?.schoolClass
            val divs = if (tt != null && cls != null) withContext(Dispatchers.Default) { divisionsOf(tt, cls.id) } else emptyList()
            Selection(
                step = if (divs.isEmpty()) OnboardingStep.CLASS else OnboardingStep.GROUPS,
                editing = true,
                mode = UserMode.STUDENT,
                classId = cls?.id,
                groups = divs.associate { d -> d.id to d.options.firstOrNull { it in prefs.groupNames } },
            )
        }
    }

    private fun classOptions(tt: Timetable): List<Option> =
        tt.classes.values.sortedBy { it.short }.map { Option(it.id, it.short) }

    /** Shorts aren't unique, so duplicates carry the surname (or else the main subject) to tell them apart. */
    private fun teacherOptions(tt: Timetable, names: Names): List<Option> {
        val byKey = names.all.associateBy { normalizeName(it) }
        val nameById = names.owners.entries.mapNotNull { (key, id) -> byKey[key]?.let { id to it } }.toMap()
        val duplicates = tt.teachers.values.groupingBy { it.short }.eachCount().filterValues { it > 1 }.keys
        return tt.teachers.values.map { teacher ->
            val name = nameById[teacher.id]
            val subtitle = if (teacher.short in duplicates) {
                name?.substringAfterLast(' ') ?: tt.lessonsForTeacher(teacher.id).mapNotNull { tt.subjects[it.subjectId]?.short }.byFrequency().firstOrNull()
            } else null
            Option(teacher.id, teacher.short, subtitle, keywords = name?.lowercase(PolishLocale).orEmpty())
        }.sortedWith(compareBy({ it.title.lowercase(PolishLocale) }, { it.subtitle }))
    }

    /** Names known to be this teacher's first, then unclaimed names whose initials fit the short. */
    private fun suggestions(names: Names, teacher: Option?): List<String> {
        if (teacher == null) return emptyList()
        val own = names.all.filter { names.owners[normalizeName(it)] == teacher.id }
        val unclaimed = names.all.filter { normalizeName(it) !in names.owners && shortFitsName(teacher.title, it) }
        return (own + unclaimed).take(3)
    }
}
