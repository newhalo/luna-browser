package com.luna.browser.browser

import kotlin.math.abs
import kotlin.math.max

/**
 * Where a short arrow press moves the pointer when "pointer snaps to links" is on.
 *
 * Modelled on link browsing in TV browsers and the CSS spatial navigation draft: candidates are measured
 * edge to edge from the item under the pointer (not centre to centre), items in the same row or column win,
 * and only items in a zone near the pointer count. When nothing is near, the caller scrolls or nudges the
 * pointer instead of letting it fly across the page.
 *
 * Rectangles are `[left, top, right, bottom, fixed]` in view pixels; `fixed` is 1 for position:fixed items.
 */
object SnapRules {
    const val RIGHT = 0
    const val LEFT = 1
    const val DOWN = 2
    const val UP = 3

    /** Along the arrow, how far (fraction of the view) the next item may be. */
    const val REACH_H = 0.4f
    const val REACH_V = 0.45f
    /** Sideways off the current row / column, how far (fraction of the view) an item may be. */
    const val SIDE_H = 0.15f
    const val SIDE_V = 0.2f

    /** The smallest item under the point, or the point itself as an empty rectangle. */
    fun origin(list: List<IntArray>, x: Float, y: Float): FloatArray {
        var best: IntArray? = null
        var ba = Long.MAX_VALUE
        for (r in list) {
            if (x < r[0] || x > r[2] || y < r[1] || y > r[3]) continue
            val a = (r[2] - r[0]).toLong() * (r[3] - r[1])
            if (a < ba) { ba = a; best = r }
        }
        val b = best ?: return floatArrayOf(x, y, x, y)
        return floatArrayOf(b[0].toFloat(), b[1].toFloat(), b[2].toFloat(), b[3].toFloat())
    }

    /** The item a press of [dir] should move to, or null when nothing lies in the zone that way. */
    fun pick(list: List<IntArray>, x: Float, y: Float, dir: Int, viewW: Int, viewH: Int, density: Float): IntArray? {
        val o = origin(list, x, y)
        val horizontal = dir == RIGHT || dir == LEFT
        val reach = if (horizontal) viewW * REACH_H else viewH * REACH_V
        val side = if (horizontal) viewH * SIDE_H else viewW * SIDE_V
        val step = 8 * density
        var best: IntArray? = null
        var bestScore = Float.MAX_VALUE
        for (r in list) {
            val l = r[0].toFloat(); val t = r[1].toFloat(); val rr = r[2].toFloat(); val b = r[3].toFloat()
            if (l == o[0] && t == o[1] && rr == o[2] && b == o[3]) continue
            // Along the arrow: the item must lie beyond the current one (its centre and its far edge).
            val gap: Float
            val ahead: Boolean
            when (dir) {
                RIGHT -> { gap = l - o[2]; ahead = (l + rr) / 2 > (o[0] + o[2]) / 2 + step && rr > o[2] }
                LEFT -> { gap = o[0] - rr; ahead = (l + rr) / 2 < (o[0] + o[2]) / 2 - step && l < o[0] }
                DOWN -> { gap = t - o[3]; ahead = (t + b) / 2 > (o[1] + o[3]) / 2 + step && b > o[3] }
                else -> { gap = o[1] - b; ahead = (t + b) / 2 < (o[1] + o[3]) / 2 - step && t < o[1] }
            }
            if (!ahead) continue
            val major = max(gap, 0f)
            // Sideways: the gap between the two items across the arrow (0 when they share a row / column).
            val minor = if (horizontal) max(0f, max(t - o[3], o[1] - b)) else max(0f, max(l - o[2], o[0] - rr))
            if (major > reach || minor > side) continue
            // Floating buttons ("back to top", chat bubbles) stay on screen everywhere: reachable only from their own row or column.
            if (r.size > 4 && r[4] == 1 && minor > 16 * density) continue
            val offset = if (horizontal) abs((t + b) / 2 - (o[1] + o[3]) / 2) else abs((l + rr) / 2 - (o[0] + o[2]) / 2)
            val score = major + 3f * minor + 0.3f * offset + (if (minor > 0f) 24 * density else 0f)
            if (score < bestScore) { bestScore = score; best = r }
        }
        return best
    }
}
