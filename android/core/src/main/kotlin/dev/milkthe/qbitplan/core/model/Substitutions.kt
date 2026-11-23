package dev.milkthe.qbitplan.core.model

import java.time.LocalDate
import java.time.LocalDateTime

data class DaySubstitutions(
    val date: LocalDate,
    val absentTeachers: List<AbsenceNote>,
    val absentClasses: List<AbsenceNote>,
    val unavailableClassrooms: List<AbsenceNote>,
    /** Header lines we don't recognise, kept verbatim. */
    val otherNotes: List<String>,
    val substitutions: List<Substitution>,
    /** Shown instead of a table, e.g. "W tym dniu nie ma żadnych zastępstw." */
    val message: String?,
    /** From the "asctimetables.com - dd.MM.yyyy HH:mm:ss" footer. */
    val generatedAt: LocalDateTime?,
)

/** [periods] `null` = the whole day. */
data class AbsenceNote(val name: String, val periods: PeriodRange?)

/** `old == new` means unchanged; `new == null` means removed (e.g. no substitute). */
data class Change(val old: String?, val new: String?) {
    val isChanged: Boolean get() = old != new

    companion object {
        fun unchanged(value: String) = Change(value, value)
    }
}

enum class SubstitutionType {
    CHANGE,
    NO_SUBSTITUTE,
    CANCELLED,
    MOVED_AWAY,
    MOVED_HERE,
    CLASS_ABSENT,
}

/** One row of the substitutions table. */
data class Substitution(
    /** Matches `SchoolClass.short`. */
    val className: String,
    /** `null` = the whole day. */
    val periods: PeriodRange?,
    val type: SubstitutionType,
    /** Group names of the WHOLE (possibly joint) lesson, across classes. Empty = whole class. */
    val groups: List<String>,
    /** Subject shorts. */
    val subject: Change?,
    /** Full teacher names – the plan only has shorts. */
    val teacher: Change?,
    /** Classroom shorts. */
    val classroom: Change?,
    val movedFromPeriod: Int?,
    val movedToPeriod: Int?,
    /** "(Bez klasy 1dt)": the lesson runs without these classes. */
    val withoutClasses: List<String>,
    val unknownParts: List<String>,
    val whatText: String,
    val infoText: String,
) {
    fun involvesTeacher(fullName: String): Boolean {
        val wanted = normalizeName(fullName)
        return listOfNotNull(teacher?.old, teacher?.new).any { normalizeName(it) == wanted }
    }
}

fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()
