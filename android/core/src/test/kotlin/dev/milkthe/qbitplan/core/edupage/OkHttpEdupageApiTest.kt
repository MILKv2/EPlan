package dev.milkthe.qbitplan.core.edupage

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkHttpEdupageApiTest {

    private val server = MockWebServer()
    private lateinit var api: OkHttpEdupageApi

    @BeforeTest
    fun setUp() {
        server.start()
        api = OkHttpEdupageApi(OkHttpClient(), server.url("/"))
    }

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `substitutions request`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"r":"<div></div>"}""").build())

        val body = api.fetchSubstitutions(LocalDate.of(2026, 11, 24))

        assertEquals("""{"r":"<div></div>"}""", body)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/substitution/server/viewer.js", request.url.encodedPath)
        assertEquals("getSubstViewerDayDataHtml", request.url.queryParameter("__func"))
        val json = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("00000000", json["__gsh"]!!.jsonPrimitive.content)
        val args = json["__args"]!!.jsonArray
        assertEquals(JsonNull, args[0])
        assertEquals("2026-11-24", args[1].jsonObject["date"]!!.jsonPrimitive.content)
        assertEquals("classes", args[1].jsonObject["mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `versions and regular timetable requests`() = runTest {
        server.enqueue(MockResponse.Builder().body("{}").build())
        server.enqueue(MockResponse.Builder().body("{}").build())

        api.fetchTimetableVersions(2026)
        api.fetchRegularTimetable("189")

        val versions = server.takeRequest()
        assertEquals("/timetable/server/ttviewer.js", versions.url.encodedPath)
        assertEquals("getTTViewerData", versions.url.queryParameter("__func"))
        assertEquals(2026, Json.parseToJsonElement(versions.body!!.utf8()).jsonObject["__args"]!!.jsonArray[1].jsonPrimitive.int)

        val regular = server.takeRequest()
        assertEquals("/timetable/server/regulartt.js", regular.url.encodedPath)
        assertEquals("regularttGetData", regular.url.queryParameter("__func"))
        assertEquals("189", Json.parseToJsonElement(regular.body!!.utf8()).jsonObject["__args"]!!.jsonArray[1].jsonPrimitive.content)
    }

    @Test
    fun `http error becomes EdupageException`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        assertFailsWith<EdupageException> { api.fetchRegularTimetable("189") }
    }
}
