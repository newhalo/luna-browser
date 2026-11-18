package com.luna.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoRulesTest {
    @Test fun speedStepsUpAndWrapsToTheSlowest() {
        assertEquals(1.25, VideoRules.nextSpeed(1.0), 0.0)
        assertEquals(2.0, VideoRules.nextSpeed(1.75), 0.0)
        assertEquals(0.5, VideoRules.nextSpeed(2.0), 0.0)
        // A rate the page set itself (not in the list) starts again from the slowest.
        assertEquals(0.5, VideoRules.nextSpeed(1.1), 0.0)
    }

    @Test fun speedLabelsAreShort() {
        assertEquals("1×", VideoRules.speedLabel(1.0))
        assertEquals("2×", VideoRules.speedLabel(2.0))
        assertEquals("1.25×", VideoRules.speedLabel(1.25))
        assertEquals("0.5×", VideoRules.speedLabel(0.5))
    }

    @Test fun numberKeysOfRemotesAndNumberPads() {
        assertEquals(0, VideoRules.digit(7))      // KEYCODE_0
        assertEquals(9, VideoRules.digit(16))     // KEYCODE_9
        assertEquals(5, VideoRules.digit(149))    // KEYCODE_NUMPAD_5
        assertNull(VideoRules.digit(21))          // DPAD_LEFT
    }

    @Test fun numberKeysJumpLikeYouTube() {
        assertEquals(0.0, VideoRules.percentTarget(0, 600.0)!!, 0.0)
        assertEquals(300.0, VideoRules.percentTarget(5, 600.0)!!, 0.0)
        assertEquals(540.0, VideoRules.percentTarget(9, 600.0)!!, 0.0)
        assertNull(VideoRules.percentTarget(5, 0.0))   // live stream: no length
    }

    @Test fun buttonSelectionStopsAtBothEnds() {
        assertEquals(VideoRules.Button.Play.ordinal, VideoRules.firstButton)
        assertEquals(0, VideoRules.move(0, -1))
        assertEquals(VideoRules.Button.entries.lastIndex, VideoRules.move(VideoRules.Button.entries.lastIndex, 1))
        assertEquals(2, VideoRules.move(1, 1))
    }
}
