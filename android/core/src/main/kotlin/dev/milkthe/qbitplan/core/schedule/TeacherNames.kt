package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.model.normalizeName
import java.text.Collator
import java.util.Locale

/**
 * Teacher full names seen in substitutions, sorted by surname (Polish collation).
 * The regular plan only has shorts, so this feeds the teacher onboarding picker.
 */
fun knownTeacherNames(days: Collection<DaySubstitutions>): List<String> {
    val names = buildList {
        for (day in days) {
            day.absentTeachers.forEach { add(it.name) }
            day.substitutions.forEach { s -> s.teacher?.old?.let(::add); s.teacher?.new?.let(::add) }
        }
    }
    val collator = Collator.getInstance(Locale.forLanguageTag("pl-PL"))
    return names.map { it.trim() }
        .filter { it.contains(' ') }
        .distinctBy { normalizeName(it) }
        .sortedWith { a, b -> collator.compare(a.surnameFirst(), b.surnameFirst()) }
}

private fun String.surnameFirst(): String = substringAfterLast(' ') + " " + substringBeforeLast(' ')

/**
 * Teacher ids by normalized full name. Shorts alone are ambiguous ("MK" fits Krystian Małeta and
 * Maciej Kania), but a substitution row naming the planned teacher sits on a regular lesson,
 * and that lesson says which teacher it is. The id with the most such lessons wins.
 */
fun teacherIdsByName(tt: Timetable, days: Collection<DaySubstitutions>): Map<String, String> {
    val planner = DayPlanner(tt)
    val votes = HashMap<String, HashMap<String, Int>>()
    for (day in days) {
        for (className in day.substitutions.map { it.className }.distinct()) {
            val classId = tt.classByShort(className)?.id ?: continue
            for (lesson in planner.forClass(classId, day.date, day)) {
                val name = lesson.teacher?.old ?: continue
                val planned = lesson.plannedTeachers.singleOrNull()?.takeIf { shortFitsName(it.short, name) } ?: continue
                votes.getOrPut(normalizeName(name)) { HashMap() }.merge(planned.id, 1, Int::plus)
            }
        }
    }
    return votes.mapValues { (_, byId) -> byId.maxBy { it.value }.key }
}

/** "KaM" fits Maciej Kania, "WB" fits Barbara Wojtanowska-Słabik: the short's capitals are initials of the name. */
fun shortFitsName(short: String, name: String): Boolean {
    val capitals = short.filter { it.isUpperCase() }.toSet()
    val initials = name.split(' ', '-').mapNotNull { it.firstOrNull()?.uppercaseChar() }.toSet()
    return capitals.size >= 2 && initials.containsAll(capitals)
}
