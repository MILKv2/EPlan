package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.edupage.OkHttpEdupageApi
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.core.edupage.SubstitutionsParser
import dev.milkthe.qbitplan.core.edupage.TimetableVersionsParser
import dev.milkthe.qbitplan.core.edupage.schoolYearOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume.assumeTrue
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Live oracle for every class: compares DayPlanner with EduPage's current timetable
 * (hidden in the school's UI, but the endpoint responds). Test-only; the app doesn't use it.
 *
 *   QBIT_LIVE=1 ./gradlew :core:test --tests '*LiveOracleTest*' --rerun -i
 */
class LiveOracleTest {

    @Test
    fun `planner matches live current timetable for all classes`() = runTest(timeout = 5.minutes) {
        assumeTrue("Set QBIT_LIVE=1", System.getenv("QBIT_LIVE") == "1")
        val client = OkHttpClient()
        val api = OkHttpEdupageApi(client)
        val today = LocalDate.now()
        val year = schoolYearOf(today)

        val ttNum = TimetableVersionsParser.parse(api.fetchTimetableVersions(year)).defaultTtNum!!
        val tt = RegularTimetableParser.parse(api.fetchRegularTimetable(ttNum), ttNum)
        val planner = DayPlanner(tt)
        val compare = OracleComparison(tt)

        val dates = generateSequence(today) { it.plusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(2).toList()
        val subs = dates.associateWith { SubstitutionsParser.parse(it, api.fetchSubstitutions(it)) }

        val mismatches = mutableListOf<String>()
        var compared = 0
        for (schoolClass in tt.classes.values.sortedBy { it.short }) {
            val oracle = compare.fromOracle(fetchCurrent(client, year, schoolClass.id, dates.first(), dates.last()))
            for (date in dates) {
                val ours = compare.fromPlanner(planner.forClass(schoolClass.id, date, subs[date]))
                val expected = oracle[date.toString()].orEmpty()
                compared++
                if (ours != expected) {
                    mismatches += "${schoolClass.short} $date\n  oracle only: ${expected - ours.toSet()}\n  ours only:   ${ours - expected.toSet()}"
                }
            }
        }
        println("Compared $compared class-days, mismatches: ${mismatches.size}")
        mismatches.forEach(::println)
        assertTrue(mismatches.isEmpty(), "Mismatches: ${mismatches.size} (see log)")
    }

    private fun fetchCurrent(client: OkHttpClient, year: Int, classId: String, from: LocalDate, to: LocalDate): String {
        val body = """{"__args":[null,{"year":$year,"datefrom":"$from","dateto":"$to","table":"classes",""" +
            """"id":"$classId","showColors":true,"showIgroupsInClasses":false,"showOrig":true,""" +
            """"log_module":"CurrentTTView"}],"__gsh":"00000000"}"""
        val request = Request.Builder()
            .url("https://elektryk.edupage.org/timetable/server/currenttt.js?__func=curentttGetData")
            .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        return client.newCall(request).execute().use { it.body.string() }
    }
}
