package dev.milkthe.qbitplan.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A published release; [apkUrl] is the direct download, [pageUrl] the release notes. */
data class AppRelease(
    val version: AppVersion,
    val pageUrl: String,
    val apkUrl: String?,
)

/** Parses GitHub's `GET /repos/{owner}/{repo}/releases/latest`. */
object ReleaseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** `null` when the tag isn't a release version, so a stray tag never prompts an update. */
    fun parse(raw: String): AppRelease? {
        val dto = try {
            json.decodeFromString(ReleaseDto.serializer(), raw)
        } catch (e: SerializationException) {
            throw ReleaseException("Unexpected release JSON", e)
        } catch (e: IllegalArgumentException) {
            throw ReleaseException("Unexpected release JSON", e)
        }
        if (dto.draft || dto.prerelease) return null
        val version = AppVersion.parse(dto.tagName) ?: return null
        return AppRelease(
            version = version,
            pageUrl = dto.htmlUrl,
            apkUrl = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.downloadUrl,
        )
    }

    @Serializable
    private class ReleaseDto(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private class AssetDto(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )
}
