package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteInputTest {
    @Test fun acceptsWebAddresses() {
        assertEquals("https://example.com/a?b=1", FavoriteInput.address("https://example.com/a?b=1"))
        assertEquals("http://example.com", FavoriteInput.address("  http://example.com  "))
        assertEquals("HTTPS://Example.com", FavoriteInput.address("HTTPS://Example.com"))
        assertEquals("https://example.org", FavoriteInput.address("example.org"))
        assertEquals("https://example.org/page?x=1", FavoriteInput.address("example.org/page?x=1"))
        assertEquals("https://vnexpress.net/tin-moi", FavoriteInput.address("vnexpress.net/tin-moi"))
    }

    @Test fun refusesEverythingElse() {
        assertNull(FavoriteInput.address(""))
        assertNull(FavoriteInput.address("   "))
        assertNull("a search phrase", FavoriteInput.address("phim hay"))
        assertNull("a single word", FavoriteInput.address("phim"))
        assertNull(FavoriteInput.address("javascript:alert(1)"))
        assertNull(FavoriteInput.address("file:///sdcard/x.html"))
        assertNull(FavoriteInput.address("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(FavoriteInput.address("content://media/external/1"))
        assertNull(FavoriteInput.address("ftp://example.com/file"))
        assertNull(FavoriteInput.address("https://exa mple.com"))
        assertNull(FavoriteInput.address("example.com is nice"))
        assertNull(FavoriteInput.address("a".repeat(2001)))
    }
}
