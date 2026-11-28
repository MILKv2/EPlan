package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.core.edupage.SubstitutionsParser
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.core.model.Timetable
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanChangesTest {

    private val tt: Timetable by lazy { RegularTimetableParser.parse(Fixtures.regular189, "189") }
    private val planner by lazy { DayPlanner(tt) }
    private val tuesday = LocalDate.of(2026, 11, 24)
    private val subs by lazy { SubstitutionsParser.parse(tuesday, Fixtures.substitutions("2026-11-24")) }
    private val classId by lazy { tt.classByShort("1at")!!.id }

    @Test
    fun `newly published substitutions are the changes, in the student's groups`() {
        val groups = GroupSelection(setOf("2. Grupa"))
        val changes = changedLessons(
            before = planner.forClass(classId, tuesday, null, groups),
            after = planner.forClass(classId, tuesday, subs, groups),
        )
        assertEquals(listOf(4, 5, 9), changes.map { it.periods.first })
        assertEquals(listOf(LessonStatus.CHANGED, LessonStatus.CHANGED, LessonStatus.CANCELLED), changes.map { it.status })
    }

    @Test
    fun `the other group hears only about what touches it`() {
        val groups = GroupSelection(setOf("1. Grupa"))
        val changes = changedLessons(planner.forClass(classId, tuesday, null, groups), planner.forClass(classId, tuesday, subs, groups))
        assertTrue(changes.none { it.periods.first in 4..5 })
    }

    @Test
    fun `withdrawn substitutions bring lessons back`() {
        val changes = changedLessons(before = planner.forClass(classId, tuesday, subs), after = planner.forClass(classId, tuesday, null))
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.all { it.status == LessonStatus.NORMAL })
    }

    @Test
    fun `the same plan has no changes`() {
        assertEquals(emptyList(), changedLessons(planner.forClass(classId, tuesday, subs), planner.forClass(classId, tuesday, subs)))
    }

    @Test
    fun `a cover handed to someone else leaves the first teacher's plan`() {
        val teacher = tt.teachers.values.single { it.short == "AG" }
        val handedOver = subs.copy(
            substitutions = subs.substitutions.map { row ->
                if (row.teacher?.new == "Anna Grzebień") row.copy(teacher = row.teacher!!.copy(new = "Dorota Dziadosz")) else row
            },
        )
        val before = planner.forTeacher(teacher.id, "Anna Grzebień", tuesday, subs)
        val after = planner.forTeacher(teacher.id, "Anna Grzebień", tuesday, handedOver)
        assertEquals(emptyList(), changedLessons(before, after))
        val dropped = droppedLessons(before, after).single()
        assertEquals(4, dropped.periods.first)
        assertEquals(listOf("1at"), dropped.classNames)
    }

    @Test
    fun `a lesson that only changed is not dropped`() {
        assertEquals(emptyList(), droppedLessons(planner.forClass(classId, tuesday, null), planner.forClass(classId, tuesday, subs)))
        assertEquals(emptyList(), droppedLessons(planner.forClass(classId, tuesday, subs), planner.forClass(classId, tuesday, null)))
    }
}
