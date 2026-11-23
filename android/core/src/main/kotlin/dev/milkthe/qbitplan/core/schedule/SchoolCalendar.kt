package dev.milkthe.qbitplan.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun LocalDate.isSchoolDay(): Boolean = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY

/** [date] itself when it's a school day, otherwise the following Monday. */
fun nearestSchoolDay(date: LocalDate): LocalDate =
    generateSequence(date) { it.plusDays(1) }.first { it.isSchoolDay() }

fun nextSchoolDay(date: LocalDate): LocalDate = nearestSchoolDay(date.plusDays(1))

fun upcomingSchoolDays(from: LocalDate, count: Int): List<LocalDate> =
    generateSequence(nearestSchoolDay(from)) { nextSchoolDay(it) }.take(count).toList()

fun previousSchoolDays(before: LocalDate, count: Int): List<LocalDate> =
    generateSequence(before.minusDays(1)) { it.minusDays(1) }.filter { it.isSchoolDay() }.take(count).toList()

/** Monday–Friday of the week containing [date]. */
fun schoolWeekOf(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L until 5L).map { monday.plusDays(it) }
}
