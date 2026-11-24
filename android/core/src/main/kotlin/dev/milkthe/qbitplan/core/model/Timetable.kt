package dev.milkthe.qbitplan.core.model

import java.time.LocalDate
import java.time.LocalTime

data class TimetableVersion(
    val ttNum: String,
    val schoolYear: Int,
    val name: String,
    val validFrom: LocalDate,
    val hidden: Boolean,
)

data class TimetableVersions(
    /** `null` when the school has no default timetable for the requested year. */
    val defaultTtNum: String?,
    val versions: List<TimetableVersion>,
) {
    val default: TimetableVersion? get() = versions.find { it.ttNum == defaultTtNum }
}

data class Period(val number: Int, val name: String, val start: LocalTime, val end: LocalTime)

/** [index] 0 = Monday. */
data class SchoolDay(val index: Int, val name: String, val short: String)

/** [index] 0 = week A. Unused by this school (every lesson is in both weeks). */
data class SchoolWeek(val index: Int, val name: String, val short: String)

/** Teacher shorts are NOT unique (e.g. three "KK"); only [id] is. */
data class Teacher(val id: String, val short: String)

data class SchoolClass(val id: String, val name: String, val short: String, val classTeacherId: String?)

data class Classroom(val id: String, val name: String, val short: String)

data class Subject(val id: String, val name: String, val short: String) {
    /**
     * Long vocational names don't fit a lesson tile and everyone uses their acronym
     * ("PiKUA" for "Przeglądy i konserwacja układów automatyki"). Shorts that aren't
     * acronyms ("Inst_aut_p", "matur_ang") would read worse than a cut name.
     */
    val displayName: String
        get() = if (name.length > LONG_NAME && short.all { it.isLetter() } && short.count { it.isUpperCase() } >= 2) short else name

    private companion object {
        const val LONG_NAME = 30
    }
}

/** Groups sharing a [division] are mutually exclusive (e.g. "1. Grupa" / "2. Grupa"). */
data class Group(
    val id: String,
    val name: String,
    val classId: String,
    val entireClass: Boolean,
    val division: String,
)

/** A placed lesson: an EduPage card joined with its lesson. */
data class TimetableLesson(
    val cardId: String,
    val lessonId: String,
    val day: Int,
    val periods: PeriodRange,
    val weeks: Set<Int>,
    val subjectId: String,
    val teacherIds: List<String>,
    val classIds: List<String>,
    val groupIds: List<String>,
    val classroomIds: List<String>,
)

/** The whole school's regular timetable in one version. */
data class Timetable(
    val ttNum: String,
    val schoolName: String,
    val validityText: String,
    val periods: List<Period>,
    val days: List<SchoolDay>,
    val weeks: List<SchoolWeek>,
    val teachers: Map<String, Teacher>,
    val classes: Map<String, SchoolClass>,
    val classrooms: Map<String, Classroom>,
    val subjects: Map<String, Subject>,
    val groups: Map<String, Group>,
    val lessons: List<TimetableLesson>,
) {
    fun period(number: Int): Period? = periods.find { it.number == number }

    fun classByShort(short: String): SchoolClass? = classes.values.find { it.short == short }

    fun subjectByShort(short: String): Subject? = subjects.values.find { it.short == short }

    fun classroomByShort(short: String): Classroom? = classrooms.values.find { it.short == short }

    fun groupsOfClass(classId: String): List<Group> =
        groups.values.filter { it.classId == classId && !it.entireClass }.sortedBy { it.division }

    /**
     * Group ids (`*12`) are regenerated with every timetable version, names are
     * stable – that's why settings store names and resolve them here.
     */
    fun groupIdsForNames(classId: String, names: Set<String>): Set<String> =
        groups.values.filter { it.classId == classId && it.name in names }.map { it.id }.toSet()

    /** [selectedGroupIds] `null` = all groups of the class. */
    fun lessonsForClass(classId: String, selectedGroupIds: Set<String>? = null): List<TimetableLesson> =
        lessons.filter { isForClass(it, classId, selectedGroupIds) }

    fun lessonsForTeacher(teacherId: String): List<TimetableLesson> =
        lessons.filter { teacherId in it.teacherIds }

    fun lessonsForClassroom(classroomId: String): List<TimetableLesson> =
        lessons.filter { classroomId in it.classroomIds }

    /**
     * A group lesson is shown when the student picked that group, or picked
     * nothing in that division (we can't tell, so we show it).
     */
    fun isForClass(lesson: TimetableLesson, classId: String, selectedGroupIds: Set<String>?): Boolean {
        if (classId !in lesson.classIds) return false
        val lessonGroups = lesson.groupIds.mapNotNull { groups[it] }.filter { it.classId == classId }
        if (lessonGroups.isEmpty() || lessonGroups.any { it.entireClass }) return true
        if (selectedGroupIds == null) return true
        val selectedDivisions = selectedGroupIds.mapNotNull { groups[it]?.division }.toSet()
        return lessonGroups.any { it.id in selectedGroupIds || it.division !in selectedDivisions }
    }
}
