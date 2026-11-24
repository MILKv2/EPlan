package dev.milkthe.qbitplan.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed interface ReleaseResponse {
    data class Fresh(val body: String, val etag: String?) : ReleaseResponse
    /** Matched the ETag we sent; the cached body is still current. */
    data object NotModified : ReleaseResponse
    /** No published release yet (GitHub answers 404, also while the repo is private). */
    data object NoRelease : ReleaseResponse
}

interface ReleaseApi {
    suspend fun fetchLatest(etag: String?): ReleaseResponse
}

class OkHttpReleaseApi(
    private val client: OkHttpClient,
    private val url: HttpUrl = LATEST_RELEASE_URL,
) : ReleaseApi {

    // Unauthenticated GitHub API allows 60 requests per hour per IP, and a school network
    // shares one IP. A 304 for a matching ETag doesn't count against that limit.
    override suspend fun fetchLatest(etag: String?): ReleaseResponse {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> ReleaseResponse.NotModified
                    response.code == 404 -> ReleaseResponse.NoRelease
                    response.isSuccessful -> ReleaseResponse.Fresh(response.body.string(), response.header("ETag"))
                    else -> throw ReleaseException("HTTP ${response.code} for latest release")
                }
            }
        }
    }

    companion object {
        val LATEST_RELEASE_URL = "https://api.github.com/repos/MILKv2/QbitPlan/releases/latest".toHttpUrl()
    }
}

class ReleaseException(message: String, cause: Throwable? = null) : IOException(message, cause)
