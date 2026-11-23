package dev.milkthe.qbitplan.core.edupage

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Checks the live server still parses (detects format changes). Skipped by default:
 *   QBIT_LIVE=1 ./gradlew :core:test --tests '*LiveEdupageTest*' --rerun
 */
class LiveEdupageTest {

    @Test
    fun `live data parses`() = runTest(timeout = 2.minutes) {
        assumeTrue("Set QBIT_LIVE=1", System.getenv("QBIT_LIVE") == "1")
        val api = OkHttpEdupageApi(OkHttpClient())
        val today = LocalDate.now()

        val versions = TimetableVersionsParser.parse(api.fetchTimetableVersions(schoolYearOf(today)))
        val ttNum = assertNotNull(versions.defaultTtNum, "no default timetable")

        val tt = RegularTimetableParser.parse(api.fetchRegularTimetable(ttNum), ttNum)
        assertTrue(tt.lessons.isNotEmpty() && tt.classes.isNotEmpty() && tt.periods.isNotEmpty())

        for (date in listOf(today, today.plusDays(1))) {
            val day = SubstitutionsParser.parse(date, api.fetchSubstitutions(date))
            println("$date: ${day.substitutions.size} substitutions, message=${day.message}")
            assertTrue(day.substitutions.all { it.unknownParts.isEmpty() }, "unknown parts: $day")
            assertTrue(day.otherNotes.isEmpty(), "unknown headers: ${day.otherNotes}")
        }
    }
}
