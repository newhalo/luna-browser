package com.luna.browser.browser

import kotlin.math.abs

/** The rules behind the fullscreen video controls, kept free of Android types so they are unit-tested. */
object VideoRules {
    /** Playback speeds offered by the Speed button, in the order it steps through them. */
    val speeds = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

    /** The speed after [current] (wraps around to the slowest). */
    fun nextSpeed(current: Double): Double {
        val i = speeds.indexOfFirst { abs(it - current) < 0.01 }
        return speeds[(i + 1).mod(speeds.size)]
    }

    /** "1×", "1.25×", "0.5×". */
    fun speedLabel(rate: Double): String {
        val s = if (abs(rate - Math.round(rate)) < 0.001) Math.round(rate).toString() else rate.toString().trimEnd('0')
        return "$s×"
    }

    /** The digit (0..9) of a number key on the remote or a keyboard's number pad, else null. */
    fun digit(keyCode: Int): Int? = when (keyCode) {
        in KEY_0..KEY_9 -> keyCode - KEY_0
        in NUMPAD_0..NUMPAD_9 -> keyCode - NUMPAD_0
        else -> null
    }

    /** Where a number key jumps to, like YouTube: 0 = start, 5 = halfway, 9 = 90 %. Null for a live stream. */
    fun percentTarget(digit: Int, duration: Double): Double? =
        if (duration > 0 && digit in 0..9) duration * digit / 10.0 else null

    /** The buttons under the progress bar, left to right. */
    enum class Button { Back, Play, Forward, Skip, Speed, Exit }

    /** The button Down selects first: Play / Pause, the one used most. */
    val firstButton = Button.Play.ordinal

    fun move(selected: Int, delta: Int): Int = (selected + delta).coerceIn(0, Button.entries.lastIndex)

    /** How far the Skip button jumps (past an intro, a recap). */
    const val SKIP_SECONDS = 30

    // android.view.KeyEvent values, repeated so this file stays free of Android classes.
    private const val KEY_0 = 7
    private const val KEY_9 = 16
    private const val NUMPAD_0 = 144
    private const val NUMPAD_9 = 153
}
