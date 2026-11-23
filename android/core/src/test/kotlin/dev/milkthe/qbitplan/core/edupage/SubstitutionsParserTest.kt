package dev.milkthe.qbitplan.core.edupage

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.model.AbsenceNote
import dev.milkthe.qbitplan.core.model.Change
import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Substitution
import dev.milkthe.qbitplan.core.model.SubstitutionType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubstitutionsParserTest {

    private fun day(date: String): DaySubstitutions =
        SubstitutionsParser.parse(LocalDate.parse(date), Fixtures.substitutions(date))

    private fun DaySubstitutions.row(cls: String, period: Int, type: SubstitutionType? = null): Substitution =
        substitutions.single {
            it.className == cls && it.periods?.first == period && (type == null || it.type == type)
        }

    @Test
    fun `header notes - absent teachers and rooms with periods`() {
        val d = day("2026-11-24")
        assertEquals(10, d.absentTeachers.size)
        assertEquals(AbsenceNote("Mariusz Czekański", null), d.absentTeachers.first())
        assertEquals(AbsenceNote("Justyna Drozd", PeriodRange.single(2)), d.absentTeachers[1])
        assertEquals(listOf(AbsenceNote("A24", PeriodRange.single(2))), d.unavailableClassrooms)
        assertTrue(d.absentClasses.isEmpty())
        assertTrue(d.otherNotes.isEmpty())
        assertNull(d.message)
    }

    @Test
    fun `substitute teacher with group, subject and room change`() {
        val s = day("2026-11-24").row("1at", 4)
        assertEquals(SubstitutionType.CHANGE, s.type)
        assertEquals(PeriodRange.single(4), s.periods)
        assertEquals(listOf("2. Grupa"), s.groups)
        assertEquals(Change("PEiE", "inf"), s.subject)
        assertEquals(Change("Wojciech Książkiewicz", "Anna Grzebień"), s.teacher)
        assertEquals(Change("A10", "B23"), s.classroom)
        assertTrue(s.involvesTeacher("anna  grzebień"))
        assertTrue(s.involvesTeacher("Wojciech Książkiewicz"))
    }

    @Test
    fun `cancelled lesson`() {
        val s = day("2026-11-24").row("1al", 0)
        assertEquals(SubstitutionType.CANCELLED, s.type)
        assertEquals(listOf("Chłopcy"), s.groups)
        assertEquals(Change.unchanged("WF"), s.subject)
        assertEquals(Change.unchanged("Marek Fornal"), s.teacher)
        assertNull(s.classroom)
    }

    @Test
    fun `moved lesson appears as a pair of rows`() {
        val d = day("2026-11-24")
        val away = d.row("2ct", 0)
        assertEquals(SubstitutionType.MOVED_AWAY, away.type)
        assertEquals(4, away.movedToPeriod)
        assertEquals(Change.unchanged("Bartosz Ryś"), away.teacher)
        assertEquals(Change.unchanged("matem"), away.subject)

        val here = d.row("2ct", 4, SubstitutionType.MOVED_HERE)
        assertEquals(0, here.movedFromPeriod)
        assertEquals(Change.unchanged("Bartosz Ryś"), here.teacher)

        assertEquals(Change.unchanged("Eob"), d.row("2ct", 4, SubstitutionType.CANCELLED).subject)
    }

    @Test
    fun `struck-out teacher alone means no substitute`() {
        val s = day("2026-11-25").row("1al", 3)
        assertEquals(SubstitutionType.NO_SUBSTITUTE, s.type)
        assertEquals(Change("Andrzej Frączek", null), s.teacher)
        assertEquals(Change.unchanged("GW"), s.subject)
    }

    @Test
    fun `day without substitutions has a message`() {
        val d = day("2026-11-21")
        assertTrue(d.substitutions.isEmpty())
        assertEquals("W tym dniu nie ma żadnych zastępstw.", d.message)
        assertEquals(listOf(AbsenceNote("Jolanta Góral", null)), d.absentTeachers)
        assertEquals(LocalDateTime.of(2026, 11, 18, 17, 9, 6), d.generatedAt)
    }

    @Test
    fun `absent class for the whole day`() {
        val d = day("2026-10-15")
        assertEquals(listOf(AbsenceNote("1dt", null)), d.absentClasses)
        assertEquals(listOf(AbsenceNote("A24", PeriodRange(4, 9)), AbsenceNote("A28", PeriodRange(4, 9))), d.unavailableClassrooms)
        assertEquals(AbsenceNote("Andrzej Bednarczyk", PeriodRange(3, 4)), d.absentTeachers[1])
        val s = d.substitutions.single { it.className == "1dt" && it.type == SubstitutionType.CLASS_ABSENT }
        assertNull(s.periods)
        assertEquals("Nieobecność", s.whatText)
    }

    @Test
    fun `period ranges and room change with unchanged teacher`() {
        val d = day("2026-09-10")
        val s = d.substitutions.single { it.className == "2al" && it.periods == PeriodRange(3, 4) }
        assertEquals(Change("Renata Rysz", "Barbara Wojtanowska-Słabik"), s.teacher)
        assertEquals(AbsenceNote("Wojciech Książkiewicz", PeriodRange(0, 4)), d.absentTeachers[1])

        val roomOnly = d.row("1al", 2)
        assertEquals(SubstitutionType.CHANGE, roomOnly.type)
        assertEquals(listOf("2. Grupa"), roomOnly.groups)
        assertEquals(Change.unchanged("Anna Olbrycht"), roomOnly.teacher)
        assertEquals(Change("A24", "A32"), roomOnly.classroom)
    }

    @Test
    fun `lesson without some class and multiple groups`() {
        val s = day("2026-11-20").substitutions.single { "2bdt" in it.withoutClasses }
        assertEquals("2et", s.className)
        assertEquals(PeriodRange(3, 4), s.periods)
        assertEquals(listOf("B-elektronik", "2. Grupa"), s.groups)
        assertEquals(Change.unchanged("Grzegorz Stareńczak"), s.teacher)
        assertTrue(s.unknownParts.isEmpty())
    }

    @Test
    fun `every fixture day parses fully`() {
        val expectedRows = mapOf(
            "2026-09-10" to 71, "2026-10-15" to 60, "2026-11-20" to 50, "2026-11-21" to 0,
            "2026-11-23" to 43, "2026-11-24" to 55, "2026-11-25" to 16,
        )
        for ((date, raw) in Fixtures.allSubstitutionDays()) {
            val d = SubstitutionsParser.parse(LocalDate.parse(date), raw)
            assertEquals(expectedRows[date], d.substitutions.size, "row count $date")
            assertTrue(d.generatedAt != null, "footer $date")
            assertTrue(d.otherNotes.isEmpty(), "unknown headers $date: ${d.otherNotes}")
            for (s in d.substitutions) {
                assertTrue(s.className.isNotBlank(), "class in $date: $s")
                assertTrue(s.unknownParts.isEmpty(), "unknown parts in $date: $s")
                assertTrue(s.periods != null || s.type == SubstitutionType.CLASS_ABSENT, "period in $date: $s")
                if (s.type != SubstitutionType.CLASS_ABSENT) assertTrue(s.subject != null, "subject in $date: $s")
            }
        }
    }

    @Test
    fun `unpublished day is just a message`() {
        val raw = """{"r":"<div style=\"padding:10px\">Informacje dla nauczycieli: Zastępstwa nie zostały jeszcze opublikowane.</div>"}"""
        val d = SubstitutionsParser.parse(LocalDate.of(2026, 11, 23), raw)
        assertTrue(d.substitutions.isEmpty())
        assertEquals("Informacje dla nauczycieli: Zastępstwa nie zostały jeszcze opublikowane.", d.message)
    }
}
