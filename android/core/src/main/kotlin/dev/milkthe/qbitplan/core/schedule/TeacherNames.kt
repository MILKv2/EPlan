package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.normalizeName
import java.text.Collator
import java.util.Locale

/**
 * Imiona i nazwiska nauczycieli znane z zastępstw (nieobecni, zastępowani,
 * zastępcy), posortowane po polsku. Służy do wyboru „kim jestem” w onboardingu
 * nauczyciela, bo plan stały zawiera tylko skróty.
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
        .filter { it.contains(' ') } // „Imię Nazwisko”, odrzuca śmieci
        .distinctBy { normalizeName(it) }
        .sortedWith { a, b -> collator.compare(a.surnameFirst(), b.surnameFirst()) }
}

/** „Anna Grzebień” → „Grzebień Anna” (sortowanie po nazwisku). */
private fun String.surnameFirst(): String = substringAfterLast(' ') + " " + substringBeforeLast(' ')
