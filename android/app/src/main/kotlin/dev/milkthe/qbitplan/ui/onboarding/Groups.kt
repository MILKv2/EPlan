package dev.milkthe.qbitplan.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.Timetable

/** Mutually exclusive groups of a class (one EduPage division). [label] null = show the options. */
@Immutable
data class Division(val id: String, @StringRes val label: Int?, val options: List<String>)

/** EduPage keeps unused template divisions (e.g. "1. Grupa"/"2. Grupa"); only divisions some lesson uses are shown. */
fun divisionsOf(tt: Timetable, classId: String): List<Division> {
    val groups = tt.groupsOfClass(classId)
    val used = tt.lessons.asSequence().filter { classId in it.classIds }.flatMap { it.groupIds }.toSet()
    val usedDivisions = groups.filter { it.id in used }.map { it.division }.toSet()
    return groups
        .filter { it.division in usedDivisions }
        .groupBy { it.division }
        .map { (division, groups) -> groups.map { it.name } .let { Division(division, divisionLabel(it), it) } }
        .filter { it.options.size > 1 }
}

/** EduPage divisions have no names; guess one from the group names. */
@StringRes
private fun divisionLabel(options: List<String>): Int? {
    val all = options.joinToString(" ").lowercase()
    return when {
        options.all { "grupa" in it.lowercase() } -> R.string.division_split
        options.all { ProfessionGroup.containsMatchIn(it) } -> R.string.division_profession
        options.all { it.lowercase().startsWith("ang") } -> R.string.division_english
        options.all { it.lowercase().startsWith("niem") } -> R.string.division_german
        "chłop" in all || "dziew" in all -> R.string.division_pe
        "religia" in all || "etyka" in all -> R.string.division_religion
        "niem" in all || "ros" in all || "fra" in all -> R.string.division_language
        else -> null
    }
}

/** Joined classes split by profession, e.g. "D-automatyk" / "F-informatyk". */
private val ProfessionGroup = Regex("^[A-Z]-\\p{L}")
