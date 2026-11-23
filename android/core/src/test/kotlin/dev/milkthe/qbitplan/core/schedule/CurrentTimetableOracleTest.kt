package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.core.edupage.SubstitutionsParser
import dev.milkthe.qbitplan.core.model.Timetable
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Porównanie z wyrocznią: plan bieżący EduPage (fixtures/currenttt) ma
 * zastępstwa nałożone po stronie serwera. Nasz [DayPlanner] (plan stały
 * + HTML zastępstw) musi dać to samo, godzina po godzinie.
 * Wersja na żywo, dla wszystkich klas: LiveOracleTest.
 */
class CurrentTimetableOracleTest {

    private val tt: Timetable by lazy { RegularTimetableParser.parse(Fixtures.regular189, "189") }

    @Test
    fun `planner matches EduPage current timetable`() {
        val planner = DayPlanner(tt)
        val compare = OracleComparison(tt)
        for (cls in listOf("1at", "1ct", "1dt", "1fl", "2at", "2ct", "3abt", "4fl")) {
            val oracle = compare.fromOracle(Fixtures.read("currenttt/${cls}_2026-11-23_27.json"))
            for (date in listOf("2026-11-23", "2026-11-24", "2026-11-25")) {
                val day = LocalDate.parse(date)
                val subs = SubstitutionsParser.parse(day, Fixtures.substitutions(date))
                val ours = compare.fromPlanner(planner.forClass(tt.classByShort(cls)!!.id, day, subs))
                assertEquals(oracle[date].orEmpty(), ours, "$cls $date")
            }
        }
    }
}
