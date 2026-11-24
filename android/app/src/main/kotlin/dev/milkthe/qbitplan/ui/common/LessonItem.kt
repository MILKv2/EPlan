package dev.milkthe.qbitplan.ui.common

import androidx.compose.runtime.Immutable
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.core.schedule.PlannedLesson
import java.time.LocalTime

/** Extra line on a lesson tile; formatted by the UI. */
sealed interface LessonNote {
    data class MovedFrom(val period: Int) : LessonNote
    data class MovedTo(val period: Int) : LessonNote
    /** The viewing teacher covers for [name]. */
    data class Covering(val name: String) : LessonNote
}

/** Display-ready lesson tile; `old*` fields are set only when the value changed. */
@Immutable
data class LessonItem(
    val key: String,
    val periods: PeriodRange,
    val start: LocalTime?,
    val end: LocalTime?,
    val title: String,
    val shortTitle: String,
    val oldTitle: String?,
    val oldSubjectShort: String?,
    val subjectShort: String,
    val classes: String?,
    val room: String?,
    val oldRoom: String?,
    val who: String?,
    val oldWho: String?,
    val groups: String?,
    val note: LessonNote?,
    val status: LessonStatus,
    /** Full subject names for the details sheet; tiles may show an acronym. */
    val subjectName: String,
    val oldSubjectName: String?,
    /** The substitution as EduPage words it. */
    val info: String?,
) {
    val isCovering: Boolean get() = note is LessonNote.Covering
}

class LessonItemMapper(private val tt: Timetable) {

    private val subjectNames = tt.subjects.values.associate { it.short to it.displayName }
    private val fullSubjectNames = tt.subjects.values.associate { it.short to it.name }

    fun subjectName(short: String?): String = short?.let { subjectNames[it] ?: it }.orEmpty()

    private fun fullSubjectName(short: String?): String = short?.let { fullSubjectNames[it] ?: it }.orEmpty()

    /**
     * [showClasses]: prefix titles with the class (teacher and room views).
     * [viewerTeacherId]: the teacher looking at their own plan; their short is not repeated
     * and lessons they cover are marked.
     */
    fun map(lessons: List<PlannedLesson>, showClasses: Boolean, viewerTeacherId: String? = null): List<LessonItem> =
        lessons.mapIndexed { index, lesson -> map(index, lesson, showClasses, viewerTeacherId) }

    private fun map(index: Int, l: PlannedLesson, showClasses: Boolean, viewerTeacherId: String?): LessonItem {
        val removed = !l.status.takesPlace
        val subjectShort = (if (removed) l.subject.old ?: l.subject.new else l.subject.new ?: l.subject.old).orEmpty()
        val subjectChanged = !removed && l.subject.isChanged && l.subject.old != null
        val classes = l.classNames.joinToString(", ").ifEmpty { null }

        val viewerPlanned = viewerTeacherId != null && l.plannedTeachers.any { it.id == viewerTeacherId }
        val covering = viewerTeacherId != null && !viewerPlanned && l.substitution != null
        val planned = l.plannedTeachers.filter { it.id != viewerTeacherId }.joinToString(", ") { it.short }.ifEmpty { null }
        val change = l.teacher
        var who: String? = null
        var oldWho: String? = null
        when {
            covering -> Unit
            change != null && change.isChanged -> {
                who = change.new
                oldWho = if (viewerPlanned) null else change.old ?: planned
            }
            viewerTeacherId == null -> who = planned ?: change?.old
            else -> who = planned
        }

        val note = when {
            covering -> change?.old?.let { LessonNote.Covering(it) }
            l.movedFromPeriod != null -> LessonNote.MovedFrom(l.movedFromPeriod!!)
            l.movedToPeriod != null -> LessonNote.MovedTo(l.movedToPeriod!!)
            else -> null
        }

        val room = l.classroom
        val roomChanged = !removed && room != null && room.isChanged
        val name = subjectName(subjectShort)

        return LessonItem(
            key = "$index:${l.lesson?.cardId}:${l.periods}",
            periods = l.periods,
            start = tt.period(l.periods.first)?.start,
            end = tt.period(l.periods.last)?.end,
            title = if (showClasses && classes != null) "$classes · $name" else name,
            shortTitle = if (showClasses && classes != null) "$classes · $subjectShort" else subjectShort,
            oldTitle = if (subjectChanged) subjectName(l.subject.old) else null,
            oldSubjectShort = if (subjectChanged) l.subject.old else null,
            subjectShort = subjectShort,
            classes = classes,
            room = if (removed) room?.old ?: room?.new else room?.new,
            oldRoom = if (roomChanged) room.old else null,
            who = who,
            oldWho = oldWho,
            groups = l.groupNames.withoutSubject(name, subjectShort),
            note = note,
            status = l.status,
            subjectName = fullSubjectName(subjectShort),
            oldSubjectName = if (subjectChanged) fullSubjectName(l.subject.old) else null,
            info = l.substitution?.infoText?.ifBlank { null },
        )
    }
}

/** Groups named after their subject (e.g. "Religia") would only repeat the title. */
internal fun List<String>.withoutSubject(vararg subject: String?): String? =
    filterNot { group -> subject.any { it != null && group.equals(it, ignoreCase = true) } }.joinToString(", ").ifEmpty { null }
