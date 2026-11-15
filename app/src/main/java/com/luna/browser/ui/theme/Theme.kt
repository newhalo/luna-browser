package com.luna.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Colour tokens ----
// Text, danger and success stay the same in every theme (readability); the rest follows the active [Palette].
// These are getters over Compose state, so a theme change repaints whatever read them.
val Bg: Color get() = Themes.current.bg
val Surface: Color get() = Themes.current.surface
val SurfaceHigh: Color get() = Themes.current.surfaceHigh
val SurfaceFocus: Color get() = Themes.current.surfaceFocus
val Outline = Color(0x22FFFFFF)
val Accent: Color get() = Themes.current.accent
val AccentDeep: Color get() = Themes.current.accentDeep
val TextPrimary = Color(0xFFF3F5F9)
val TextSecondary = Color(0xFFA9B1C1)
val Danger = Color(0xFFFF8A8A)
val Success = Color(0xFF6EE7A8)
val FocusRing = Color.White
val Scrim: Color get() = Themes.current.bg.copy(alpha = 0.9f)
/** Translucent surface for controls that sit on top of artwork. */
val Glass = Color(0x2EFFFFFF)

/** Layout tokens. Designed on a 960 x 540 dp canvas (1080p and 4K both report this on Android TV). */
object Dim {
    val SafeH = 48.dp
    val SafeV = 27.dp
    val Gap = 16.dp
    val Radius = 16.dp
    val RowHeight = 76.dp
}

/** Type scale, tuned for ~3 m viewing distance. */
object T {
    val display = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    val title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    val body = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    val label = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    val caption = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = TextSecondary)
}

val LocalReduceMotion = compositionLocalOf { false }

/** Duration helper: animations are 100-250 ms, or instant when reduced motion is on. */
@Composable
fun ms(value: Int): Int = if (LocalReduceMotion.current) 0 else value

@Composable
fun LunaTheme(themeId: String, reduceMotion: Boolean, content: @Composable () -> Unit) {
    // Selected before any child reads a colour token (a write that no earlier read in this composition saw).
    remember(themeId) { Themes.select(themeId) }
    androidx.compose.runtime.CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent, background = Bg, surface = Surface,
                onPrimary = Bg, onBackground = TextPrimary, onSurface = TextPrimary,
            ),
            content = content,
        )
    }
}
