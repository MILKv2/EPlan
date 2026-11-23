package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.Change
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Substitution
import dev.milkthe.qbitplan.core.model.Teacher
import dev.milkthe.qbitplan.core.model.TimetableLesson

enum class LessonStatus {
    NORMAL,
    CHANGED,
    NO_SUBSTITUTE,
    CANCELLED,
    MOVED_AWAY,
    MOVED_HERE,
    CLASS_ABSENT,
    ;

    /** Whether the lesson actually happens (occupies the room, teacher and students). */
    val takesPlace: Boolean get() = this == NORMAL || this == CHANGED || this == NO_SUBSTITUTE || this == MOVED_HERE
}

/** One tile of a day plan: a regular lesson with its substitution applied, or a lesson outside the plan. */
data class PlannedLesson(
    val periods: PeriodRange,
    val status: LessonStatus,
    /** Subject shorts: `old` = planned, `new` = actual. */
    val subject: Change,
    val plannedTeachers: List<Teacher>,
    /** Full names from the substitution; `null` when there is none. */
    val teacher: Change?,
    /** Classroom shorts. */
    val classroom: Change?,
    val classNames: List<String>,
    /** Empty = whole class. */
    val groupNames: List<String>,
    val movedFromPeriod: Int? = null,
    val movedToPeriod: Int? = null,
    /** `null` for lessons outside the regular plan. */
    val lesson: TimetableLesson?,
    val substitution: Substitution?,
) {
    val substituteName: String? get() = teacher?.takeIf { it.isChanged }?.new
}
