package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.core.edupage.SubstitutionsParser
import dev.milkthe.qbitplan.core.model.Change
import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Timetable
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayPlannerTest {

    private val tt: Timetable by lazy { RegularTimetableParser.parse(Fixtures.regular189, "189") }
    private val planner by lazy { DayPlanner(tt) }

    private val tuesday = LocalDate.of(2026, 11, 24)
    private fun subs(date: String): DaySubstitutions =
        SubstitutionsParser.parse(LocalDate.parse(date), Fixtures.substitutions(date))

    private fun classId(short: String) = tt.classByShort(short)!!.id

    @Test
    fun `student of group 2 sees split substitution, cancelled religion`() {
        val plan = planner.forClass(classId("1at"), tuesday, subs("2026-11-24"), groups = GroupSelection(setOf("2. Grupa")))

        val p4 = plan.single { it.periods == PeriodRange.single(4) }
        assertEquals(LessonStatus.CHANGED, p4.status)
        assertEquals(Change("PEiE", "inf"), p4.subject)
        assertEquals(Change("Wojciech Książkiewicz", "Anna Grzebień"), p4.teacher)
        assertEquals("Anna Grzebień", p4.substituteName)
        assertEquals(Change("A10", "B23"), p4.classroom)
        assertEquals(listOf("KW"), p4.plannedTeachers.map { it.short })

        val p5 = plan.single { it.periods == PeriodRange.single(5) }
        assertEquals(Change("PEiE", "jang"), p5.subject)
        assertEquals("Dorota Dziadosz", p5.substituteName)

        val pt = plan.single { it.subject.new == "PT" }
        assertEquals(PeriodRange(6, 8), pt.periods)
        assertEquals(LessonStatus.NORMAL, pt.status)
        assertEquals(listOf("2. Grupa"), pt.groupNames)
        assertNull(pt.teacher)

        val religion = plan.single { it.subject.old == "religia" }
        assertEquals(LessonStatus.CANCELLED, religion.status)

        assertTrue(plan.none { it.groupNames == listOf("1. Grupa") }, "group 1 hidden")
    }

    @Test
    fun `student attending neither religion nor ethics sees neither`() {
        val neither = GroupSelection(setOf("2. Grupa"), skipped = setOf("Religia", "Etyka"))
        for (substitutions in listOf(null, subs("2026-11-24"))) {
            val plan = planner.forClass(classId("1at"), tuesday, substitutions, groups = neither)
            assertTrue(plan.none { it.subject.old in setOf("religia", "etyka") || it.subject.new in setOf("religia", "etyka") })
            assertTrue(plan.none { it.groupNames == listOf("1. Grupa") }, "other divisions still filtered")
        }
        val all = planner.forClass(classId("1at"), tuesday, null)
        assertTrue(all.any { it.subject.old == "religia" }, "fixture has religion on Tuesday")
    }

    @Test
    fun `student of group 1 keeps the double lesson unchanged`() {
        val plan = planner.forClass(classId("1at"), tuesday, subs("2026-11-24"), groups = GroupSelection(setOf("1. Grupa")))
        val peie = plan.single { it.subject.old == "PEiE" }
        assertEquals(PeriodRange(4, 5), peie.periods)
        assertEquals(LessonStatus.NORMAL, peie.status)
        assertEquals("C201", peie.classroom?.new)
    }

    @Test
    fun `moved and cancelled lessons in 2ct`() {
        val plan = planner.forClass(classId("2ct"), tuesday, subs("2026-11-24"))

        val away = plan.single { it.status == LessonStatus.MOVED_AWAY }
        assertEquals(PeriodRange.single(0), away.periods)
        assertEquals(4, away.movedToPeriod)

        val here = plan.single { it.status == LessonStatus.MOVED_HERE }
        assertEquals(PeriodRange.single(4), here.periods)
        assertEquals(0, here.movedFromPeriod)
        assertEquals("matem", here.subject.new)
        assertEquals("A56", here.classroom?.new)
        assertEquals("BR", here.plannedTeachers.single().short)

        assertEquals(LessonStatus.CANCELLED, plan.single { it.subject.old == "Eob" }.status)
        assertEquals(Change("hist", "matem"), plan.single { it.periods.first == 6 }.subject)
    }

    @Test
    fun `no substitute`() {
        val wednesday = LocalDate.of(2026, 11, 25)
        val gw = planner.forClass(classId("1al"), wednesday, subs("2026-11-25")).single { it.periods.first == 3 }
        assertEquals(LessonStatus.NO_SUBSTITUTE, gw.status)
        assertEquals(Change("Andrzej Frączek", null), gw.teacher)
        assertNull(gw.substituteName)
        assertTrue(gw.status.takesPlace, "students still attend")
    }

    @Test
    fun `absent class marks the whole day`() {
        // 2026-10-15 is a Thursday; an absent class affects every lesson.
        val plan = planner.forClass(classId("1dt"), LocalDate.of(2026, 10, 15), subs("2026-10-15"))
        assertTrue(plan.isNotEmpty())
        assertTrue(plan.all { it.status == LessonStatus.CLASS_ABSENT }, plan.toString())
    }

    @Test
    fun `no substitutions means plain timetable, weekend is empty`() {
        val plain = planner.forClass(classId("1at"), tuesday, substitutions = null)
        assertTrue(plain.all { it.status == LessonStatus.NORMAL })
        assertEquals(listOf(1, 2, 3, 4, 4, 6, 6, 9), plain.map { it.periods.first })
        assertTrue(planner.forClass(classId("1at"), LocalDate.of(2026, 11, 28), subs("2026-11-21")).isEmpty())
    }

    @Test
    fun `week filter is supported`() {
        // Every lesson in this school is in both weeks.
        val all = planner.forClass(classId("1at"), tuesday, null)
        assertEquals(all, planner.forClass(classId("1at"), tuesday, null, weekIndex = 0))
        assertEquals(all, planner.forClass(classId("1at"), tuesday, null, weekIndex = 1))
        assertTrue(planner.forClass(classId("1at"), tuesday, null, weekIndex = 5).isEmpty())
    }

    @Test
    fun `teacher view includes substitutions they take`() {
        val plan = planner.forTeacher("-161", "Anna Grzebień", tuesday, subs("2026-11-24"))
        val extra = plan.single { it.periods == PeriodRange.single(4) }
        assertEquals(LessonStatus.CHANGED, extra.status)
        assertEquals(listOf("1at"), extra.classNames)
        assertEquals("inf", extra.subject.new)
        assertEquals("B23", extra.classroom?.new)
        assertEquals(listOf(1, 2, 4, 6, 7), plan.map { it.periods.first })
        assertEquals(listOf("4ct"), plan.single { it.periods == PeriodRange(2, 3) }.classNames)
    }

    @Test
    fun `teacher view shows own lessons taken over by substitutes`() {
        val plan = planner.forTeacher("-39", "Wojciech Książkiewicz", tuesday, subs("2026-11-24"))
        assertEquals(listOf("Anna Grzebień", "Dorota Dziadosz"), plan.filter { it.classNames == listOf("1at") }.map { it.substituteName })
    }

    @Test
    fun `teacher view without full name shows only planned lessons`() {
        val plan = planner.forTeacher("-161", null, tuesday, subs("2026-11-24"))
        assertTrue(plan.none { it.periods == PeriodRange.single(4) })
    }

    @Test
    fun `joint lesson appears once in teacher view with all classes`() {
        // Moskal's (MP) joint PE: 4bl Dziew1 + 4fl Dziew2, periods 1-2
        val mp = tt.lessons.single { it.day == 1 && it.periods == PeriodRange(1, 2) && tt.classes[it.classIds.first()]?.short == "4bl" && "Dziew1" in it.groupIds.map { g -> tt.groups[g]!!.name } }.teacherIds.single()
        val wf = planner.forTeacher(mp, "Piotr Moskal", tuesday, subs("2026-11-24")).filter { it.periods.first == 1 }
        assertEquals(1, wf.size, wf.toString())
        assertEquals(setOf("4bl", "4fl"), wf.single().classNames.toSet())
    }

    @Test
    fun `classroom view follows room changes`() {
        val b23 = tt.classroomByShort("B23")!!.id
        val inB23 = planner.forClassroom(b23, tuesday, subs("2026-11-24"))
        assertTrue(inB23.any { it.periods == PeriodRange.single(4) && it.classNames == listOf("1at") && it.subject.new == "inf" })

        val a10 = tt.classroomByShort("A10")!!.id
        val inA10 = planner.forClassroom(a10, tuesday, subs("2026-11-24"))
        val movedOut = inA10.single { it.periods.first == 4 && it.classNames == listOf("1at") && it.groupNames == listOf("2. Grupa") }
        assertEquals(Change("A10", "B23"), movedOut.classroom)
    }

    @Test
    fun `free classrooms account for changes and unavailable rooms`() {
        val s = subs("2026-11-24")
        val free4 = planner.freeClassrooms(tuesday, 4, s).map { it.short }
        assertTrue("A10" in free4, "A10 freed by a room change")
        assertTrue("B23" !in free4, "B23 taken by a substitution")
        assertTrue("Brama Gł." !in free4 && "Park. B" !in free4, "duty spots are not classrooms")
        val occupancy = planner.classroomOccupancy(tuesday, 4, s).associateBy { it.room.short }
        assertEquals(listOf("1at"), occupancy.getValue("B23").classNames)
        assertTrue(occupancy.getValue("A10").isFree)
        assertTrue(planner.classroomOccupancy(tuesday, 2, s).single { it.room.short == "A24" }.unavailable)
        assertTrue("A24" !in planner.freeClassrooms(tuesday, 2, s).map { it.short }, "A24 unavailable in period 2")
        assertTrue(planner.freeClassrooms(tuesday, 4, null).map { it.short }.let { "A10" !in it && "B23" in it })
    }
}
