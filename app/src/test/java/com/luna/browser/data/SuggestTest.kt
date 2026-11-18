package com.luna.browser.data

import com.luna.browser.data.Suggest.Entry
import com.luna.browser.data.Suggest.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {
    private val yt = Entry("YouTube", "https://www.youtube.com/", Kind.History, visits = 30, order = 5)
    private val wiki = Entry("Big Buck Bunny - Wikipedia", "https://en.wikipedia.org/wiki/Big_Buck_Bunny", Kind.History, visits = 3, order = 0)
    private val fav = Entry("Phim hoạt hình", "https://example.org/phim", Kind.Favorite)
    private val tab = Entry("Elephants Dream", "https://en.wikipedia.org/wiki/Elephants_Dream", Kind.Tab)

    @Test fun anEmptyQuerySuggestsNothing() {
        assertTrue(Suggest.rank("  ", listOf(yt, wiki)).isEmpty())
    }

    @Test fun theStartOfASiteNameComesFirst() {
        assertEquals(listOf(yt), Suggest.rank("you", listOf(wiki, yt)))
        assertEquals(yt, Suggest.rank("www.yout", listOf(wiki, yt)).first())
    }

    @Test fun anyPartOfTheSiteNameMatches() {
        assertEquals(wiki, Suggest.rank("wiki", listOf(yt, wiki)).first())
    }

    @Test fun titleWordsMatchInAnyOrderWithoutAccents() {
        assertEquals(listOf(wiki), Suggest.rank("buck big", listOf(yt, wiki, fav)))
        assertEquals(listOf(fav), Suggest.rank("hoat hinh", listOf(yt, wiki, fav)))
    }

    @Test fun openTabsAndFavouritesComeBeforeHistoryAmongEquals() {
        val r = Suggest.rank("wikipedia", listOf(wiki, tab))
        assertEquals(tab, r.first())
    }

    @Test fun oneSuggestionPerAddress() {
        val again = wiki.copy(kind = Kind.Favorite, url = wiki.url + "/")
        assertEquals(1, Suggest.rank("bunny", listOf(wiki, again)).size)
    }

    @Test fun hostsLoseWwwAndMobilePrefixes() {
        assertEquals("youtube.com", Suggest.host("https://m.youtube.com/watch?v=1"))
        assertEquals("example.org", Suggest.host("http://www.Example.org:8080/a"))
    }

    @Test fun foldingDropsVietnameseMarks() {
        assertEquals("duong pho", Suggest.fold("Đường Phố"))
    }
}
