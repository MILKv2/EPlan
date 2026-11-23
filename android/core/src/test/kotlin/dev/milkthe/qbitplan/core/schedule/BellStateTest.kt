package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.Period
import java.time.Duration
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BellStateTest {

    private val periods = listOf(
        Period(0, "0", LocalTime.of(7, 5), LocalTime.of(7, 50)),
        Period(1, "1", LocalTime.of(7, 55), LocalTime.of(8, 40)),
        Period(2, "2", LocalTime.of(8, 45), LocalTime.of(9, 30)),
    )

    @Test
    fun `before first lesson`() {
        val state = assertIs<BellState.Break>(bellStateAt(periods, LocalTime.of(6, 50)))
        assertNull(state.previous)
        assertEquals(0, state.next.number)
        assertEquals(Duration.ofMinutes(15), state.untilBell(LocalTime.of(6, 50)))
    }

    @Test
    fun `during lesson, bell boundaries`() {
        val state = assertIs<BellState.DuringLesson>(bellStateAt(periods, LocalTime.of(8, 0)))
        assertEquals(1, state.period.number)
        assertEquals(Duration.ofMinutes(40), state.untilBell(LocalTime.of(8, 0)))
        assertEquals(1, periodAt(periods, LocalTime.of(7, 55)))
        assertNull(periodAt(periods, LocalTime.of(8, 40)))
    }

    @Test
    fun `break between lessons`() {
        val state = assertIs<BellState.Break>(bellStateAt(periods, LocalTime.of(8, 42)))
        assertEquals(1, state.previous?.number)
        assertEquals(2, state.next.number)
    }

    @Test
    fun `after school`() {
        assertEquals(BellState.AfterSchool, bellStateAt(periods, LocalTime.of(9, 30)))
        assertEquals(BellState.AfterSchool, bellStateAt(emptyList(), LocalTime.NOON))
    }
}
