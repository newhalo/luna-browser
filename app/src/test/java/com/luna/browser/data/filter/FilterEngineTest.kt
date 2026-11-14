package com.luna.browser.data.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behaviour of the filter engine on real-world rule shapes (taken from ABPVN and luna.txt).
 * Add a case here whenever a site-specific rule is added or a parsing bug is fixed.
 */
class FilterEngineTest {
    private fun engine(vararg lists: String) = FilterBuilder().apply { lists.forEach { add(it) } }.build()

    private val page = "https://www.example-videos.test/watch/episode-1"

    @Test fun hostRuleBlocksDomainAndSubdomains() {
        val e = engine("||popunder-ads.test^")
        assertNotNull(e.match("https://jf.popunder-ads.test/rFN7OyXOXwvMkU/43077", page, Type.AMBIGUOUS))
        assertNull(e.match("https://notpopunder-ads.test/x.js", page, Type.SCRIPT))
    }

    @Test fun hostsFileFormat() {
        val e = engine("# comment\n0.0.0.0 ads.example.com\n127.0.0.1 track.example.net\nplain.example.org")
        assertNotNull(e.match("https://ads.example.com/a.js", page, Type.SCRIPT))
        assertNotNull(e.match("https://x.track.example.net/p", page, Type.AMBIGUOUS))
        assertNotNull(e.match("https://plain.example.org/", page, Type.IMAGE))
        assertNull(e.match("https://example.com/", page, Type.IMAGE))
    }

    @Test fun pathPatternWithTypeAndDomain() {
        val e = engine("/vast.js\$script,domain=boctem.org|phimdacap.tv")
        assertNotNull(e.match("https://cdn.x.com/player/vast.js", "https://phimdacap.tv/xem/1", Type.SCRIPT))
        assertNull(e.match("https://cdn.x.com/player/vast.js", "https://other.tv/", Type.SCRIPT)) // wrong site
        assertNull(e.match("https://cdn.x.com/player/vast.js", "https://phimdacap.tv/", Type.IMAGE)) // wrong type
    }

    @Test fun wildcardAndSeparator() {
        val e = engine("||ghienphimz.mobi^*.gif\$image")
        assertNotNull(e.match("https://ghienphimz.mobi/banners/top.gif", "https://ghienphimz.mobi/", Type.IMAGE))
        assertNull(e.match("https://ghienphimz.mobi/poster.jpg", "https://ghienphimz.mobi/", Type.IMAGE))
    }

    @Test fun partialPathRule() {
        val e = engine(".com/qc/")
        assertNotNull(e.match("https://news.example.com/qc/banner.png", page, Type.IMAGE))
        assertNull(e.match("https://news.example.com/qcm/banner.png", page, Type.IMAGE))
    }

    @Test fun thirdPartyOption() {
        val e = engine("||cdn.ads.vn^\$third-party")
        assertNotNull(e.match("https://cdn.ads.vn/a.js", page, Type.SCRIPT))
        assertNull(e.match("https://cdn.ads.vn/a.js", "https://ads.vn/", Type.SCRIPT))
    }

    @Test fun exceptionWins() {
        val e = engine("||cdn.example.com^", "@@||cdn.example.com/player/\$script")
        assertNotNull(e.match("https://cdn.example.com/ads.js", page, Type.SCRIPT))
        assertNull(e.match("https://cdn.example.com/player/main.js", page, Type.SCRIPT))
    }

    @Test fun documentAllowlistDisablesBlocking() {
        val e = engine("||doubleclick.net^", "@@||mybank.vn^\$document")
        assertNull(e.match("https://doubleclick.net/x", "https://mybank.vn/login", Type.SCRIPT))
        assertNotNull(e.match("https://doubleclick.net/x", page, Type.SCRIPT))
    }

    @Test fun popupRules() {
        val e = engine("\$popup,domain=player-cdn.com", "||betting.example^\$popup")
        assertNotNull(e.match("https://anything.com/", "https://player-cdn.com/embed/1", Type.POPUP))
        assertNotNull(e.match("https://betting.example/landing", page, Type.POPUP))
        assertNull(e.match("https://anything.com/", page, Type.POPUP))
        // $popup rules never block ordinary requests
        assertNull(e.match("https://betting.example/landing", page, Type.SCRIPT))
    }

    @Test fun regexRule() {
        val e = engine("/^https:\\/\\/[a-z]{3,}\\.com\\/\$/\$xmlhttprequest,third-party")
        assertNotNull(e.match("https://abcdef.com/", page, Type.XHR))
        assertNull(e.match("https://abcdef.com/path", page, Type.XHR))
    }

    @Test fun unsupportedOptionsAreSkippedNotOverBlocking() {
        val e = engine("||example.com^\$csp=script-src 'none'", "||example.org^\$removeparam=utm")
        assertNull(e.match("https://example.com/", page, Type.SCRIPT))
        assertNull(e.match("https://example.org/?utm=1", page, Type.SCRIPT))
    }

    @Test fun cosmeticGenericSpecificAndExceptions() {
        val e = engine(
            "##.catfish\n##.banner-ads\nexample-videos.test###toggle_hiddenads\n~safe.vn##.promo\n" +
                "safe.vn#@#.banner-ads\nmetruyencv.com#?#.x:-abp-has(small)",
        )
        val css = e.css(page)
        assertTrue(css.contains(".catfish{display:none!important}"))
        assertTrue(css.contains("#toggle_hiddenads"))
        assertTrue(css.contains(".promo"))
        val safe = e.css("https://safe.vn/")
        assertFalse(safe.contains(".banner-ads"))
        assertFalse(safe.contains(".promo"))
        assertFalse(safe.contains("-abp-has"))
    }

    @Test fun entityDomains() {
        val e = engine("||ads.cdn.net^\$domain=phimmoi.*", "phimmoi.*##.ads-top")
        assertNotNull(e.match("https://ads.cdn.net/x.js", "https://phimmoi.net/", Type.SCRIPT))
        assertNotNull(e.match("https://ads.cdn.net/x.js", "https://www.phimmoi.cc/", Type.SCRIPT))
        assertTrue(e.css("https://phimmoi.ink/").contains(".ads-top"))
    }

    @Test fun bundledLunaListParses() {
        val f = File("src/main/assets/filters/luna.txt")
        if (!f.exists()) return
        val b = FilterBuilder()
        val n = b.add(f.readText())
        assertTrue("luna.txt should contain rules", n > 50)
        val e = b.build()
        // A pop-under network that the bundled list must always block.
        assertNotNull(e.match("https://jf.bolasarpen.com/abc", page, Type.AMBIGUOUS))
        
    }

    @Test fun siteOfHandlesVietnameseSecondLevel() {
        assertEquals("abc.com.vn", siteOf("phim.abc.com.vn"))
        assertEquals("example-videos.test", siteOf("www.example-videos.test"))
    }
}
