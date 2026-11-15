package com.luna.browser.data

import com.luna.browser.data.WebViewCheck.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewCheckTest {
    @Test fun readsTheMajorVersion() {
        assertEquals(157, WebViewCheck.major("157.0.8103.38"))
        assertEquals(88, WebViewCheck.major(" 88.0.4324.181 "))
        assertEquals(120, WebViewCheck.major("120"))
        assertNull(WebViewCheck.major(null))
        assertNull(WebViewCheck.major(""))
        assertNull(WebViewCheck.major("unknown"))
        assertNull(WebViewCheck.major("0.1"))
    }

    @Test fun classifiesTheEngine() {
        assertEquals(Level.Ok, WebViewCheck.level("157.0.8103.38"))
        assertEquals(Level.Ok, WebViewCheck.level("110.0.5481.65"))
        assertEquals(Level.Old, WebViewCheck.level("109.0.5414.86"))
        assertEquals(Level.Old, WebViewCheck.level("90.0.4430.91"))
        assertEquals(Level.VeryOld, WebViewCheck.level("89.0.4389.105"))
        assertEquals(Level.VeryOld, WebViewCheck.level("68.0.3440.91"))
        assertEquals(Level.Unknown, WebViewCheck.level(null))
        assertEquals(Level.Unknown, WebViewCheck.level("n/a"))
    }

    @Test fun onlyOldEnginesNeedAttention() {
        assertTrue(WebViewCheck.needsAttention(Level.Old))
        assertTrue(WebViewCheck.needsAttention(Level.VeryOld))
        assertFalse(WebViewCheck.needsAttention(Level.Ok))
        assertFalse(WebViewCheck.needsAttention(Level.Unknown))
    }
}
