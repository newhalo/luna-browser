package com.luna.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRulesTest {
    @Test fun youtubeIsKeyedByVideoIdNotByTrackingParameters() {
        val a = ResumeRules.key("https://www.youtube.com/watch?v=abc123&list=PL1&t=30s")
        val b = ResumeRules.key("https://m.youtube.com/watch?v=abc123")
        val c = ResumeRules.key("https://youtu.be/abc123?si=xyz")
        assertEquals("yt:abc123", a)
        assertEquals(a, b)
        assertEquals(a, c)
        assertTrue(ResumeRules.key("https://www.youtube.com/watch?v=other") != a)
    }

    @Test fun otherSitesKeepPathAndQueryButNotFragmentOrWww() {
        assertEquals(
            ResumeRules.key("https://www.example.com/xem/phim-1?ep=2#player"),
            ResumeRules.key("https://example.com/xem/phim-1?ep=2"),
        )
        assertTrue(ResumeRules.key("https://example.com/xem/phim-1?ep=2") != ResumeRules.key("https://example.com/xem/phim-1?ep=3"))
    }

    @Test fun shortClipsAndTheVeryStartAreNotSaved() {
        assertFalse(ResumeRules.shouldSave(60.0, 90.0))      // a short clip or a pre-roll ad
        assertFalse(ResumeRules.shouldSave(5.0, 3600.0))     // barely started
        assertTrue(ResumeRules.shouldSave(600.0, 3600.0))
    }

    @Test fun aFinishedVideoHasNothingToResume() {
        assertTrue(ResumeRules.finished(3590.0, 3600.0))     // credits
        assertTrue(ResumeRules.finished(3500.0, 3600.0))     // within the last 4%
        assertFalse(ResumeRules.finished(1800.0, 3600.0))
    }

    @Test fun restoreSeeksBackOnlyForTheSameVideoAtTheStart() {
        val saved = Resume(1200.0, 3600.0, 0)
        assertEquals(1200, ResumeRules.restoreDelta(saved, 0.0, 3600.0))
        assertEquals(1195, ResumeRules.restoreDelta(saved, 5.0, 3601.0))   // tiny differences are fine
        assertNull(ResumeRules.restoreDelta(saved, 0.0, 1800.0))           // a different video
        assertNull(ResumeRules.restoreDelta(saved, 400.0, 3600.0))         // the viewer already moved on
        assertNull(ResumeRules.restoreDelta(saved, 0.0, 60.0))             // an ad is playing first
    }

    @Test fun clockFormatsHoursOnlyWhenNeeded() {
        assertEquals("12:34", ResumeRules.clock(754.0))
        assertEquals("1:02:03", ResumeRules.clock(3723.0))
        assertEquals("0:00", ResumeRules.clock(-4.0))
    }

    @Test fun fractionIsClamped() {
        assertEquals(0.5f, Resume(50.0, 100.0, 0).fraction, 0.001f)
        assertEquals(1f, Resume(150.0, 100.0, 0).fraction, 0.001f)
        assertEquals(0f, Resume(10.0, 0.0, 0).fraction, 0.001f)
    }
}
