package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSettingsTest {
    @Test fun subdomainsShareASite() {
        assertEquals("example.com", SiteSettings.keyOf("https://www.example.com/a?b=1"))
        assertEquals("example.com", SiteSettings.keyOf("https://m.example.com/"))
        assertEquals("abc.com.vn", SiteSettings.keyOf("https://phim.abc.com.vn/xem/1"))
        assertEquals("", SiteSettings.keyOf("about:blank"))
    }

    @Test fun overridesSurviveAJsonRoundTrip() {
        val map = mapOf(
            "a.com" to SiteOverride(desktop = false, zoom = 150),
            "b.com" to SiteOverride(adblock = false, pointer = true),
            "empty.com" to SiteOverride(),
        )
        val back = SiteSettings.parseAll(SiteSettings.serializeAll(map))
        assertEquals(setOf("a.com", "b.com"), back.keys)           // empty entries are dropped
        assertEquals(SiteOverride(desktop = false, zoom = 150), back["a.com"])
        assertEquals(SiteOverride(adblock = false, pointer = true), back["b.com"])
        assertNull(back["a.com"]!!.adblock)                        // "no opinion" stays null, not false
    }

    @Test fun brokenJsonMeansNoOverrides() {
        assertTrue(SiteSettings.parseAll("not json").isEmpty())
        assertTrue(SiteSettings.parseAll("""{"a.com":{"z":9999}}""").isEmpty())   // out-of-range zoom ignored
    }

    @Test fun choicesStepThroughDefaultAndWrap() {
        val opts = listOf(null, 100, 125)
        assertEquals(100, SiteSettings.next(null, opts))
        assertEquals(125, SiteSettings.next(100, opts))
        assertNull(SiteSettings.next(125, opts))
    }
}
