package dev.milkthe.qbitplan.core.schedule

import java.time.LocalDate
import java.time.LocalTime
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

    @Test
    fun `plan opens on the next school day once the last lesson is over`() {
        val thursday = friday.minusDays(1)
        val end = LocalTime.of(14, 25)
        assertEquals(thursday, planStartDay(thursday, LocalTime.of(14, 24), end))
        assertEquals(friday, planStartDay(thursday, end, end))
        assertEquals(monday, planStartDay(friday, LocalTime.of(18, 0), end))
        assertEquals(friday, planStartDay(friday, LocalTime.of(18, 0), lastLessonEnd = null))
        assertEquals(monday, planStartDay(saturday, LocalTime.of(9, 0), end))
    }
}
