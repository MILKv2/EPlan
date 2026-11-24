package dev.milkthe.qbitplan.core.schedule

/**
 * Lessons of [after] that look different in [before]: what a push about new substitutions
 * should tell. A lesson back to normal (its substitution withdrawn) counts as a change too.
 */
fun changedLessons(before: List<PlannedLesson>, after: List<PlannedLesson>): List<PlannedLesson> {
    val seen = before.map { it.signature() }.toSet()
    return after.filter { it.signature() !in seen }
}

// What a student or teacher sees; the substitution row itself (wording, parse leftovers) doesn't matter.
private fun PlannedLesson.signature() = listOf(
    periods, status, subject, teacher, classroom, classNames, groupNames, movedFromPeriod, movedToPeriod, plannedTeachers.map { it.id },
)
