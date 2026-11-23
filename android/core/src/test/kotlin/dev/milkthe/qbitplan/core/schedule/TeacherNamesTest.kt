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

        assertTrue("Anna Grzebień" in names) // zastępczyni
        assertTrue("Wojciech Książkiewicz" in names) // nieobecny
        assertTrue("Barbara Wojtanowska-Słabik" in names) // nazwisko z myślnikiem
        assertEquals(names.size, names.map { normalizeName(it) }.toSet().size, "bez duplikatów")
        assertTrue(names.none { "Nieobecność" in it || "W tym dniu" in it })

        // Polskie sortowanie po nazwisku: Góral przed Grzebień, Ł po L.
        assertTrue(names.indexOf("Jolanta Góral") < names.indexOf("Anna Grzebień"))
        val surnames = names.map { it.substringAfterLast(' ') }
        val firstL = surnames.indexOfFirst { it.startsWith("L") }
        val firstLstroke = surnames.indexOfFirst { it.startsWith("Ł") }
        if (firstL >= 0 && firstLstroke >= 0) assertTrue(firstL < firstLstroke)
    }
}
