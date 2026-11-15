package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun parsesTagsAndBuildNames() {
        assertEquals(listOf(0, 4, 1), UpdateCheck.parse("v0.4.1"))
        assertEquals(listOf(0, 4, 1), UpdateCheck.parse("0.4.1-debug"))
        assertEquals(listOf(1, 10), UpdateCheck.parse("1.10"))
        assertNull(UpdateCheck.parse("latest"))
        assertNull(UpdateCheck.parse(""))
        assertNull(UpdateCheck.parse(null))
    }

    @Test fun comparesNumbersNotText() {
        assertTrue(UpdateCheck.isNewer("0.10.0", "0.9.9"))
        assertTrue(UpdateCheck.isNewer("v0.5.0", "0.4.1-debug"))
        assertTrue(UpdateCheck.isNewer("1.0", "0.99.99"))
        assertFalse(UpdateCheck.isNewer("0.4.1", "0.4.1"))
        assertFalse(UpdateCheck.isNewer("0.4.0", "0.4.1"))
        assertFalse(UpdateCheck.isNewer("0.4", "0.4.0"))
        assertFalse("junk is never newer", UpdateCheck.isNewer("banana", "0.4.1"))
        assertFalse(UpdateCheck.isNewer("0.5.0", null))
    }

    @Test fun readsTheLatestReleaseOfThisProjectOnly() {
        val ok = """{"tag_name":"v0.5.0","html_url":"https://github.com/newhalo/luna-browser/releases/tag/v0.5.0","draft":false,"prerelease":false}"""
        val r = UpdateCheck.fromJson(ok)
        assertNotNull(r)
        assertEquals("0.5.0", r!!.version)
        assertTrue(r.url.startsWith("https://github.com/newhalo/luna-browser/"))

        assertNull("a page on another site must be refused", UpdateCheck.fromJson("""{"tag_name":"v9.9.9","html_url":"https://evil.example/luna.apk"}"""))
        assertNull(UpdateCheck.fromJson("""{"tag_name":"v9.9.9","html_url":"https://github.com/someone-else/luna-browser/releases"}"""))
        assertNull(UpdateCheck.fromJson("""{"tag_name":"v0.6.0-rc1","html_url":"https://github.com/newhalo/luna-browser/releases/tag/x","prerelease":true}"""))
        assertNull(UpdateCheck.fromJson("""{"tag_name":"v0.6.0","html_url":"https://github.com/newhalo/luna-browser/releases/tag/x","draft":true}"""))
        assertNull(UpdateCheck.fromJson("not json"))
        assertNull(UpdateCheck.fromJson("""{"message":"Not Found"}"""))
    }

    @Test fun storeInstallsAreLeftToTheStore() {
        assertTrue(UpdateCheck.isStoreInstall("com.android.vending"))
        assertTrue(UpdateCheck.isStoreInstall("org.fdroid.fdroid"))
        assertFalse(UpdateCheck.isStoreInstall(null))
        assertFalse(UpdateCheck.isStoreInstall("com.google.android.packageinstaller"))
        assertFalse(UpdateCheck.isStoreInstall("com.example.downloader"))
    }
}
