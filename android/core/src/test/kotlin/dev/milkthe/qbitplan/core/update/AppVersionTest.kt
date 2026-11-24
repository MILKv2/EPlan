package dev.milkthe.qbitplan.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun `parses plain versions and tags`() {
        assertEquals(AppVersion(1, 0, 1), AppVersion.parse("1.0.1"))
        assertEquals(AppVersion(1, 2, 13), AppVersion.parse("v1.2.13"))
        assertEquals("1.2.13", AppVersion.parse("v1.2.13").toString())
    }

    @Test
    fun `rejects anything that isn't a release version`() {
        listOf("", "1.0", "1.0.1-beta", "v1.0.1.2", "latest", "99999999999.0.0").forEach {
            assertNull(AppVersion.parse(it), it)
        }
    }

    @Test
    fun `compares numerically, not as text`() {
        assertTrue(AppVersion(1, 0, 10) > AppVersion(1, 0, 9))
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 99))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertEquals(0, AppVersion(1, 0, 1).compareTo(AppVersion(1, 0, 1)))
    }
}
