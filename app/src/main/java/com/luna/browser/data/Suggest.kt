package com.luna.browser.data

import java.text.Normalizer

/**
 * Suggestions while typing an address or a search, ranked on the TV from what is already here: open tabs, favourites
 * and the history. Nothing is sent anywhere (no search-engine suggestions: they would send every letter typed).
 *
 * Matching ignores case and accents ("phim" finds "Phím"), so typing without diacritics on a remote still works.
 */
object Suggest {
    enum class Kind { Tab, Favorite, History }

    /** One candidate. [visits] counts visits to its site; [order] is its place in the history (0 = most recent). */
    data class Entry(val title: String, val url: String, val kind: Kind, val visits: Int = 0, val order: Int = 0) {
        // Worked out once per entry, not at every key press: the candidates are kept while the box is open,
        // and folding ~400 titles per letter typed is noticeable on a TV chip.
        internal val hostName: String by lazy { host(url) }
        internal val foldedTitle: String by lazy { fold(title) }
        internal val titleWords: List<String> by lazy { foldedTitle.split(WORD_BREAK).filter { it.isNotEmpty() } }
        internal val foldedUrl: String by lazy { fold(url) }
    }

    private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")
    private val MARKS = Regex("\\p{Mn}+")

    /** The best [limit] entries for [query] (at most one per address), best first; empty for an empty query. */
    fun rank(query: String, entries: List<Entry>, limit: Int = 6): List<Entry> {
        val q = fold(query.trim()).removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (q.isEmpty()) return emptyList()
        val words = q.split(' ').filter { it.isNotEmpty() }
        return entries.mapNotNull { e -> score(q, words, e)?.let { e to it } }
            .sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.order })
            .distinctBy { it.first.url.trimEnd('/') }
            .take(limit)
            .map { it.first }
    }

    /** The entry's host without "www." / "m.", lower case: "www.YouTube.com/watch" -> "youtube.com". */
    fun host(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringBefore(':').lowercase().removePrefix("www.").removePrefix("m.")

    private fun score(q: String, words: List<String>, e: Entry): Int? {
        val host = e.hostName
        val title = e.foldedTitle
        val titleWords = e.titleWords
        val match = when {
            host.startsWith(q) -> 1000                                              // "you" -> youtube.com
            host.split('.').any { it.startsWith(q) } -> 700                         // "wiki" -> en.wikipedia.org
            words.all { w -> titleWords.any { it.startsWith(w) } } -> 500            // "big buck" -> Big Buck Bunny
            title.contains(q) -> 250
            e.foldedUrl.contains(q) -> 200
            else -> return null
        }
        val kindBonus = when (e.kind) { Kind.Tab -> 120; Kind.Favorite -> 100; Kind.History -> 0 }
        // A site visited often, or a page seen recently, comes first among equals.
        return match + kindBonus + minOf(e.visits, 50) * 4 + maxOf(0, 40 - e.order)
    }

    /** Lower case without accents (đ -> d), for matching only. */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "").replace('đ', 'd')
}
