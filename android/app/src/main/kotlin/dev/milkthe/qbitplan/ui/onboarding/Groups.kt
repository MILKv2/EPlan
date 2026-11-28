package dev.milkthe.qbitplan.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.core.model.Timetable

/**
 * Mutually exclusive groups of a class (one EduPage division). [label] null = show the options.
 * [default] is set for an optional division (religion/ethics): a student attends one or none,
 * never both, so it offers "none" instead of "all" and starts on [default].
 */
@Immutable
data class Division(val id: String, @StringRes val label: Int?, val options: List<String>, val default: String? = null) {
    val optional: Boolean get() = default != null
}

/** The group picked in [division]; `null` = all of them, or none if the division is skipped. */
fun Selection.pickIn(division: Division): String? =
    if (division.id in skippedDivisions) null else groups[division.id] ?: division.default

fun Selection.groupSelection(divisions: List<Division>): GroupSelection = GroupSelection(
    picked = divisions.mapNotNull { pickIn(it) }.toSet(),
    skipped = divisions.filter { it.id in skippedDivisions }.flatMap { it.options }.toSet(),
)

/** EduPage keeps unused template divisions (e.g. "1. Grupa"/"2. Grupa"); only divisions some lesson uses are shown. */
fun divisionsOf(tt: Timetable, classId: String): List<Division> {
    val groups = tt.groupsOfClass(classId)
    val used = tt.lessons.asSequence().filter { classId in it.classIds }.flatMap { it.groupIds }.toSet()
    val usedDivisions = groups.filter { it.id in used }.map { it.division }.toSet()
    return groups
        .filter { it.division in usedDivisions }
        .groupBy { it.division }
        .map { (division, groups) -> groups.map { it.name }.let { Division(division, divisionLabel(it), it, defaultOf(it)) } }
        .filter { it.options.size > 1 }
}

private fun defaultOf(options: List<String>): String? =
    if (divisionLabel(options) == R.string.division_religion) options.firstOrNull { it.lowercase().startsWith("relig") } ?: options.first() else null

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

/** Joined classes split by profession, e.g. "D-automatyk" / "F-informatyk", or just "grB" / "grF". */
private val ProfessionGroup = Regex("^([A-Z]-\\p{L}|gr[A-Z]$)")
