package com.luna.browser.data.filter

/**
 * Filter lists carry a "! Version: 2026.11.15" comment. When a list exists twice (the copy bundled in the app and a
 * downloaded one) the newer wins, so an app update never gets stuck behind an old download, and a fresh download
 * never gets replaced by an older bundle.
 */
object ListVersion {
    private val header = Regex("""^!\s*Version:\s*([0-9][0-9.]*)""", RegexOption.MULTILINE)

    /** The version numbers, e.g. [2026, 11, 15]; empty when the list has none. */
    fun of(text: String?): List<Int> {
        if (text == null) return emptyList()
        val v = header.find(text.take(4000))?.groupValues?.get(1) ?: return emptyList()
        return v.split('.').map { it.toIntOrNull() ?: 0 }
    }

    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** The text with the higher version; on a tie (or without versions) the downloaded copy. */
    fun newer(downloaded: String?, bundled: String?): String? = when {
        downloaded == null -> bundled
        bundled == null -> downloaded
        compare(of(bundled), of(downloaded)) > 0 -> bundled
        else -> downloaded
    }
}
