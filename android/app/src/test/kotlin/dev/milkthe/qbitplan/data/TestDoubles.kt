package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.edupage.EdupageApi
import dev.milkthe.qbitplan.core.update.ReleaseApi
import dev.milkthe.qbitplan.core.update.ReleaseResponse
import dev.milkthe.qbitplan.data.local.CachedEntry
import dev.milkthe.qbitplan.data.local.ResponseCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val fixturesDir = File(System.getProperty("fixtures.dir")!!)

fun fixture(path: String) = File(fixturesDir, path).readText()

/** Serves fixtures; days without a fixture get the "no substitutions" day. */
class FakeEdupageApi : EdupageApi {
    val versionCalls = mutableListOf<Int>()
    val timetableCalls = mutableListOf<String>()
    val substitutionCalls = mutableListOf<LocalDate>()
    var defaultNum = "189"
    var offline = false
    var brokenTimetable = false
    val emptyYears = mutableSetOf<Int>()
    /** Edits the served substitutions page, e.g. to change a row after it was first fetched. */
    var editSubstitutions: (String) -> String = { it }

    override suspend fun fetchTimetableVersions(schoolYear: Int): String {
        if (offline) throw IOException("offline")
        versionCalls += schoolYear
        val num = if (schoolYear in emptyYears) "" else defaultNum
        return """{"r":{"regular":{"default_num":"$num","timetables":[]}}}"""
    }

    override suspend fun fetchRegularTimetable(ttNum: String): String {
        if (offline) throw IOException("offline")
        timetableCalls += ttNum
        return if (brokenTimetable) """{"r":{"oops":true}}""" else fixture("regulartt_189.json")
    }

    override suspend fun fetchSubstitutions(date: LocalDate): String {
        if (offline) throw IOException("offline")
        substitutionCalls += date
        val file = File(fixturesDir, "substitutions/$date.json")
        return editSubstitutions(if (file.exists()) file.readText() else fixture("substitutions/2026-11-21.json"))
    }
}

class InMemoryResponseCache : ResponseCache {
    private val entries = MutableStateFlow<Map<String, CachedEntry>>(emptyMap())
    override fun observe(key: String): Flow<CachedEntry?> = entries.map { it[key] }
    override suspend fun get(key: String) = entries.value[key]
    override suspend fun put(entry: CachedEntry) = entries.update { it + (entry.key to entry) }
    override suspend fun deleteWithPrefixBelow(prefix: String, keyBelow: String) =
        entries.update { map -> map.filterKeys { !(it.startsWith(prefix) && it < keyBelow) } }
}

class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    fun advance(d: Duration) { now = now.plus(d) }
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
}

/** Answers with [next]; records the ETag of each call. */
class FakeReleaseApi(var next: () -> ReleaseResponse = { ReleaseResponse.NoRelease }) : ReleaseApi {
    val etags = mutableListOf<String?>()
    override suspend fun fetchLatest(etag: String?): ReleaseResponse {
        etags += etag
        return next()
    }
}
