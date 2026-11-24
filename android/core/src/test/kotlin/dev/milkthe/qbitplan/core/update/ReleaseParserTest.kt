package dev.milkthe.qbitplan.core.update

import dev.milkthe.qbitplan.core.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReleaseParserTest {

    @Test
    fun `parses version, page and apk download`() {
        val release = assertNotNull(ReleaseParser.parse(Fixtures.read("github/latest_release.json")))

        assertEquals(AppVersion(1, 0, 2), release.version)
        assertEquals("https://github.com/MILKv2/QbitPlan/releases/tag/v1.0.2", release.pageUrl)
        assertEquals("https://github.com/MILKv2/QbitPlan/releases/download/v1.0.2/E-Plan-v1.0.2.apk", release.apkUrl)
    }

    @Test
    fun `release without an apk still points to its page`() {
        val release = assertNotNull(ReleaseParser.parse(release(tag = "v1.1.0", assets = """[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"}]""")))
        assertEquals(AppVersion(1, 1, 0), release.version)
        assertNull(release.apkUrl)
    }

    @Test
    fun `ignores tags that aren't versions, drafts and prereleases`() {
        assertNull(ReleaseParser.parse(release(tag = "nightly")))
        assertNull(ReleaseParser.parse(release(tag = "v2.0.0", extra = ""","draft":true""")))
        assertNull(ReleaseParser.parse(release(tag = "v2.0.0", extra = ""","prerelease":true""")))
    }

    @Test
    fun `unexpected bodies become ReleaseException`() {
        assertFailsWith<ReleaseException> { ReleaseParser.parse("""{"message":"API rate limit exceeded"}""") }
        assertFailsWith<ReleaseException> { ReleaseParser.parse("<html>") }
    }

    private fun release(tag: String, assets: String = "[]", extra: String = "") =
        """{"tag_name":"$tag","html_url":"https://github.com/MILKv2/QbitPlan/releases/tag/$tag","assets":$assets$extra}"""
}
