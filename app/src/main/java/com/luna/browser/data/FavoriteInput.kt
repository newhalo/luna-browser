package com.luna.browser.data

/**
 * What the phone may save as a favourite: a web address, nothing else. A search phrase ("phim hay"), a scheme such as
 * `javascript:` or `file:`, or text with spaces is refused instead of being turned into a search page.
 */
object FavoriteInput {
    private val full = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    private val bare = Regex("^[^\\s:/?#]+\\.[^\\s:]+(/\\S*)?$")

    /** The https/http address for [text], or null when it is not a web address. */
    fun address(text: String): String? {
        val raw = text.trim()
        if (raw.isEmpty() || raw.length > 2000) return null
        return when {
            full.matches(raw) -> raw
            bare.matches(raw) -> "https://$raw"
            else -> null
        }
    }
}
