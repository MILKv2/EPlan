package dev.milkthe.qbitplan.core.model

import dev.milkthe.qbitplan.core.Fixtures
import dev.milkthe.qbitplan.core.edupage.TimetableVersionsParser
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionChoiceTest {

    // Published 2026-11-28 00:00: 190 (from 28.11) is already default, 189 still visible.
    private val versions = TimetableVersionsParser.parse(Fixtures.read("ttviewer_2026-11-28.json"))
    private val friday = LocalDate.of(2026, 11, 27)
    private val monday = LocalDate.of(2026, 11, 30)

    @Test
    fun `published versions are the visible ones of the year, newest first`() {
        assertEquals(listOf("190", "189", "188"), versions.published(2026).map { it.ttNum })
        assertTrue(versions.published(2025).isEmpty())
        assertEquals("190", versions.newest(2026)?.ttNum)
    }

    @Test
    fun `picks the version in force on the day, not default_num`() {
        assertEquals("190", versions.defaultTtNum)
        assertEquals(VersionChoice("189", pinned = false), versions.choose(friday, 2026, pin = null))
        assertEquals(VersionChoice("190", pinned = false), versions.choose(monday, 2026, pin = null))
    }

    @Test
    fun `a version published ahead is used before default_num is switched`() {
        val raw = """{"r":{"regular":{"default_num":"189","timetables":[
            {"tt_num":"189","year":2026,"text":"a","datefrom":"2026-11-21","hidden":false},
            {"tt_num":"190","year":2026,"text":"b","datefrom":"2026-11-28","hidden":false},
            {"tt_num":"191","year":2026,"text":"draft","datefrom":"2026-11-29","hidden":true}]}}}"""
        val ahead = TimetableVersionsParser.parse(raw)
        assertEquals("189", ahead.choose(friday, 2026, null)?.ttNum)
        assertEquals("190", ahead.choose(monday, 2026, null)?.ttNum, "hidden 191 is a draft")
    }

    @Test
    fun `falls back to default_num when no version matches the day`() {
        val raw = """{"r":{"regular":{"default_num":"200","timetables":[
            {"tt_num":"200","year":2026,"text":"a","datefrom":"2026-09-01","hidden":false}]}}}"""
        val early = TimetableVersionsParser.parse(raw)
        assertEquals("200", early.choose(LocalDate.of(2026, 8, 28), 2026, null)?.ttNum)
        assertNull(TimetableVersionsParser.parse("""{"r":{"regular":{"default_num":"","timetables":[]}}}""").choose(monday, 2026, null))
    }

    @Test
    fun `a pin holds until a newer version is published`() {
        val pin = VersionPin("189", newestAtPin = "190")
        assertTrue(versions.isPinValid(pin, 2026))
        assertEquals(VersionChoice("189", pinned = true), versions.choose(monday, 2026, pin))

        val newer = versions.copy(versions = versions.versions + TimetableVersion("191", 2026, "styczen", LocalDate.of(2027, 1, 4), hidden = false))
        assertFalse(newer.isPinValid(pin, 2026))
        assertEquals(VersionChoice("190", pinned = false), newer.choose(monday, 2026, pin))
    }

    @Test
    fun `a pin to a version that is gone or hidden is ignored`() {
        assertFalse(versions.isPinValid(VersionPin("187", newestAtPin = "190"), 2026), "187 is hidden")
        assertFalse(versions.isPinValid(VersionPin("999", newestAtPin = "190"), 2026))
    }

    @Test
    fun `short name drops the date range`() {
        assertEquals("grudzien2026_RC1", versions.versions.first { it.ttNum == "190" }.shortName)
        assertEquals("191", TimetableVersion("191", 2026, "", LocalDate.of(2027, 1, 4), hidden = false).shortName)
    }
}
