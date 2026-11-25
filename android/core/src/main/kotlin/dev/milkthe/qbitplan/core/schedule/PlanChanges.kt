package dev.milkthe.qbitplan.core.schedule

/**
 * Lessons of [after] that look different in [before]: what a push about new substitutions
 * should tell. A lesson back to normal (its substitution withdrawn) counts as a change too.
 */
fun changedLessons(before: List<PlannedLesson>, after: List<PlannedLesson>): List<PlannedLesson> {
    val seen = before.map { it.signature() }.toSet()
    return after.filter { it.signature() !in seen }
}

/**
 * Lessons of [before] with nothing left in their periods in [after], e.g. a cover handed to
 * another teacher: it just leaves the covering teacher's plan, so [changedLessons] can't see it.
 */
fun droppedLessons(before: List<PlannedLesson>, after: List<PlannedLesson>): List<PlannedLesson> =
    before.filter { old -> after.none { it.periods.overlaps(old.periods) } }

// What a student or teacher sees; the substitution row itself (wording, parse leftovers) doesn't matter.
private fun PlannedLesson.signature() = listOf(
    periods, status, subject, teacher, classroom, classNames, groupNames, movedFromPeriod, movedToPeriod, plannedTeachers.map { it.id },
)
