package dev.milkthe.qbitplan.ui.common

import androidx.compose.runtime.Immutable
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.core.schedule.PlannedLesson
import java.time.LocalTime

/** Display-ready lesson tile; `old*` fields are set only when the value changed. */
@Immutable
data class LessonItem(
    val key: String,
    val periods: PeriodRange,
    val start: LocalTime?,
    val end: LocalTime?,
    val subject: String,
    val subjectShort: String,
    val oldSubject: String?,
    val teacher: String?,
    val oldTeacher: String?,
    val room: String?,
    val oldRoom: String?,
    val classes: String?,
    val groups: String?,
    val status: LessonStatus,
    val movedFrom: Int?,
    val movedTo: Int?,
)

class LessonItemMapper(private val tt: Timetable) {

    private val subjectNames = tt.subjects.values.associate { it.short to it.name }

    fun subjectName(short: String?): String = short?.let { subjectNames[it] ?: it }.orEmpty()

    /** [viewerTeacherId]: the teacher looking at their own plan; their short is not repeated on every tile. */
    fun map(lessons: List<PlannedLesson>, showClasses: Boolean, viewerTeacherId: String? = null): List<LessonItem> =
        lessons.mapIndexed { index, lesson -> map(index, lesson, showClasses, viewerTeacherId) }

    private fun map(index: Int, l: PlannedLesson, showClasses: Boolean, viewerTeacherId: String?): LessonItem {
        val removed = !l.status.takesPlace
        val subjectShort = if (removed) l.subject.old ?: l.subject.new else l.subject.new ?: l.subject.old
        val subjectChanged = !removed && l.subject.isChanged && l.subject.old != null

        val planned = l.plannedTeachers.filter { it.id != viewerTeacherId }.joinToString(", ") { it.short }.ifEmpty { null }
        val change = l.teacher
        val teacher: String?
        val oldTeacher: String?
        if (change != null && change.isChanged) {
            teacher = change.new
            oldTeacher = change.old ?: planned
        } else {
            teacher = planned ?: change?.old?.takeIf { viewerTeacherId == null }
            oldTeacher = null
        }

        val room = l.classroom
        val roomChanged = !removed && room != null && room.isChanged

        return LessonItem(
            key = "$index:${l.lesson?.cardId}:${l.periods}",
            periods = l.periods,
            start = tt.period(l.periods.first)?.start,
            end = tt.period(l.periods.last)?.end,
            subject = subjectName(subjectShort),
            subjectShort = subjectShort.orEmpty(),
            oldSubject = if (subjectChanged) subjectName(l.subject.old) else null,
            teacher = teacher,
            oldTeacher = oldTeacher,
            room = if (removed) room?.old ?: room?.new else room?.new,
            oldRoom = if (roomChanged) room.old else null,
            classes = if (showClasses) l.classNames.joinToString(", ").ifEmpty { null } else null,
            groups = l.groupNames.joinToString(", ").ifEmpty { null },
            status = l.status,
            movedFrom = l.movedFromPeriod,
            movedTo = l.movedToPeriod,
        )
    }
}
