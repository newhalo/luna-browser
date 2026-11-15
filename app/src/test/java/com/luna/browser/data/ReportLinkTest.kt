package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLinkTest {
    @Test
    fun queryFragmentAndUserInfoNeverReachTheReport() {
        val page = ReportLink.cleanPage("https://user:secret@example.com/watch/ep-1?token=abc&q=private#t=30")
        assertEquals("https://example.com/watch/ep-1", page)
    }

    @Test
    fun keepsPortAndPath() {
        assertEquals("http://192.168.1.5:8080/a/b", ReportLink.cleanPage("http://192.168.1.5:8080/a/b?x=1"))
    }

    @Test
    fun onlyWebPagesAreReportable() {
        assertNull(ReportLink.cleanPage("file:///sdcard/secret.html"))
        assertNull(ReportLink.cleanPage("javascript:alert(1)"))
        assertNull(ReportLink.cleanPage("not a url"))
        assertNull(ReportLink.cleanPage(""))
        assertNull(ReportLink.build("about:blank", "x"))
    }

    @Test
    fun buildsAPrefilledIssueAddress() {
        val link = ReportLink.build("https://example.com/p?secret=1", "Luna 0.5.0 · WebView 131")
        assertNotNull(link)
        link!!
        assertTrue(link.startsWith("https://github.com/newhalo/luna-browser/issues/new?template=ad_report.yml"))
        assertTrue(link.contains("url=https%3A%2F%2Fexample.com%2Fp"))
        assertFalse(link.contains("secret"))
        assertTrue(link.contains("version=Luna%200.5.0"))
        assertFalse("spaces must be %20, not +", link.contains("+"))
    }

    @Test
    fun veryLongPathsAreCut() {
        val page = ReportLink.cleanPage("https://example.com/" + "a".repeat(500))!!
        assertTrue(page.length <= 180)
    }

    @Test
    fun environmentSkipsMissingParts() {
        assertEquals("Luna 1.0", ReportLink.environment("1.0", null, null, ""))
        assertEquals("Luna 1.0 · WebView 9 · filter lists 2026-01-01 · TV", ReportLink.environment("1.0", "9", "2026-01-01", "TV"))
    }
}
