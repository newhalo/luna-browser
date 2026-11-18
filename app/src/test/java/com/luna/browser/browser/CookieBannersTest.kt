package com.luna.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieBannersTest {
    @Test fun coversTheCommonConsentTools() {
        listOf("#onetrust-consent-sdk", "#CybotCookiebotDialog", "#usercentrics-root", "#didomi-host", "#qc-cmp2-container")
            .forEach { assertTrue(it, it in CookieBanners.SELECTORS) }
        assertEquals(CookieBanners.SELECTORS.size, CookieBanners.SELECTORS.toSet().size)
    }

    @Test fun onlyHidesNeverClicks() {
        val js = CookieBanners.JS
        assertTrue(js.contains("display:none!important"))
        assertFalse(js.contains(".click("))
        assertFalse(js.contains("accept", ignoreCase = true))
    }

    @Test fun runsOncePerPage() {
        assertTrue(CookieBanners.JS.startsWith("(function(){if(window.__lunaCk)return;"))
        // Run before the document exists, it must not mark itself done (the run at page end has to work).
        val js = CookieBanners.JS
        assertTrue(js.indexOf("if(!root)return;") in 0 until js.indexOf("window.__lunaCk=1"))
    }
}
