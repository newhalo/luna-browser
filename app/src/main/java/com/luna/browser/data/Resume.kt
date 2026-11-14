package com.luna.browser.data

import java.net.URI

/** Where a video was left: [pos] of [dur] seconds, last updated at [time]. */
data class Resume(val pos: Double, val dur: Double, val time: Long) {
    val fraction: Float get() = if (dur <= 0) 0f else (pos / dur).toFloat().coerceIn(0f, 1f)
}

/**
 * The rules for "continue where you left off". Pure functions, so they are unit-tested
 * (see ResumeRulesTest) without a device.
 */
object ResumeRules {
    /** Clips shorter than this are not worth resuming (and pre-roll ads are shorter still). */
    const val MIN_DURATION = 180.0

    /** Positions before this are treated as "not started". */
    const val MIN_POSITION = 15.0

    /** A saved duration may differ from the playing one by this much (live re-encodes, ads). */
    private const val DURATION_TOLERANCE = 5.0

    /**
     * Identifies a video page. Fragments and tracking parameters must not make the same video look
     * different; YouTube is keyed by video id since its URLs carry playlist/time parameters.
     */
    fun key(url: String): String {
        val u = try { URI(url.trim()) } catch (e: Exception) { return url.substringBefore('#') }
        val host = (u.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val query = u.rawQuery ?: ""
        if (host == "youtube.com" || host == "music.youtube.com") {
            val v = query.split('&').firstOrNull { it.startsWith("v=") }?.substring(2)
            if (!v.isNullOrEmpty()) return "yt:$v"
        }
        if (host == "youtu.be") u.path?.trim('/')?.takeIf { it.isNotEmpty() }?.let { return "yt:$it" }
        return "$host${u.rawPath ?: ""}${if (query.isEmpty()) "" else "?$query"}"
    }

    /** Whether a playing position is worth remembering. */
    fun shouldSave(pos: Double, dur: Double) = dur >= MIN_DURATION && pos >= MIN_POSITION

    /** Whether the viewer has effectively finished the video, so there is nothing to resume. */
    fun finished(pos: Double, dur: Double) = dur > 0 && (dur - pos < maxOf(30.0, dur * 0.04))

    /** The playing position must be at least this far before the saved one for a seek to be worth it. */
    private const val MIN_GAP = 20.0

    /**
     * How many seconds to seek (relative) to get back to [saved], or null when the video that is
     * playing is not the one that was saved (different length), or is already at (or past) that point.
     * Film sites usually autoplay for a while before the viewer goes fullscreen, so "a little way in"
     * still counts as the start.
     */
    fun restoreDelta(saved: Resume, pos: Double, dur: Double): Int? {
        if (dur < MIN_DURATION || kotlin.math.abs(saved.dur - dur) > DURATION_TOLERANCE) return null
        if (saved.pos < MIN_POSITION || saved.pos - pos < MIN_GAP) return null
        return (saved.pos - pos).toInt()
    }

    /** "1:02:03" / "12:34". */
    fun clock(seconds: Double): String {
        val t = seconds.toLong().coerceAtLeast(0)
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
