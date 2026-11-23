package dev.milkthe.qbitplan.core.schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SchoolCalendarTest {

    private val friday = LocalDate.of(2026, 11, 27)
    private val saturday = LocalDate.of(2026, 11, 28)
    private val monday = LocalDate.of(2026, 11, 30)

    @Test
    fun `nearest and next school day skip weekends`() {
        assertEquals(friday, nearestSchoolDay(friday))
        assertEquals(monday, nearestSchoolDay(saturday))
        assertEquals(monday, nextSchoolDay(friday))
        assertEquals(monday, nextSchoolDay(saturday))
    }

    @Test
    fun `upcoming and previous school days`() {
        assertEquals(listOf(friday, monday, monday.plusDays(1)), upcomingSchoolDays(friday, 3))
        assertEquals(listOf(monday, monday.plusDays(1)), upcomingSchoolDays(saturday, 2))
        assertEquals(listOf(friday, friday.minusDays(1)), previousSchoolDays(monday, 2))
    }

    @Test
    fun `school week is Monday to Friday`() {
        val week = schoolWeekOf(LocalDate.of(2026, 11, 26))
        assertEquals(LocalDate.of(2026, 11, 23), week.first())
        assertEquals(friday, week.last())
        assertEquals(LocalDate.of(2026, 11, 23), schoolWeekOf(LocalDate.of(2026, 11, 29)).first())
    }
}
