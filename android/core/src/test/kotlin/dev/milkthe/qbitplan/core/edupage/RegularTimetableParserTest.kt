package dev.milkthe.qbitplan.core.edupage

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.model.TimetableLesson
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegularTimetableParserTest {

    private val tt: Timetable by lazy { RegularTimetableParser.parse(Fixtures.regular189, "189") }

    @Test
    fun `parses metadata`() {
        assertEquals("189", tt.ttNum)
        assertTrue(tt.schoolName.isNotBlank())
        assertEquals("Ważność: 21.11.2026-31.12.2026", tt.validityText)
    }

    @Test
    fun `parses periods 0 to 10 with bell times`() {
        assertEquals((0..10).toList(), tt.periods.map { it.number })
        assertEquals(LocalTime.of(7, 5), tt.period(0)!!.start)
        assertEquals(LocalTime.of(7, 50), tt.period(0)!!.end)
        assertEquals(LocalTime.of(15, 35), tt.period(10)!!.start)
        assertEquals(LocalTime.of(16, 20), tt.period(10)!!.end)
    }

    @Test
    fun `parses dictionaries`() {
        assertEquals(listOf("Pn", "Wt", "Śr", "Czw", "Pi"), tt.days.map { it.short })
        assertEquals(listOf("A", "B"), tt.weeks.map { it.short })
        assertEquals(38, tt.classes.size)
        assertEquals(68, tt.classrooms.size)
        assertEquals(86, tt.subjects.size)
        assertEquals(85, tt.teachers.size)
        assertEquals("-210", tt.classByShort("1at")!!.id)
        assertEquals("Biologia", tt.subjectByShort("biol")!!.name)
    }

    @Test
    fun `teacher shorts are not unique, ids are`() {
        val kk = tt.teachers.values.filter { it.short == "KK" }
        assertEquals(3, kk.size)
        assertEquals(3, kk.map { it.id }.toSet().size)
    }

    @Test
    fun `expands cards into lessons and skips unplaced ones`() {
        // 1268 cards, 12 of them unplaced; each placed card has exactly one day.
        assertEquals(1256, tt.lessons.size)
        val card1 = tt.lessons.single { it.cardId == "*1" }
        assertEquals(3, card1.day) // "00010" = Thursday
        assertEquals(PeriodRange(5, 6), card1.periods)
        assertEquals(setOf(0, 1), card1.weeks)
        assertEquals("-29", card1.subjectId)
        assertEquals(listOf("-35"), card1.teacherIds)
        assertEquals(listOf("-187"), card1.classIds)
        assertEquals(listOf("-71"), card1.classroomIds)
    }

    @Test
    fun `class 1at on Tuesday matches EduPage`() {
        // Computed independently (Python) and matching EduPage's current timetable.
        val expected = listOf(
            Row(1, 1, "hist", "MK", "Cała klasa", "A54"),
            Row(2, 2, "ZastMat", "RR", "Cała klasa", "A53"),
            Row(3, 3, "GW", "KK", "Cała klasa", "A33"),
            Row(4, 5, "PEiE", "BoA", "1. Grupa", "C201"),
            Row(4, 5, "PEiE", "KW", "2. Grupa", "A10"),
            Row(6, 8, "PT", "BoA", "2. Grupa", "C201"),
            Row(6, 8, "PT", "LA", "1. Grupa", "A104"),
            Row(9, 9, "religia", "MCz", "Religia", "A101d"),
        )
        val actual = tt.lessonsForClass("-210").filter { it.day == 1 }.map { it.toRow() }
        assertEquals(expected.sortedBy { it.toString() }, actual.sortedBy { it.toString() })
    }

    @Test
    fun `group filter shows chosen group, whole class and undecided divisions`() {
        val selected = tt.groupIdsForNames("-210", setOf("1. Grupa"))
        assertEquals(1, selected.size)
        val tuesday = tt.lessonsForClass("-210", selectedGroupIds = selected)
            .filter { it.day == 1 }.map { it.toRow() }

        assertTrue(tuesday.none { it.group == "2. Grupa" })
        assertTrue(tuesday.any { it.group == "1. Grupa" && it.subject == "PEiE" })
        assertTrue(tuesday.any { it.group == "Religia" })
        assertTrue(tuesday.any { it.group == "Cała klasa" })
    }

    @Test
    fun `teacher and classroom views`() {
        assertEquals(16, tt.lessonsForTeacher("-35").size)
        val a10 = tt.classroomByShort("A10")!!
        assertTrue(tt.lessonsForClassroom(a10.id).isNotEmpty())
        assertTrue(tt.lessonsForClassroom(a10.id).all { a10.id in it.classroomIds })
    }

    @Test
    fun `broken response throws EdupageException`() {
        assertFailsWith<EdupageException> { RegularTimetableParser.parse("""{"r":{"foo":1}}""", "1") }
    }

    private data class Row(
        val first: Int,
        val last: Int,
        val subject: String,
        val teacher: String,
        val group: String,
        val room: String,
    )

    private fun TimetableLesson.toRow() = Row(
        first = periods.first,
        last = periods.last,
        subject = tt.subjects.getValue(subjectId).short,
        teacher = teacherIds.joinToString { tt.teachers.getValue(it).short },
        group = groupIds.joinToString { tt.groups.getValue(it).name },
        room = classroomIds.joinToString { tt.classrooms.getValue(it).short },
    )

    @Test
    fun `long subject names fall back to acronym shorts`() {
        fun display(short: String) = tt.subjects.values.single { it.short == short }.displayName
        assertEquals("PiKUA", display("PiKUA"))
        assertEquals("MUKMUE", display("MUKMUE"))
        assertEquals("Edukacja dla bezpieczeństwa", display("Edb"))
        assertEquals("Wychowanie fizyczne", display("WF"))
        assertEquals("Przeglądy, konserwacja, diagnostyka i naprawa instalacji automatyki przemysłowej", display("Inst_aut_p"))
    }
}
