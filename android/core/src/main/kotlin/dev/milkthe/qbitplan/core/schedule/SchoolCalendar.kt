package dev.milkthe.qbitplan.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

fun LocalDate.isSchoolDay(): Boolean = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY

/** [date] itself when it's a school day, otherwise the following Monday. */
fun nearestSchoolDay(date: LocalDate): LocalDate =
    generateSequence(date) { it.plusDays(1) }.first { it.isSchoolDay() }

fun nextSchoolDay(date: LocalDate): LocalDate = nearestSchoolDay(date.plusDays(1))

/** The day a plan opens on: today until its last lesson ([lastLessonEnd]) is over, then the next school day. */
fun planStartDay(today: LocalDate, now: LocalTime, lastLessonEnd: LocalTime?): LocalDate = when {
    !today.isSchoolDay() -> nearestSchoolDay(today)
    lastLessonEnd != null && now >= lastLessonEnd -> nextSchoolDay(today)
    else -> today
}

/** When [planStartDay] can next give another answer: the end of today's last lesson, else midnight. */
fun nextPlanDayChange(today: LocalDate, now: LocalTime, lastLessonEnd: LocalTime?): LocalDateTime =
    if (today.isSchoolDay() && lastLessonEnd != null && now < lastLessonEnd) today.atTime(lastLessonEnd)
    else today.plusDays(1).atStartOfDay()

fun upcomingSchoolDays(from: LocalDate, count: Int): List<LocalDate> =
    generateSequence(nearestSchoolDay(from)) { nextSchoolDay(it) }.take(count).toList()

fun previousSchoolDays(before: LocalDate, count: Int): List<LocalDate> =
    generateSequence(before.minusDays(1)) { it.minusDays(1) }.filter { it.isSchoolDay() }.take(count).toList()

/** Monday–Friday of the week containing [date]. */
fun schoolWeekOf(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L until 5L).map { monday.plusDays(it) }
}
