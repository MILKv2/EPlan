package dev.milkthe.qbitplan.core.update

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OkHttpReleaseApiTest {

    private val server = MockWebServer()
    private lateinit var api: OkHttpReleaseApi

    @BeforeTest
    fun setUp() {
        server.start()
        api = OkHttpReleaseApi(OkHttpClient(), server.url("/repos/MILKv2/QbitPlan/releases/latest"))
    }

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `fresh response carries body and etag`() = runTest {
        server.enqueue(MockResponse.Builder().body("{}").addHeader("ETag", "W/\"abc\"").build())

        assertEquals(ReleaseResponse.Fresh("{}", "W/\"abc\""), api.fetchLatest(etag = null))

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/repos/MILKv2/QbitPlan/releases/latest", request.url.encodedPath)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        assertNull(request.headers["If-None-Match"])
    }

    @Test
    fun `sends the etag and understands 304`() = runTest {
        server.enqueue(MockResponse.Builder().code(304).build())

        assertEquals(ReleaseResponse.NotModified, api.fetchLatest(etag = "W/\"abc\""))
        assertEquals("W/\"abc\"", server.takeRequest().headers["If-None-Match"])
    }

    @Test
    fun `404 means no release yet, other errors throw`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(403).build())

        assertEquals(ReleaseResponse.NoRelease, api.fetchLatest(etag = null))
        assertFailsWith<ReleaseException> { api.fetchLatest(etag = null) }
    }
}
