package com.luna.browser.browser

import com.luna.browser.browser.TabPolicy.LiveTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabPolicyTest {
    @Test fun noOneIsFrozenWhileThereIsRoom() {
        assertNull(TabPolicy.victim(listOf(LiveTab(0, 10), LiveTab(1, 20)), target = 2, current = 1, maxLive = 3))
        assertNull(TabPolicy.victim(emptyList(), target = 0, current = 0, maxLive = 2))
    }

    @Test fun theLeastRecentlyUsedOtherTabIsFrozen() {
        val live = listOf(LiveTab(0, 300), LiveTab(1, 100), LiveTab(2, 200))
        assertEquals(1, TabPolicy.victim(live, target = 3, current = 0, maxLive = 3))
    }

    @Test fun theTabOnScreenIsKeptEvenIfItIsTheOldest() {
        val live = listOf(LiveTab(0, 1), LiveTab(1, 200), LiveTab(2, 300))
        assertEquals(1, TabPolicy.victim(live, target = 3, current = 0, maxLive = 3))
    }

    @Test fun theTargetItselfCountsAsLiveNotAsAVictim() {
        // Re-selecting a live tab never freezes anything.
        val live = listOf(LiveTab(0, 1), LiveTab(1, 2), LiveTab(2, 3))
        assertNull(TabPolicy.victim(live, target = 1, current = 0, maxLive = 3))
    }

    @Test fun closingKeepsTheRightTabCurrent() {
        assertEquals(1, TabPolicy.indexAfterClose(current = 2, removed = 0, sizeAfter = 3))   // an earlier tab went: shift down
        assertEquals(2, TabPolicy.indexAfterClose(current = 2, removed = 2, sizeAfter = 3))   // the current one: its neighbour
        assertEquals(1, TabPolicy.indexAfterClose(current = 2, removed = 2, sizeAfter = 2))   // it was the last: the new last
        assertEquals(0, TabPolicy.indexAfterClose(current = 0, removed = 2, sizeAfter = 2))   // a later tab went: unchanged
        assertEquals(0, TabPolicy.indexAfterClose(current = 0, removed = 0, sizeAfter = 0))
    }

    @Test fun tabsSurviveAJsonRoundTrip() {
        val tabs = listOf("https://a.com/x" to "A", "" to "", "https://b.com/" to "B \"quoted\"")
        val (back, cur) = TabPolicy.fromJson(TabPolicy.toJson(tabs, 2))
        assertEquals(tabs, back)
        assertEquals(2, cur)
    }

    @Test fun badOrHostileJsonYieldsNothingUsable() {
        assertTrue(TabPolicy.fromJson("nope").first.isEmpty())
        assertTrue(TabPolicy.fromJson(null).first.isEmpty())
        // Only web addresses are restored (no javascript:, file:, intent: ...).
        val (tabs, _) = TabPolicy.fromJson("""{"current":0,"tabs":[{"u":"javascript:alert(1)","t":"x"},{"u":"file:///etc/passwd","t":"y"},{"u":"https://ok.com","t":"z"}]}""")
        assertEquals(listOf("https://ok.com" to "z"), tabs)
        // The current index is clamped into the list.
        assertEquals(1, TabPolicy.fromJson(TabPolicy.toJson(listOf("https://a.com" to "", "https://b.com" to ""), 99)).second)
    }

    @Test fun nothingBeyondTheLimitIsRestored() {
        val many = (1..20).map { "https://s$it.com" to "$it" }
        assertEquals(TabPolicy.MAX_TABS, TabPolicy.fromJson(TabPolicy.toJson(many, 0)).first.size)
    }
}
