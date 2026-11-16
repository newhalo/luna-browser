package com.luna.browser.data

import com.luna.browser.data.AddressInput.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressInputTest {
    private fun k(s: String) = AddressInput.classify(s)

    @Test fun fullAddressesStayAsTyped() {
        assertEquals(Kind.Explicit, k("https://vn2d.my"))
        assertEquals(Kind.Explicit, k("HTTP://example.com/a b"))
        assertEquals(Kind.Explicit, k("ftp://x.y"))
    }

    @Test fun bareSiteNames() {
        assertEquals(Kind.Bare, k("vn2d.my"))
        assertEquals(Kind.Bare, k("example.com/page?q=1"))
        assertEquals(Kind.Bare, k("www.example.com:8443/x"))
        assertEquals(Kind.Bare, k("tuoitre.vn"))
        assertEquals(Kind.Bare, k("đài.vn"))
        assertEquals(Kind.Bare, k("xn--ls8h.xn--p1ai"))
    }

    @Test fun localAddresses() {
        assertEquals(Kind.Local, k("192.168.1.1"))
        assertEquals(Kind.Local, k("192.168.1.1:8080/admin"))
        assertEquals(Kind.Local, k("localhost:3000"))
    }

    @Test fun wordsAreSearched() {
        assertEquals(Kind.Search, k("thượng công chúa"))
        assertEquals(Kind.Search, k("nhạc trẻ"))
        assertEquals(Kind.Search, k("vn2d"))
        assertEquals(Kind.Search, k("what is node.js"))
    }

    @Test fun numbersAndVersionsAreNotSites() {
        assertEquals(Kind.Search, k("3.14"))
        assertEquals(Kind.Search, k("v1.2.3"))
        assertEquals(Kind.Search, k("1.5x"))
        assertEquals(Kind.Search, k("a..b"))
        assertEquals(Kind.Search, k(".com"))
    }

    @Test fun emptyInput() {
        assertEquals(Kind.Empty, k("   "))
    }
}
