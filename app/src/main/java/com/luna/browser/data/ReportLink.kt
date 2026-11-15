package com.luna.browser.data

import java.net.URI
import java.net.URLEncoder

/**
 * Builds the pre-filled GitHub issue address behind "Report a problem" (shown as a QR code, so a phone can send it).
 *
 * Nothing leaves the TV: the report only exists once the viewer scans the code and submits it on GitHub. The page
 * address is reduced to scheme, host and path, because the query and fragment are where search words, tokens and
 * session ids live.
 */
object ReportLink {
    const val ISSUES = "https://github.com/newhalo/luna-browser/issues/new"
    private const val TEMPLATE = "ad_report.yml"
    private const val MAX_PAGE = 180

    /** The part of [url] that is safe to put in a report, or null when it is not a web page. */
    fun cleanPage(url: String): String? {
        val u = try { URI(url.trim()) } catch (e: Exception) { return null }
        val scheme = u.scheme?.lowercase() ?: return null
        val host = u.host ?: return null
        if (scheme != "http" && scheme != "https") return null
        val path = u.rawPath ?: ""
        // No user info, port is kept (it can matter for local servers), no query, no fragment.
        val port = if (u.port > 0) ":${u.port}" else ""
        return "$scheme://$host$port$path".take(MAX_PAGE)
    }

    /** One line about this installation for the report's "version" field. */
    fun environment(version: String, webView: String?, listsDate: String?, device: String): String =
        listOfNotNull("Luna $version", webView?.let { "WebView $it" }, listsDate?.let { "filter lists $it" }, device.ifBlank { null })
            .joinToString(" · ")

    /** The issue address, or null when [url] is not a web page. */
    fun build(url: String, environment: String): String? {
        val page = cleanPage(url) ?: return null
        fun q(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        return "$ISSUES?template=${q(TEMPLATE)}&title=${q("[Report] " + (URI(page).host ?: ""))}&url=${q(page)}&version=${q(environment)}"
    }
}
