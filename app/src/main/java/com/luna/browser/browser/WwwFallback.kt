package com.luna.browser.browser

import java.net.URI

/**
 * When a page cannot be reached, browsers try the obvious neighbours of the address before giving up: some sites answer only
 * on `www.example.com` (the bare name has no address or no certificate), and a site typed without `https://` may only speak
 * plain `http://`. Kept apart from the Android classes so it is unit-tested.
 */
object WwwFallback {
    private val SECOND_LEVEL = setOf("com", "co", "net", "org", "gov", "edu", "ac")

    /**
     * The addresses to try, in order, after [url] failed to connect. [bare] says the viewer typed it without a scheme, which is
     * the only case where falling back from https to http is acceptable (an explicit https address is never downgraded).
     */
    fun candidates(url: String, bare: Boolean): List<String> {
        val out = mutableListOf<String>()
        val www = alternative(url)
        www?.let { out.add(it) }
        if (bare && url.startsWith("https://", ignoreCase = true)) {
            val http = "http://" + url.substring(8)
            out.add(http)
            www?.let { out.add("http://" + it.substring(8)) }
        }
        return out
    }

    /** The same address on `www.` + host, or null when that makes no sense (already `www`, a subdomain, an IP, not http/https). */
    fun alternative(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (host.contains(':') || host.all { it.isDigit() || it == '.' }) return null
        val labels = host.split('.')
        if (labels.any { it.isEmpty() } || labels.first().equals("www", true)) return null
        val bare = labels.size == 2 || (labels.size == 3 && labels[1].lowercase() in SECOND_LEVEL && labels[2].length == 2)
        if (!bare) return null
        val at = url.indexOf(host, url.indexOf("://") + 3)
        if (at < 0) return null
        return url.substring(0, at) + "www." + url.substring(at)
    }
}
