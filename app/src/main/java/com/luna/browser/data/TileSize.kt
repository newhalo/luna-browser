package com.luna.browser.data

/**
 * How big the 16:9 thumbnails on Home (and in the History grid) are. Smaller tiles show more at once.
 * The stored value is validated here, so a hand-edited or stale preference can never produce a zero-sized tile.
 */
object TileSize {
    const val LARGE = "large"
    const val MEDIUM = "medium"
    const val SMALL = "small"

    val all = listOf(LARGE, MEDIUM, SMALL)

    /** Factor applied to the base tile widths. */
    fun scale(value: String?): Float = when (value) {
        MEDIUM -> 0.8f
        SMALL -> 0.64f
        else -> 1f
    }

    /** How many tiles of [tileDp] (with [gapDp] between them) fit in [availableDp]; at least one. */
    fun columns(availableDp: Float, tileDp: Float, gapDp: Float): Int =
        if (tileDp <= 0f) 1 else ((availableDp + gapDp) / (tileDp + gapDp)).toInt().coerceAtLeast(1)
}
