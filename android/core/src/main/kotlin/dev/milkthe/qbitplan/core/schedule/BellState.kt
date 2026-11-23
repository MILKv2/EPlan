package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.Period
import java.time.Duration
import java.time.LocalTime

/** Gdzie w dniu szkolnym jesteśmy: do paska „teraz” i odliczania do dzwonka. */
sealed interface BellState {
    /** Trwa lekcja [period], dzwonek o [period].end. */
    data class DuringLesson(val period: Period) : BellState {
        fun untilBell(now: LocalTime): Duration = Duration.between(now, period.end)
    }

    /** Przerwa (albo przed pierwszą lekcją, gdy [previous] == null). */
    data class Break(val previous: Period?, val next: Period) : BellState {
        fun untilBell(now: LocalTime): Duration = Duration.between(now, next.start)
    }

    /** Po ostatniej lekcji. */
    data object AfterSchool : BellState
}

/**
 * Stan dzwonków o godzinie [now] wg tabeli [periods]. Lekcja trwa
 * od `start` włącznie do `end` wyłącznie.
 */
fun bellStateAt(periods: List<Period>, now: LocalTime): BellState {
    val sorted = periods.sortedBy { it.start }
    sorted.firstOrNull { now >= it.start && now < it.end }?.let { return BellState.DuringLesson(it) }
    val next = sorted.firstOrNull { it.start > now } ?: return BellState.AfterSchool
    return BellState.Break(previous = sorted.lastOrNull { it.end <= now }, next = next)
}

/** Numer lekcji trwającej o [time] (do „wolne sale o godz. X”); `null` = przerwa/po lekcjach. */
fun periodAt(periods: List<Period>, time: LocalTime): Int? =
    (bellStateAt(periods, time) as? BellState.DuringLesson)?.period?.number
