package dev.milkthe.qbitplan.core.edupage

import dev.milkthe.qbitplan.core.Fixtures
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimetableVersionsParserTest {

    @Test
    fun `parses default version and history`() {
        val versions = TimetableVersionsParser.parse(Fixtures.versions)

        assertEquals("189", versions.defaultTtNum)
        val default = versions.default!!
        assertEquals(LocalDate.of(2026, 11, 21), default.validFrom)
        assertEquals("listopad2026_RC3 (21.11. - 31.12.2026)", default.name)
        assertEquals(2026, default.schoolYear)
        assertFalse(default.hidden)
        assertTrue(versions.versions.size > 100, "history of all years")
    }

    @Test
    fun `empty default_num means no timetable for that year`() {
        val raw = """{"r":{"regular":{"default_num":"","timetables":[]}}}"""
        assertNull(TimetableVersionsParser.parse(raw).defaultTtNum)
    }

    @Test
    fun `server errors become EdupageException`() {
        assertFailsWith<EdupageException> { TimetableVersionsParser.parse("""{"e":"Error: 9906805036","em":"undefined"}""") }
        assertFailsWith<EdupageException> { TimetableVersionsParser.parse("""{"reload":true}""") }
        assertFailsWith<EdupageException> { TimetableVersionsParser.parse("<html>Cloudflare</html>") }
    }

    @Test
    fun `school year switches in August`() {
        assertEquals(2026, schoolYearOf(LocalDate.of(2026, 11, 23)))
        assertEquals(2026, schoolYearOf(LocalDate.of(2026, 8, 1)))
        assertEquals(2025, schoolYearOf(LocalDate.of(2026, 7, 31)))
        assertEquals(2025, schoolYearOf(LocalDate.of(2026, 1, 10)))
    }
}
