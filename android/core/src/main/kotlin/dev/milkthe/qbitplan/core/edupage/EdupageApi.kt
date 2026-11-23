package dev.milkthe.qbitplan.core.edupage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate

/** Returns raw response bodies; parsing is separate so raw responses can be cached and re-parsed. */
interface EdupageApi {
    suspend fun fetchTimetableVersions(schoolYear: Int): String
    suspend fun fetchRegularTimetable(ttNum: String): String
    suspend fun fetchSubstitutions(date: LocalDate): String
}

class OkHttpEdupageApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
) : EdupageApi {

    override suspend fun fetchTimetableVersions(schoolYear: Int): String =
        post("timetable/server/ttviewer.js", "getTTViewerData", buildJsonArray {
            add(JsonNull)
            add(schoolYear)
        })

    override suspend fun fetchRegularTimetable(ttNum: String): String =
        post("timetable/server/regulartt.js", "regularttGetData", buildJsonArray {
            add(JsonNull)
            add(ttNum)
        })

    override suspend fun fetchSubstitutions(date: LocalDate): String =
        post("substitution/server/viewer.js", "getSubstViewerDayDataHtml", buildJsonArray {
            add(JsonNull)
            addJsonObject {
                put("date", date.toString())
                put("mode", "classes") // the only public view
            }
        })

    private suspend fun post(path: String, function: String, args: JsonArray): String {
        val url = baseUrl.newBuilder()
            .addPathSegments(path)
            .addQueryParameter("__func", function)
            .build()
        val body = buildJsonObject {
            put("__args", args)
            put("__gsh", GUEST_GSH)
        }.toString().toRequestBody(JSON)
        val request = Request.Builder().url(url).post(body).build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw EdupageException("HTTP ${response.code} for $function")
                response.body.string()
            }
        }
    }

    companion object {
        val DEFAULT_BASE_URL = "https://elektryk.edupage.org/".toHttpUrl()

        /** Always "00000000" for guests; without it the server replies {"reload":true}. */
        const val GUEST_GSH = "00000000"

        private val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}

class EdupageException(message: String, cause: Throwable? = null) : IOException(message, cause)
