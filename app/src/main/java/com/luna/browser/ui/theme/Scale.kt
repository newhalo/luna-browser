package com.luna.browser.ui.theme

/** The layout is drawn on a canvas that is 960 dp wide (see docs/DESIGN.md). */
const val CANVAS_WIDTH_DP = 960f

/**
 * Pixels per dp that make the screen's longer side exactly [CANVAS_WIDTH_DP] dp wide.
 *
 * Most Android TVs already report 960 dp (720p at 1.33x, 1080p at 2x, 4K at 4x), but some use another
 * density — a 4K panel left at 2x reports 1920 dp and a UI half the intended size. Normalising here keeps
 * every screen, font and focus ring the same physical size on any TV, resolution or density setting.
 */
fun canvasDensity(widthPx: Float, heightPx: Float): Float =
    (maxOf(widthPx, heightPx) / CANVAS_WIDTH_DP).coerceIn(0.5f, 8f)
