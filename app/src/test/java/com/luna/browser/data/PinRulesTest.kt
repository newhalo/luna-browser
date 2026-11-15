package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {
    @Test fun onlyFourDigitsAreValid() {
        assertTrue(PinRules.valid("0000"))
        assertTrue(PinRules.valid("1234"))
        assertFalse(PinRules.valid("123"))
        assertFalse(PinRules.valid("12345"))
        assertFalse(PinRules.valid("12a4"))
        assertFalse(PinRules.valid(""))
        assertFalse(PinRules.valid("１２３４"))   // full-width digits
    }

    @Test fun storedValueNeverContainsThePin() {
        val s = PinRules.encode("4821")
        assertFalse(s.contains("4821"))
        assertTrue(s.matches(Regex("[0-9a-f]{16}:[0-9a-f]{64}")))
    }

    @Test fun verifyAcceptsOnlyTheRightPin() {
        val s = PinRules.encode("4821")
        assertTrue(PinRules.verify("4821", s))
        assertFalse(PinRules.verify("4822", s))
        assertFalse(PinRules.verify("0000", s))
        assertFalse(PinRules.verify("482", s))
        assertFalse(PinRules.verify("4821", null))
        assertFalse(PinRules.verify("4821", ""))
        assertFalse(PinRules.verify("4821", "nocolon"))
        assertFalse(PinRules.verify("4821", ":abc"))
        assertFalse(PinRules.verify("4821", "abc:"))
    }

    @Test fun eachPinGetsItsOwnSalt() {
        assertNotEquals(PinRules.encode("1111"), PinRules.encode("1111"))
        assertEquals(PinRules.encode("1111", "ab12"), PinRules.encode("1111", "ab12"))
    }

    @Test fun waitsGrowAfterRepeatedFailures() {
        for (n in 0..4) assertEquals("try $n", 0, PinRules.lockSeconds(n))
        assertEquals(30, PinRules.lockSeconds(5))
        assertEquals(60, PinRules.lockSeconds(6))
        assertEquals(120, PinRules.lockSeconds(7))
        assertEquals(300, PinRules.lockSeconds(20))
        assertEquals(300, PinRules.lockSeconds(1000))
    }
}
