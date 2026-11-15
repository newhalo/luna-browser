package com.luna.browser.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test fun idsAreUniqueAndTheFirstIsTheDefault() {
        assertEquals(Themes.all.size, Themes.all.map { it.id }.toSet().size)
        assertEquals(Themes.DEFAULT, Themes.all.first().id)
    }

    @Test fun unknownIdFallsBackToTheDefault() {
        assertSame(Themes.all.first(), Themes.byId("nope"))
        assertSame(Themes.all.first(), Themes.byId(null))
        assertSame(Themes.all.first(), Themes.byId(""))
    }

    @Test fun everyThemeIsDarkAndReadable() {
        for (p in Themes.all) {
            assertTrue("${p.id}: background must be dark", p.bg.luminance() < 0.03f)
            assertTrue("${p.id}: accent on background", contrast(p.accent, p.bg) >= 7f)
            assertTrue("${p.id}: accent on the focused surface", contrast(p.accent, p.surfaceFocus) >= 4.5f)
            assertTrue("${p.id}: text on the focused surface", contrast(TextPrimary, p.surfaceFocus) >= 7f)
            assertTrue("${p.id}: secondary text on surface", contrast(TextSecondary, p.surfaceHigh) >= 4.5f)
        }
    }

    @Test fun surfacesGetLighterStepByStep() {
        for (p in Themes.all) {
            assertTrue(p.id, p.bg.luminance() <= p.surface.luminance())
            assertTrue(p.id, p.surface.luminance() < p.surfaceHigh.luminance())
            assertTrue(p.id, p.surfaceHigh.luminance() < p.surfaceFocus.luminance())
        }
    }

    @Test fun movingThemesHaveSomethingToDraw() {
        for (p in Themes.all) if (p.motion != Motion.Still) assertTrue(p.id, p.lights.isNotEmpty())
        assertEquals(Motion.Still, Themes.byId("graphite").motion)
    }

    @Test fun pingPongIsATriangle() {
        assertEquals(0f, pingPong(0f), 1e-6f)
        assertEquals(0.5f, pingPong(0.5f), 1e-6f)
        assertEquals(1f, pingPong(1f), 1e-6f)
        assertEquals(0.5f, pingPong(1.5f), 1e-6f)
        assertEquals(0f, pingPong(2f), 1e-6f)
        assertEquals(0.25f, pingPong(4.25f), 1e-6f)
        assertEquals(0.5f, pingPong(-0.5f), 1e-6f)
    }
}
