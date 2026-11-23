package dev.milkthe.qbitplan.core.edupage

import java.time.LocalDate
import java.time.Month

/**
 * Rok szkolny w rozumieniu EduPage: 2026 = rok 2026/27.
 * Nowy rok liczymy od sierpnia (EduPage dodaje plany na nowy rok pod koniec
 * wakacji). Jeśli dla tego roku nie ma jeszcze domyślnego planu, repozytorium
 * ponawia zapytanie z rokiem o 1 mniejszym.
 */
fun schoolYearOf(date: LocalDate): Int =
    if (date.month >= Month.AUGUST) date.year else date.year - 1
