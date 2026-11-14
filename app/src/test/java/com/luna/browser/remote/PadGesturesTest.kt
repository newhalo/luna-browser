package com.luna.browser.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadGesturesTest {
    @Test fun parsesAndClampsMovements() {
        assertEquals(12 to -7, PadGestures.parse("12,-7"))
        assertEquals(1000 to -1000, PadGestures.parse("99999, -99999"))
        assertNull(PadGestures.parse("12"))
        assertNull(PadGestures.parse("a,b"))
        assertNull(PadGestures.parse("1,2,3"))
        assertNull(PadGestures.parse(""))
    }

    @Test fun smallMovementsAccumulateIntoOnePress() {
        val s = SwipeSteps(step = 100)
        assertEquals(emptyList<Char>(), s.feed(40, 0))
        assertEquals(emptyList<Char>(), s.feed(40, 0))
        assertEquals(listOf('R'), s.feed(40, 0))        // 120 >= 100, 20 carried over
        assertEquals(listOf('R'), s.feed(80, 0))        // 20 + 80
    }

    @Test fun longSwipesRepeatAndDirectionsAreRight() {
        val s = SwipeSteps(step = 100)
        assertEquals(listOf('L', 'L', 'L'), s.feed(-350, 0))
        assertEquals(listOf('D', 'D'), s.feed(0, 230))
        assertEquals(listOf('U'), s.feed(0, -130))      // the 30 left over from before + 130 = 100 upwards
    }

    @Test fun aPressOnOneAxisForgetsTheOther() {
        val s = SwipeSteps(step = 100)
        assertEquals(emptyList<Char>(), s.feed(0, 90))   // almost a "down"
        assertEquals(listOf('R'), s.feed(110, 0))        // a clear "right": the vertical drift is dropped
        assertEquals(emptyList<Char>(), s.feed(0, 20))   // 20, not 110
    }

    @Test fun neverFloodsTheTv() {
        assertEquals(4, SwipeSteps(step = 10).feed(1000, 0).size)
    }

    @Test fun resetClearsLeftovers() {
        val s = SwipeSteps(step = 100)
        s.feed(90, 0)
        s.reset()
        assertEquals(emptyList<Char>(), s.feed(20, 0))
    }
}
