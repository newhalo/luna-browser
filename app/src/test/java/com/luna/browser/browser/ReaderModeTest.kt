package com.luna.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModeTest {
    private val longText = "Lorem ipsum dolor sit amet consectetur adipiscing elit. ".repeat(12)   // ~670 chars

    private fun json(vararg blocks: String, title: String = "A title") =
        """{"title":"$title","site":"example.com","blocks":[${blocks.joinToString(",")}]}"""

    private fun p(t: String) = """{"k":"p","t":"$t"}"""

    @Test fun parsesWhatThePageReturns() {
        val raw = json(p(longText), """{"k":"h2","t":"Section"}""", """{"k":"li","t":"one"}""", """{"k":"li","t":"two"}""")
        val a = ReaderMode.parse(raw)
        assertNotNull(a)
        assertEquals("A title", a!!.title)
        assertEquals("example.com", a.site)
        assertEquals(listOf("p", "h2", "li", "li"), a.blocks.map { it.kind })
    }

    @Test fun parsesTheQuotedStringEvaluateJavascriptReturns() {
        val inner = json(p(longText), p(longText), p("third"))
        val quoted = org.json.JSONObject.quote(inner)           // what evaluateJavascript gives for a returned string
        assertNotNull(ReaderMode.parse(quoted))
    }

    @Test fun noArticleMeansNull() {
        assertNull(ReaderMode.parse(null))
        assertNull(ReaderMode.parse("null"))
        assertNull(ReaderMode.parse("\"null\""))
        assertNull(ReaderMode.parse("not json"))
        assertNull("too little text", ReaderMode.parse(json(p("short"), p("shorter"), p("tiny"))))
        assertNull("too few blocks", ReaderMode.parse(json(p(longText.repeat(2)))))
    }

    @Test fun unknownKindsAndBadImagesAreDropped() {
        val raw = json(
            p(longText), p(longText), p("third paragraph"),
            """{"k":"script","t":"alert(1)"}""",
            """{"k":"img","t":"cap","u":"javascript:alert(1)"}""",
            """{"k":"img","t":"cap","u":"file:///sdcard/x.png"}""",
            """{"k":"img","t":"ok","u":"https://example.com/a.jpg"}""",
        )
        val a = ReaderMode.parse(raw)!!
        assertEquals(listOf("p", "p", "p", "img"), a.blocks.map { it.kind })
        assertEquals("https://example.com/a.jpg", a.blocks.last().url)
    }

    @Test fun htmlIsEscapedSoThePageCannotInjectMarkup() {
        val evil = "<script>alert(1)</script> & \"quotes\" 'single'"
        val a = ReaderMode.parse(json(p(longText), """{"k":"quote","t":${org.json.JSONObject.quote(evil)}}""", p("third"), title = "<img src=x onerror=alert(1)>"))!!
        val html = ReaderMode.html(a, "5 min <read>")
        assertFalse(html.contains("<script>alert"))
        assertFalse(html.contains("<img src=x"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quotes&quot; &#39;single&#39;"))
        assertTrue(html.contains("5 min &lt;read&gt;"))
    }

    @Test fun imageAddressesCannotBreakOutOfTheAttribute() {
        val raw = json(p(longText), p("two"), p("three"), """{"k":"img","t":"","u":"https://e.com/a.jpg\" onerror=\"alert(1)"}""")
        val html = ReaderMode.html(ReaderMode.parse(raw)!!, "1 min")
        assertFalse(html.contains("\" onerror=\""))
        assertTrue(html.contains("&quot; onerror=&quot;"))
    }

    @Test fun listItemsAreGroupedAndClosed() {
        val a = ReaderMode.parse(json(p(longText), """{"k":"li","t":"a"}""", """{"k":"li","t":"b"}""", p("after"), """{"k":"li","t":"c"}"""))!!
        val html = ReaderMode.html(a, "1 min")
        assertTrue(html.contains("<ul><li>a</li><li>b</li></ul>"))
        assertTrue(html.contains("<ul><li>c</li></ul>"))
        assertEquals(html.split("<ul>").size, html.split("</ul>").size)
    }

    @Test fun readingTime() {
        val words = (1..800).joinToString(" ") { "word" }
        val a = ReaderMode.parse(json(p(words), p("x y z"), p("and more words to keep it above the limit of five hundred characters in total: " + "x ".repeat(100))))!!
        assertEquals(5, ReaderMode.minutes(a))
        val short = ReaderMode.parse(json(p(longText), p("two"), p("three")))!!
        assertEquals(1, ReaderMode.minutes(short))
    }

    @Test fun cjkTextWithoutSpacesStillGetsAReadingTime() {
        val han = "这是一个很长的段落".repeat(120)      // 1080 chars, no spaces
        val a = ReaderMode.parse(json(p(han), p(han), p(han)))!!
        assertTrue(ReaderMode.minutes(a) >= 2)
    }

    @Test fun extractionScriptIsSelfContained() {
        // The script is injected as one expression: it must be an IIFE that returns JSON text or null.
        val js = ReaderMode.EXTRACT_JS
        assertTrue(js.startsWith("(function(){"))
        assertTrue(js.endsWith("})()"))
        assertFalse("must not touch the network", js.contains("fetch(") || js.contains("XMLHttpRequest"))
        assertFalse("must not eval", js.contains("eval(") || js.contains("Function("))
    }
}
