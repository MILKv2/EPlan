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
 * Test na prawdziwym serwerze: sprawdza, czy EduPage nie zmieniło formatu.
 * Domyślnie pomijany; uruchomienie:
 *   QBIT_LIVE=1 ./gradlew :core:test --tests '*LiveEdupageTest*' --rerun
 */
class LiveEdupageTest {

    @Test
    fun `live data parses`() = runTest(timeout = 2.minutes) {
        assumeTrue("Ustaw QBIT_LIVE=1", System.getenv("QBIT_LIVE") == "1")
        val api = OkHttpEdupageApi(OkHttpClient())
        val today = LocalDate.now()

        val versions = TimetableVersionsParser.parse(api.fetchTimetableVersions(schoolYearOf(today)))
        val ttNum = assertNotNull(versions.defaultTtNum, "brak domyślnego planu")

        val tt = RegularTimetableParser.parse(api.fetchRegularTimetable(ttNum), ttNum)
        assertTrue(tt.lessons.isNotEmpty() && tt.classes.isNotEmpty() && tt.periods.isNotEmpty())

        for (date in listOf(today, today.plusDays(1))) {
            val day = SubstitutionsParser.parse(date, api.fetchSubstitutions(date))
            println("$date: ${day.substitutions.size} zastępstw, komunikat=${day.message}")
            assertTrue(day.substitutions.all { it.unknownParts.isEmpty() }, "nieznane części: $day")
            assertTrue(day.otherNotes.isEmpty(), "nieznane nagłówki: ${day.otherNotes}")
        }
    }
}
