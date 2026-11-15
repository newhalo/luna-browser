package com.luna.browser.data

import org.json.JSONObject

/**
 * "Is there a newer Luna?" for builds installed outside a store (GitHub APK, Downloader, adb).
 *
 * It only reads the public GitHub release list and tells the viewer; it never downloads or installs anything
 * itself. Builds installed from an app store are skipped: the store updates them, and store rules don't allow an app
 * to point to another place to get itself.
 */
object UpdateCheck {
    const val API = "https://api.github.com/repos/newhalo/luna-browser/releases/latest"
    private const val PAGE_PREFIX = "https://github.com/newhalo/luna-browser/"
    const val MAX_BYTES = 256 * 1024
    const val DAY_MS = 24L * 60 * 60 * 1000

    class Release(val version: String, val url: String)

    /** "v0.4.1", "0.4.1-debug" -> [0, 4, 1]; null when there is no leading number. */
    fun parse(v: String?): List<Int>? {
        val core = v?.trim()?.removePrefix("v")?.removePrefix("V")?.takeWhile { it.isDigit() || it == '.' } ?: return null
        val parts = core.split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() }
    }

    fun isNewer(latest: String?, current: String?): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Reads GitHub's "latest release" JSON; drafts, pre-releases and pages outside this project are refused. */
    fun fromJson(json: String): Release? = try {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) null else {
            val tag = o.optString("tag_name")
            val url = o.optString("html_url")
            if (parse(tag) == null || !url.startsWith(PAGE_PREFIX)) null else Release(tag.removePrefix("v"), url)
        }
    } catch (e: Exception) {
        null
    }

    private val stores = setOf("com.android.vending", "com.amazon.venezia", "org.fdroid.fdroid", "com.xiaomi.mipicks", "com.sec.android.app.samsungapps")

    /** True when [installer] is an app store that already handles updates. */
    fun isStoreInstall(installer: String?): Boolean = installer != null && installer in stores
}
