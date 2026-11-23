package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.Period
import java.time.Duration
import java.time.LocalTime

sealed interface BellState {
    data class DuringLesson(val period: Period) : BellState {
        fun untilBell(now: LocalTime): Duration = Duration.between(now, period.end)
    }

    /** [previous] is `null` before the first lesson. */
    data class Break(val previous: Period?, val next: Period) : BellState {
        fun untilBell(now: LocalTime): Duration = Duration.between(now, next.start)
    }

    data object AfterSchool : BellState
}

/** Lessons run from `start` inclusive to `end` exclusive. */
fun bellStateAt(periods: List<Period>, now: LocalTime): BellState {
    val sorted = periods.sortedBy { it.start }
    sorted.firstOrNull { now >= it.start && now < it.end }?.let { return BellState.DuringLesson(it) }
    val next = sorted.firstOrNull { it.start > now } ?: return BellState.AfterSchool
    return BellState.Break(previous = sorted.lastOrNull { it.end <= now }, next = next)
}

fun periodAt(periods: List<Period>, time: LocalTime): Int? =
    (bellStateAt(periods, time) as? BellState.DuringLesson)?.period?.number
