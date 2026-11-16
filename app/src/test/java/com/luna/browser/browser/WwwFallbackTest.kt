package com.luna.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WwwFallbackTest {
    @Test fun addsWwwToABareDomain() {
        assertEquals("https://www.vn2c.my/", WwwFallback.alternative("https://vn2c.my/"))
        assertEquals("http://www.example.com/a/b?q=1#x", WwwFallback.alternative("http://example.com/a/b?q=1#x"))
    }

    @Test fun keepsThePortAndUserInfo() {
        assertEquals("https://www.example.com:8443/p", WwwFallback.alternative("https://example.com:8443/p"))
    }

    @Test fun worksForTwoPartSuffixes() {
        assertEquals("https://www.example.com.vn/", WwwFallback.alternative("https://example.com.vn/"))
        assertEquals("https://www.bbc.co.uk/news", WwwFallback.alternative("https://bbc.co.uk/news"))
    }

    @Test fun leavesEverythingElseAlone() {
        assertNull(WwwFallback.alternative("https://www.example.com/"))
        assertNull(WwwFallback.alternative("https://m.example.com/"))
        assertNull(WwwFallback.alternative("https://api.example.co.uk/"))
        assertNull(WwwFallback.alternative("http://192.168.1.2/"))
        assertNull(WwwFallback.alternative("http://localhost:8080/"))
        assertNull(WwwFallback.alternative("data:text/html,hi"))
        assertNull(WwwFallback.alternative("not a url"))
    }

    @Test fun theAlternativeHasNoFurtherAlternative() {
        val alt = WwwFallback.alternative("https://vn2c.my")!!
        assertNull(WwwFallback.alternative(alt))
    }
}
