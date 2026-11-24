package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.core.edupage.SubstitutionsParser
import dev.milkthe.qbitplan.core.model.normalizeName
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeacherNamesTest {

    private val days = Fixtures.allSubstitutionDays().map { (date, raw) -> SubstitutionsParser.parse(LocalDate.parse(date), raw) }

    @Test
    fun `collects unique names sorted by surname`() {
        val names = knownTeacherNames(days)

        assertTrue("Anna Grzebień" in names)
        assertTrue("Wojciech Książkiewicz" in names)
        assertTrue("Barbara Wojtanowska-Słabik" in names)
        assertEquals(names.size, names.map { normalizeName(it) }.toSet().size, "no duplicates")
        assertTrue(names.none { "Nieobecność" in it || "W tym dniu" in it })

        // Polish collation by surname: Góral before Grzebień, Ł after L.
        assertTrue(names.indexOf("Jolanta Góral") < names.indexOf("Anna Grzebień"))
        val surnames = names.map { it.substringAfterLast(' ') }
        val firstL = surnames.indexOfFirst { it.startsWith("L") }
        val firstLstroke = surnames.indexOfFirst { it.startsWith("Ł") }
        if (firstL >= 0 && firstLstroke >= 0) assertTrue(firstL < firstLstroke)
    }

    @Test
    fun `short fits a name by its initials`() {
        assertTrue(shortFitsName("KaM", "Maciej Kania"))
        assertTrue(shortFitsName("MK", "Krystian Małeta"))
        assertTrue(shortFitsName("WB", "Barbara Wojtanowska-Słabik"))
        assertFalse(shortFitsName("HP", "Anna Jurczak"))
        assertFalse(shortFitsName("K", "Krystian Małeta"))
    }

    @Test
    fun `substitutions tell whose name it is`() {
        val tt = RegularTimetableParser.parse(Fixtures.regular189, "189")
        val owners = teacherIdsByName(tt, days)
        fun shortOf(name: String) = owners[normalizeName(name)]?.let { tt.teachers.getValue(it).short }

        // "MK" fits Maciej Kania by initials, but he is KaM in the plan.
        assertEquals("KaM", shortOf("Maciej Kania"))
        assertEquals("JA", shortOf("Anna Jurczak"))
        assertEquals("WB", shortOf("Barbara Wojtanowska-Słabik"))
        // Two teachers share the short BR; each name lands on its own one.
        val rys = owners.getValue(normalizeName("Bartosz Ryś"))
        val bargiel = owners.getValue(normalizeName("Radosław Bargieł"))
        assertEquals("BR", tt.teachers.getValue(rys).short)
        assertEquals("BR", tt.teachers.getValue(bargiel).short)
        assertTrue(rys != bargiel)
        // Only ever seen covering, never on their own planned lesson: unknown.
        assertNull(shortOf("Krystian Małeta"))
    }
}
