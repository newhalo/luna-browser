package com.luna.browser.data

import com.luna.browser.data.filter.FilterBuilder
import com.luna.browser.data.filter.ListVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * luna.txt is fetched by every installed app straight from the repository, so a broken edit would reach
 * everyone within a day. These checks run in CI before such an edit can be merged.
 */
class BundledListTest {
    private val text = File("src/main/assets/filters/luna.txt").readText()

    @Test fun hasAVersionHeaderInTheExpectedFormat() {
        val v = ListVersion.of(text)
        assertEquals("expected '! Version: YYYY.MM.DD'", 3, v.size)
        assertTrue(v[0] in 2026..2100 && v[1] in 1..12 && v[2] in 1..31)
    }

    @Test fun parsesIntoARealNumberOfRules() {
        val rules = FilterBuilder().add(text)
        assertTrue("only $rules rules parsed", rules >= 50)
        FilterBuilder().apply { add(text) }.build()   // must not throw
    }

    @Test fun noSuspiciousLines() {
        text.lines().forEachIndexed { i, l ->
            assertTrue("line ${i + 1} is very long", l.length < 2000)
            assertTrue("line ${i + 1}: scripts can't be injected from a list", !l.contains("##+js(") && !l.contains("javascript:"))
        }
    }

    @Test fun theNewerVersionWins() {
        val old = "! Version: 2026.01.05\nrule-old"
        val new = "! Version: 2026.11.15\nrule-new"
        assertEquals(new, ListVersion.newer(old, new))
        assertEquals(new, ListVersion.newer(new, old))        // a newer download is never replaced by an older bundle
        assertEquals(old, ListVersion.newer(old, "no header")) // unversioned bundle: keep the download
        assertEquals("b", ListVersion.newer(null, "b"))
        assertEquals("a", ListVersion.newer("a", null))
        assertNull(ListVersion.newer(null, null))
        assertEquals(listOf(2026, 11, 15), ListVersion.of(new))
        assertTrue(ListVersion.compare(listOf(2026, 11), listOf(2026, 11, 0)) == 0)
    }
}
