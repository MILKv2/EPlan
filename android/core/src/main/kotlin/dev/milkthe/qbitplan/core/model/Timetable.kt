package dev.milkthe.qbitplan.core.model

import java.time.LocalDate
import java.time.LocalTime

// `data class` to klasa na dane: Kotlin sam generuje equals/hashCode/toString
// i copy(). Wszystkie pola są `val`, czyli tylko do odczytu.

/** Jedna wersja planu z listy `getTTViewerData`. */
data class TimetableVersion(
    val ttNum: String,
    val schoolYear: Int,
    val name: String,
    val validFrom: LocalDate,
    val hidden: Boolean,
)

data class TimetableVersions(
    /** Wersja, którą szkoła pokazuje domyślnie; `null`, gdy brak dla tego roku. */
    val defaultTtNum: String?,
    val versions: List<TimetableVersion>,
) {
    val default: TimetableVersion? get() = versions.find { it.ttNum == defaultTtNum }
}

/** Lekcja (godzina lekcyjna) z dzwonkami, np. 1 = 07:55-08:40. */
data class Period(val number: Int, val name: String, val start: LocalTime, val end: LocalTime)

/** Dzień tygodnia szkolnego; `index` 0 = poniedziałek. */
data class SchoolDay(val index: Int, val name: String, val short: String)

/** Tydzień A/B; `index` 0 = A. W tej szkole nieużywane (wszystkie lekcje w obu). */
data class SchoolWeek(val index: Int, val name: String, val short: String)

/** W planie są tylko skróty; **skróty nie są unikalne**, unikalne jest `id`. */
data class Teacher(val id: String, val short: String)

data class SchoolClass(val id: String, val name: String, val short: String, val classTeacherId: String?)

data class Classroom(val id: String, val name: String, val short: String)

data class Subject(val id: String, val name: String, val short: String)

/**
 * Grupa w obrębie klasy. Grupy z tym samym [division] wzajemnie się wykluczają
 * (np. „1. Grupa” i „2. Grupa”). [entireClass] = „Cała klasa”.
 */
data class Group(
    val id: String,
    val name: String,
    val classId: String,
    val entireClass: Boolean,
    val division: String,
)

/**
 * Jedna lekcja w planie stałym, czyli połączona para card + lesson z EduPage:
 * konkretny dzień, zakres lekcji, tygodnie, przedmiot, nauczyciele, sale.
 */
data class TimetableLesson(
    val cardId: String,
    val lessonId: String,
    /** 0 = poniedziałek. */
    val day: Int,
    val periods: PeriodRange,
    /** Indeksy tygodni (0 = A, 1 = B), w których lekcja się odbywa. */
    val weeks: Set<Int>,
    val subjectId: String,
    val teacherIds: List<String>,
    val classIds: List<String>,
    val groupIds: List<String>,
    val classroomIds: List<String>,
)

/** Cały plan stały szkoły (wszystkie klasy) w jednej wersji. */
data class Timetable(
    val ttNum: String,
    val schoolName: String,
    /** Np. „Ważność: 21.11.2026-31.12.2026”. */
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

    /** Grupy klasy bez „Cała klasa”, posortowane wg podziału. */
    fun groupsOfClass(classId: String): List<Group> =
        groups.values.filter { it.classId == classId && !it.entireClass }.sortedBy { it.division }

    /**
     * Zamienia nazwy grup (np. „1. Grupa”, „ang2”) na ich ID w tej wersji planu.
     * ID grup (`*12`) są lokalne dla jednej wersji planu, a nazwy są stałe,
     * dlatego w ustawieniach zapisujemy nazwy.
     */
    fun groupIdsForNames(classId: String, names: Set<String>): Set<String> =
        groups.values.filter { it.classId == classId && it.name in names }.map { it.id }.toSet()

    /**
     * Lekcje klasy. [selectedGroupIds] = grupy wybrane przez ucznia
     * (`null` = pokaż wszystkie grupy). Zob. [isForClass].
     */
    fun lessonsForClass(classId: String, selectedGroupIds: Set<String>? = null): List<TimetableLesson> =
        lessons.filter { isForClass(it, classId, selectedGroupIds) }

    fun lessonsForTeacher(teacherId: String): List<TimetableLesson> =
        lessons.filter { teacherId in it.teacherIds }

    fun lessonsForClassroom(classroomId: String): List<TimetableLesson> =
        lessons.filter { classroomId in it.classroomIds }

    /**
     * Czy lekcja dotyczy ucznia klasy [classId] z wybranymi grupami.
     * - lekcja całej klasy (albo bez grup tej klasy): tak;
     * - lekcja grupy: tak, jeśli uczeń wybrał tę grupę albo nie wybrał
     *   żadnej grupy w tym podziale (wtedy nie wiemy, więc pokazujemy).
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
