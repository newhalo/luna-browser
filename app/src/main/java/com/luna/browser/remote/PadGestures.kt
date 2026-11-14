package com.luna.browser.remote

import kotlin.math.abs

/** Parsing and interpretation of what the phone's touchpad sends. Pure, so it is unit-tested. */
object PadGestures {
    /**
     * "dx,dy" in per-mille of the pad's width / height (so every phone screen feels the same), clamped to
     * +-1000; null for anything else.
     */
    fun parse(text: String): Pair<Int, Int>? {
        val p = text.split(',')
        if (p.size != 2) return null
        val x = p[0].trim().toIntOrNull() ?: return null
        val y = p[1].trim().toIntOrNull() ?: return null
        return x.coerceIn(-1000, 1000) to y.coerceIn(-1000, 1000)
    }
}

/**
 * Turns finger movement into D-pad presses, for the places where there is no pointer (menus, the home
 * screen, link mode, video controls): every [step] per-mille of travel is one key press. A press on one
 * axis forgets the movement accumulated on the other, so a slightly diagonal swipe doesn't fire both.
 */
class SwipeSteps(private val step: Int = 140) {
    private var ax = 0
    private var ay = 0

    /** Feeds a movement; returns the directions to press: 'L', 'R', 'U', 'D'. */
    fun feed(dx: Int, dy: Int): List<Char> {
        ax += dx
        ay += dy
        val out = ArrayList<Char>(2)
        while (abs(ax) >= step && out.size < MAX_PER_FEED) {
            out += if (ax > 0) 'R' else 'L'
            ax -= if (ax > 0) step else -step
            ay = 0
        }
        while (abs(ay) >= step && out.size < MAX_PER_FEED) {
            out += if (ay > 0) 'D' else 'U'
            ay -= if (ay > 0) step else -step
            ax = 0
        }
        return out
    }

    fun reset() {
        ax = 0
        ay = 0
    }

    private companion object {
        const val MAX_PER_FEED = 4
    }
}
