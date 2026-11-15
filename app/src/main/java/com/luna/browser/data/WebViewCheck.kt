package com.luna.browser.data

/**
 * Is the TV's system WebView (Chromium) recent enough for today's web? Many TVs never update it, and an old engine
 * is the most common reason a modern site shows a blank page or a broken player.
 *
 * Thresholds are deliberately conservative: Chromium 110 is from early 2023; below 90 even mainstream sites fail.
 */
object WebViewCheck {
    enum class Level { Unknown, Ok, Old, VeryOld }

    const val GOOD = 110
    const val MIN = 90

    /** The major version of "157.0.8103.38", or null when it can't be read. */
    fun major(version: String?): Int? = version?.trim()?.substringBefore('.')?.toIntOrNull()?.takeIf { it > 0 }

    fun level(version: String?): Level = when (val m = major(version)) {
        null -> Level.Unknown
        in GOOD..Int.MAX_VALUE -> Level.Ok
        in MIN until GOOD -> Level.Old
        else -> Level.VeryOld
    }

    fun needsAttention(level: Level) = level == Level.Old || level == Level.VeryOld
}
