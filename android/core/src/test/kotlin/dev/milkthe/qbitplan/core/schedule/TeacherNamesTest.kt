package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.edupage.SubstitutionsParser
import dev.milkthe.qbitplan.core.model.normalizeName
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeacherNamesTest {

    @Test
    fun `collects unique names sorted by surname`() {
        val days = Fixtures.allSubstitutionDays().map { (date, raw) -> SubstitutionsParser.parse(LocalDate.parse(date), raw) }
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
}
