package com.luna.browser.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** How a theme's backdrop moves. All of them are painted into one small bitmap (see `Aurora`), never as GPU layers. */
enum class Motion {
    /** Slow drifting colour lights. */
    Drift,
    /** Drifting lights plus warm sparks rising slowly. */
    Embers,
    /** Drifting lights plus softly blinking, wandering dots. */
    Fireflies,
    /** Layered slow waves along the lower half. */
    Waves,
    /** A faint glow and twinkling stars. */
    Stars,
    /** Nothing moves: a plain background (cheapest, and AMOLED-black for Graphite). */
    Still,
}

/** One drifting light: centre and radius in dp on the 960×540 dp canvas, drift (dx, dy) over a sweep. */
class ThemeLight(val color: Color, val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float)

/** Colours and motion of one theme. [id] is what is stored in the settings. */
class Palette(
    val id: String,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceFocus: Color,
    val accent: Color,
    val accentDeep: Color,
    val lights: List<ThemeLight>,
    val motion: Motion,
    /** Colour of sparks / dots / stars for the motions that have them. */
    val particle: Color = Color.White,
)

object Themes {
    const val DEFAULT = "luna"

    val all: List<Palette> = listOf(
        Palette(
            "luna", Color(0xFF0B0D12), Color(0xFF151922), Color(0xFF1F2530), Color(0xFF2E3748), Color(0xFF8AA4FF), Color(0xFF3B4A8C),
            listOf(
                ThemeLight(Color(0xCC4F63E8), 210f, 110f, 390f, 260f, 140f),
                ThemeLight(Color(0xCC9B3DB5), 880f, 380f, 320f, -200f, 120f),
                ThemeLight(Color(0xCC1A93A6), 410f, 590f, 290f, 300f, -180f),
            ),
            Motion.Drift,
        ),
        Palette(
            "dusk", Color(0xFF130B10), Color(0xFF211620), Color(0xFF2E1F2B), Color(0xFF45303E), Color(0xFFFFAB80), Color(0xFF8C4A3B),
            listOf(
                ThemeLight(Color(0xCCE0562F), 230f, 480f, 400f, 240f, -120f),
                ThemeLight(Color(0xCCB3337A), 800f, 150f, 330f, -180f, 140f),
                ThemeLight(Color(0xCCE8A23A), 520f, 640f, 300f, 220f, -160f),
            ),
            Motion.Embers, particle = Color(0xFFFFC98A),
        ),
        Palette(
            "forest", Color(0xFF09120D), Color(0xFF121C16), Color(0xFF1B2820), Color(0xFF2A3D31), Color(0xFF7EE0A4), Color(0xFF2F6B4A),
            listOf(
                ThemeLight(Color(0xCC2A9A5E), 200f, 160f, 380f, 240f, 120f),
                ThemeLight(Color(0xCC17877F), 860f, 400f, 330f, -220f, -100f),
                ThemeLight(Color(0xCC62A336), 480f, 620f, 280f, 260f, -200f),
            ),
            Motion.Fireflies, particle = Color(0xFFD9F57A),
        ),
        Palette(
            "ocean", Color(0xFF07101A), Color(0xFF0F1A28), Color(0xFF172536), Color(0xFF243A52), Color(0xFF6CCBFF), Color(0xFF1F5F8C),
            listOf(
                ThemeLight(Color(0xB32B6FD1), 480f, 40f, 520f, 160f, 40f),
                ThemeLight(Color(0x9917A6C9), 120f, 360f, 300f, 140f, 60f),
            ),
            Motion.Waves, particle = Color(0xFF6CCBFF),
        ),
        Palette(
            "night", Color(0xFF06070E), Color(0xFF10121C), Color(0xFF191C2A), Color(0xFF272B40), Color(0xFFC4B5FF), Color(0xFF4A3F8C),
            listOf(
                ThemeLight(Color(0x886A4FD1), 720f, 120f, 460f, -160f, 80f),
                ThemeLight(Color(0x663A5FC8), 160f, 440f, 380f, 180f, -80f),
            ),
            Motion.Stars, particle = Color(0xFFF4F1FF),
        ),
        Palette(
            "graphite", Color(0xFF000000), Color(0xFF0E0E11), Color(0xFF18181C), Color(0xFF2A2A30), Color(0xFFE6E8EE), Color(0xFF5A5E6B),
            emptyList(), Motion.Still,
        ),
    )

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: all.first()

    /** The active palette. Compose state: whatever reads a colour token repaints when it changes. */
    var current: Palette by mutableStateOf(all.first())
        private set

    fun select(id: String?) {
        val p = byId(id)
        if (p !== current) current = p
    }
}

/** 0..1..0 triangle over a phase that grows without bound: 0→1 for phase 0..1, back to 0 for 1..2, and so on. */
fun pingPong(phase: Float): Float {
    val m = ((phase % 2f) + 2f) % 2f
    return if (m <= 1f) m else 2f - m
}
