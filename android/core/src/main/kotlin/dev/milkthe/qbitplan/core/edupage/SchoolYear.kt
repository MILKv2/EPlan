package dev.milkthe.qbitplan.core.edupage

import java.time.LocalDate
import java.time.Month

/**
 * EduPage school year: 2026 = 2026/27, switching in August. Callers retry with
 * the previous year when the new one has no default timetable yet.
 */
fun schoolYearOf(date: LocalDate): Int =
    if (date.month >= Month.AUGUST) date.year else date.year - 1
