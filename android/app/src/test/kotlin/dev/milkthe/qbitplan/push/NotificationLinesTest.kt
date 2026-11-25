package dev.milkthe.qbitplan.push

import dev.milkthe.qbitplan.core.model.PeriodRange
import org.junit.Test
import kotlin.test.assertEquals

class NotificationLinesTest {

    private fun line(first: Int, last: Int = first, text: String = "$first") = NotificationLine(PeriodRange(first, last), text)
    private val notice = NotificationLine(null, "?")

    @Test
    fun `new lines join the shown ones by period`() {
        assertEquals(listOf("2", "4", "6"), mergeLines(listOf(line(2), line(6)), listOf(line(4))).map { it.text })
    }

    @Test
    fun `a lesson changed again reads as its latest state`() {
        val merged = mergeLines(listOf(line(4, text = "old"), line(3, 4, text = "block"), line(5)), listOf(line(4, text = "new")))
        assertEquals(listOf("new", "5"), merged.map { it.text })
    }

    @Test
    fun `the vague notice gives way to details and isn't repeated`() {
        assertEquals(listOf("4"), mergeLines(listOf(notice), listOf(line(4))).map { it.text })
        assertEquals(listOf("4", "?"), mergeLines(listOf(line(4), notice), listOf(notice)).map { it.text })
    }

    @Test
    fun `nothing shown means just the new lines`() {
        assertEquals(listOf(line(4)), mergeLines(emptyList(), listOf(line(4))))
    }
}
