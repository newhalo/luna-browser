package com.luna.browser.data

/**
 * What the viewer typed in the address box: a full address, a bare site name (`vn2d.my`, `example.com/page`), a local
 * address, or words to search for. Pure Kotlin so the rules are unit-tested.
 */
object AddressInput {
    enum class Kind { Empty, Explicit, Local, Bare, Search }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
    private val IPV4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?([/?#].*)?$")

    fun classify(input: String): Kind {
        val t = input.trim()
        if (t.isEmpty()) return Kind.Empty
        if (SCHEME.matches(t)) return Kind.Explicit
        if (t.startsWith("localhost") || IPV4.matches(t)) return Kind.Local
        if (t.any { it.isWhitespace() }) return Kind.Search
        // The site-name part: up to the first / ? #, without a user name and a :port.
        val authority = t.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val host = authority.substringBefore(':')
        val labels = host.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return Kind.Search
        // "3.14" or "v1.2.3" are not sites: the last part must be letters (a top-level name, or xn-- for international ones).
        val tld = labels.last()
        val letters = tld.length >= 2 && tld.all { it.isLetter() } || tld.startsWith("xn--", ignoreCase = true)
        return if (letters) Kind.Bare else Kind.Search
    }
}
