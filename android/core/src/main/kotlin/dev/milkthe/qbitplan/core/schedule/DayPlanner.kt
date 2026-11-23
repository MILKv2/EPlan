package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.Change
import dev.milkthe.qbitplan.core.model.Classroom
import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Substitution
import dev.milkthe.qbitplan.core.model.SubstitutionType
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.model.TimetableLesson
import java.time.LocalDate

/**
 * Overlays a day's substitutions on the regular timetable.
 *
 * Lessons are split into single periods first, because a substitution can
 * cover only part of a multi-period lesson (e.g. period 4 of 4-5). Rows that
 * match nothing become their own tiles so no information is lost. Adjacent
 * periods of the same lesson in the same state are merged back at the end.
 *
 * Substitutions are published per class, so teacher and classroom views are
 * built from the per-class results.
 */
class DayPlanner(private val tt: Timetable) {

    // The classrooms table also lists duty spots (gates, car parks); only rooms with lessons count.
    private val teachingRooms by lazy {
        val used = tt.lessons.flatMap { it.classroomIds }.toSet()
        tt.classrooms.values.filter { it.id in used }.sortedBy { it.short }
    }

    /** [selectedGroupNames] `null` = all groups of the class. */
    fun forClass(
        classId: String,
        date: LocalDate,
        substitutions: DaySubstitutions?,
        selectedGroupNames: Set<String>? = null,
        weekIndex: Int? = null,
    ): List<PlannedLesson> {
        val slots = resolveClass(classId, date, substitutions, weekIndex)
        if (selectedGroupNames == null) return merge(slots)
        val selectedIds = tt.groupIdsForNames(classId, selectedGroupNames)
        return merge(
            slots.filter { slot ->
                if (slot.lesson != null) tt.isForClass(slot.lesson, classId, selectedIds)
                else isRelevantGroupNames(classId, slot.groupNames, selectedIds)
            },
        )
    }

    /**
     * The teacher's own lessons (including ones someone else covers) plus lessons
     * they cover. [fullName] links plan shorts to the full names used in substitutions.
     */
    fun forTeacher(
        teacherId: String,
        fullName: String?,
        date: LocalDate,
        substitutions: DaySubstitutions?,
        weekIndex: Int? = null,
    ): List<PlannedLesson> {
        val day = date.schoolDayIndex()
        val classIds = tt.lessons.filter { teacherId in it.teacherIds && it.day == day }.flatMap { it.classIds }.toMutableSet()
        if (fullName != null) {
            substitutions?.substitutions.orEmpty()
                .filter { it.involvesTeacher(fullName) }
                .mapNotNullTo(classIds) { tt.classByShort(it.className)?.id }
        }
        val slots = classIds.flatMap { resolveClass(it, date, substitutions, weekIndex) }.filter { slot ->
            teacherId in slot.lesson?.teacherIds.orEmpty() ||
                (fullName != null && slot.substitution?.involvesTeacher(fullName) == true)
        }
        return merge(dedupeAcrossClasses(slots))
    }

    /** Lessons held in the room after changes, plus lessons moved out of it. */
    fun forClassroom(
        classroomId: String,
        date: LocalDate,
        substitutions: DaySubstitutions?,
        weekIndex: Int? = null,
    ): List<PlannedLesson> {
        val short = tt.classrooms[classroomId]?.short ?: return emptyList()
        val slots = allSlots(date, substitutions, weekIndex).filter { slot ->
            short in slot.classroom?.old.splitRooms() || short in slot.classroom?.new.splitRooms()
        }
        return merge(slots)
    }

    /** Teaching rooms with no lesson after changes and not listed as unavailable. */
    fun freeClassrooms(
        date: LocalDate,
        period: Int,
        substitutions: DaySubstitutions?,
        weekIndex: Int? = null,
    ): List<Classroom> {
        val occupied = allSlots(date, substitutions, weekIndex)
            .filter { it.period == period && it.status.takesPlace }
            .flatMap { it.classroom?.new.splitRooms() }
            .toMutableSet()
        substitutions?.unavailableClassrooms.orEmpty()
            .filter { it.periods == null || period in it.periods }
            .mapTo(occupied) { it.name }
        return teachingRooms.filter { it.short !in occupied }
    }

    private data class Slot(
        val period: Int,
        val status: LessonStatus,
        val subject: Change,
        val teacher: Change?,
        val classroom: Change?,
        val classNames: List<String>,
        val groupNames: List<String>,
        val movedFromPeriod: Int?,
        val movedToPeriod: Int?,
        val lesson: TimetableLesson?,
        val substitution: Substitution?,
    )

    private fun resolveClass(
        classId: String,
        date: LocalDate,
        substitutions: DaySubstitutions?,
        weekIndex: Int?,
    ): List<Slot> {
        val schoolClass = tt.classes[classId] ?: return emptyList()
        val day = date.schoolDayIndex()

        val slots = tt.lessons
            .filter { classId in it.classIds && it.day == day && (weekIndex == null || weekIndex in it.weeks) }
            .flatMap { lesson -> (lesson.periods.first..lesson.periods.last).map { baseSlot(lesson, it, classId) } }
            .toMutableList()
        val matched = BooleanArray(slots.size)
        val extra = mutableListOf<Slot>()

        val rows = substitutions?.substitutions.orEmpty().filter { it.className == schoolClass.short }
        // MOVED_HERE rows look up their source lesson, so handle them last.
        for (row in rows.sortedBy { it.type == SubstitutionType.MOVED_HERE }) {
            when (row.type) {
                SubstitutionType.CLASS_ABSENT -> slots.indices
                    .filter { row.periods == null || slots[it].period in row.periods }
                    .forEach { i ->
                        slots[i] = slots[i].copy(status = LessonStatus.CLASS_ABSENT, substitution = row)
                        matched[i] = true
                    }

                SubstitutionType.MOVED_HERE -> {
                    val periods = row.periods ?: continue
                    val source = slots.firstOrNull {
                        it.period == row.movedFromPeriod && it.lesson != null && it.subject.old == row.subject?.old
                    }
                    for (p in periods.first..periods.last) extra += movedHereSlot(row, p, source, schoolClass.short)
                }

                else -> {
                    val periods = row.periods
                        // Rare row without periods: applies to every lesson of the subject.
                        ?: PeriodRange(slots.minOfOrNull { it.period } ?: continue, slots.maxOf { it.period })
                    for (p in periods.first..periods.last) {
                        val targets = findTargets(slots, matched, p, row, classId)
                        if (targets.isEmpty()) {
                            extra += unmatchedSlot(row, p, schoolClass.short)
                        } else {
                            targets.forEach { i ->
                                slots[i] = applyRow(slots[i], row)
                                matched[i] = true
                            }
                        }
                    }
                }
            }
        }
        return slots + extra
    }

    private fun findTargets(
        slots: List<Slot>,
        matched: BooleanArray,
        period: Int,
        row: Substitution,
        classId: String,
    ): List<Int> {
        val atPeriod = slots.indices.filter { slots[it].period == period && slots[it].lesson != null }
        // Prefer slots no other row has claimed yet.
        val candidates = atPeriod.filter { !matched[it] }.ifEmpty { atPeriod }
        fun groupsOk(i: Int) = row.groups.isEmpty() || slots[i].groupNames.any { it in row.groups } ||
            (slots[i].groupNames.isEmpty() && tt.groupsOfClass(classId).none { it.name in row.groups })
        val oldSubject = row.subject?.old
        val bySubject = candidates.filter { slots[it].subject.old == oldSubject && groupsOk(it) }
        if (row.groups.isNotEmpty() && bySubject.size > 1) {
            // EduPage lists the group names of the WHOLE joint lesson, across classes:
            // "Dziew1, Dziew2" in 4fl means 4bl/Dziew1 + 4fl/Dziew2. Prefer the lesson
            // whose group set matches exactly.
            val rowGroups = row.groups.toSet()
            val exact = bySubject.filter { lessonWideGroupNames(slots[it].lesson!!) == rowGroups }
            if (exact.isNotEmpty()) return exact
        }
        if (bySubject.isNotEmpty()) return bySubject
        // Subject mismatch: only accept an unambiguous match by groups.
        return candidates.filter { groupsOk(it) }.takeIf { it.size == 1 }.orEmpty()
    }

    private fun baseSlot(lesson: TimetableLesson, period: Int, classId: String): Slot {
        val rooms = lesson.classroomIds.mapNotNull { tt.classrooms[it]?.short }.joinToString(", ")
        return Slot(
            period = period,
            status = LessonStatus.NORMAL,
            subject = Change.unchanged(tt.subjects[lesson.subjectId]?.short.orEmpty()),
            teacher = null,
            classroom = rooms.takeIf { it.isNotEmpty() }?.let { Change.unchanged(it) },
            classNames = lesson.classIds.mapNotNull { tt.classes[it]?.short },
            groupNames = groupNamesOf(lesson, classId),
            movedFromPeriod = null,
            movedToPeriod = null,
            lesson = lesson,
            substitution = null,
        )
    }

    private fun applyRow(slot: Slot, row: Substitution): Slot = slot.copy(
        status = when (row.type) {
            SubstitutionType.CANCELLED -> LessonStatus.CANCELLED
            SubstitutionType.MOVED_AWAY -> LessonStatus.MOVED_AWAY
            SubstitutionType.NO_SUBSTITUTE -> LessonStatus.NO_SUBSTITUTE
            else -> LessonStatus.CHANGED
        },
        subject = Change(slot.subject.old, row.subject?.new ?: slot.subject.new),
        teacher = row.teacher,
        classroom = row.classroom?.let { Change(slot.classroom?.old ?: it.old, it.new) } ?: slot.classroom,
        movedToPeriod = row.movedToPeriod,
        substitution = row,
    )

    private fun movedHereSlot(row: Substitution, period: Int, source: Slot?, className: String) = Slot(
        period = period,
        status = LessonStatus.MOVED_HERE,
        subject = Change.unchanged(row.subject?.new ?: source?.subject?.old.orEmpty()),
        teacher = row.teacher,
        classroom = row.classroom ?: source?.classroom,
        classNames = source?.classNames ?: listOf(className),
        groupNames = source?.groupNames ?: row.groups,
        movedFromPeriod = row.movedFromPeriod,
        movedToPeriod = null,
        lesson = source?.lesson,
        substitution = row,
    )

    private fun unmatchedSlot(row: Substitution, period: Int, className: String) = Slot(
        period = period,
        status = when (row.type) {
            SubstitutionType.CANCELLED -> LessonStatus.CANCELLED
            SubstitutionType.MOVED_AWAY -> LessonStatus.MOVED_AWAY
            SubstitutionType.NO_SUBSTITUTE -> LessonStatus.NO_SUBSTITUTE
            else -> LessonStatus.CHANGED
        },
        subject = row.subject ?: Change(null, null),
        teacher = row.teacher,
        classroom = row.classroom,
        classNames = listOf(className),
        groupNames = row.groups,
        movedFromPeriod = row.movedFromPeriod,
        movedToPeriod = row.movedToPeriod,
        lesson = null,
        substitution = row,
    )

    private fun allSlots(date: LocalDate, substitutions: DaySubstitutions?, weekIndex: Int?): List<Slot> =
        dedupeAcrossClasses(tt.classes.keys.flatMap { resolveClass(it, date, substitutions, weekIndex) })

    /** Joint lessons show up once per class; keep one slot (preferably with a substitution) and union the class names. */
    private fun dedupeAcrossClasses(slots: List<Slot>): List<Slot> {
        val (fromPlan, outsidePlan) = slots.partition { it.lesson != null && it.status != LessonStatus.MOVED_HERE }
        val deduped = fromPlan.groupBy { it.lesson!!.cardId to it.period }.values.map { same ->
            val best = same.firstOrNull { it.substitution != null } ?: same.first()
            best.copy(classNames = same.flatMap { it.classNames }.distinct())
        }
        return deduped + outsidePlan.distinct()
    }

    private fun merge(slots: List<Slot>): List<PlannedLesson> {
        val sorted = slots.sortedWith(compareBy({ it.period }, { it.groupNames.joinToString() }, { it.subject.new }))
        val result = mutableListOf<PlannedLesson>()
        val open = mutableListOf<Pair<Slot, IntRange>>()
        for (slot in sorted) {
            val index = open.indexOfFirst { (last, range) -> range.last + 1 == slot.period && last.sameState(slot) }
            if (index >= 0) {
                val (_, range) = open[index]
                open[index] = slot to (range.first..slot.period)
            } else {
                open += slot to (slot.period..slot.period)
            }
        }
        for ((slot, range) in open) result += slot.toPlanned(PeriodRange(range.first, range.last))
        return result.sortedWith(compareBy({ it.periods.first }, { it.groupNames.joinToString() }, { it.subject.new }))
    }

    private fun Slot.sameState(other: Slot) =
        copy(period = 0) == other.copy(period = 0) && lesson != null && other.lesson != null

    private fun Slot.toPlanned(periods: PeriodRange) = PlannedLesson(
        periods = periods,
        status = status,
        subject = subject,
        plannedTeachers = lesson?.teacherIds?.mapNotNull { tt.teachers[it] }.orEmpty(),
        teacher = teacher,
        classroom = classroom,
        classNames = classNames,
        groupNames = groupNames,
        movedFromPeriod = movedFromPeriod,
        movedToPeriod = movedToPeriod,
        lesson = lesson,
        substitution = substitution,
    )

    /** Group names within this class; "Cała klasa" → empty. */
    private fun groupNamesOf(lesson: TimetableLesson, classId: String): List<String> =
        lesson.groupIds.mapNotNull { tt.groups[it] }
            .filter { it.classId == classId && !it.entireClass }
            .map { it.name }

    /** Group names of the whole lesson across classes. */
    private fun lessonWideGroupNames(lesson: TimetableLesson): Set<String> =
        lesson.groupIds.mapNotNull { tt.groups[it] }.filter { !it.entireClass }.map { it.name }.toSet()

    private fun isRelevantGroupNames(classId: String, names: List<String>, selectedIds: Set<String>): Boolean {
        if (names.isEmpty()) return true
        val classGroups = tt.groups.values.filter { it.classId == classId }
        val rowGroups = classGroups.filter { it.name in names }
        if (rowGroups.isEmpty() || rowGroups.any { it.entireClass }) return true
        val selectedDivisions = selectedIds.mapNotNull { tt.groups[it]?.division }.toSet()
        return rowGroups.any { it.id in selectedIds || it.division !in selectedDivisions }
    }

    private fun String?.splitRooms(): List<String> =
        this?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}

/** 0 = Monday … 6 = Sunday. */
fun LocalDate.schoolDayIndex(): Int = dayOfWeek.value - 1
