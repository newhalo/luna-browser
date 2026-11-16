package com.luna.browser.browser

import java.net.URI

/**
 * Some sites answer only on `www.example.com`: the bare `example.com` has no address or no certificate, so typing it (or
 * following an old link to it) fails. When a page fails to connect, Luna tries the same address once with `www.` in front.
 * Kept apart from the Android classes so it is unit-tested.
 */
object WwwFallback {
    private val SECOND_LEVEL = setOf("com", "co", "net", "org", "gov", "edu", "ac")

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
