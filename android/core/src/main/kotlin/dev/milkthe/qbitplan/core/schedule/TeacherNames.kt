package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.DaySubstitutions
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
