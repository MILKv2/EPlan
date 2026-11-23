package dev.milkthe.qbitplan.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.Timetable

/** Mutually exclusive groups of a class (one EduPage division). [label] null = show the options. */
@Immutable
data class Division(val id: String, @StringRes val label: Int?, val options: List<String>)

fun divisionsOf(tt: Timetable, classId: String): List<Division> =
    tt.groupsOfClass(classId)
        .groupBy { it.division }
        .map { (division, groups) -> groups.map { it.name } .let { Division(division, divisionLabel(it), it) } }
        .filter { it.options.size > 1 }

/** EduPage divisions have no names; guess one from the group names. */
@StringRes
private fun divisionLabel(options: List<String>): Int? {
    val all = options.joinToString(" ").lowercase()
    return when {
        options.all { "grupa" in it.lowercase() } -> R.string.division_split
        options.all { it.lowercase().startsWith("ang") } -> R.string.division_english
        options.all { it.lowercase().startsWith("niem") } -> R.string.division_german
        "chłop" in all || "dziew" in all -> R.string.division_pe
        "religia" in all || "etyka" in all -> R.string.division_religion
        "niem" in all || "ros" in all || "fra" in all -> R.string.division_language
        else -> null
    }
}
