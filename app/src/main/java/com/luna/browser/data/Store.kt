package com.luna.browser.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class Site(val title: String, val url: String) {
    val host: String get() = hostOf(url)
}

data class HistoryEntry(val title: String, val url: String, val time: Long)

fun hostOf(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url

val QuickSites = listOf(
    Site("YouTube", "https://www.youtube.com"),
    Site("Google", "https://www.google.com"),
    Site("Facebook", "https://www.facebook.com"),
    Site("VnExpress", "https://vnexpress.net"),
    Site("Tuổi Trẻ", "https://tuoitre.vn"),
    Site("Wikipedia", "https://www.wikipedia.org"),
    Site("Twitch", "https://www.twitch.tv"),
    Site("Zing MP3", "https://zingmp3.vn"),
)

/** Setting keys. */
object K {
    const val ENGINE = "engine"
    const val LAUNCH = "launch"
    const val SHOW_CONTINUE = "showContinue"
    const val SHOW_RECENT = "showRecent"
    const val CURSOR_SPEED = "cursorSpeed"
    const val CURSOR_START = "cursorStart"
    const val DESKTOP = "desktop"
    const val ZOOM = "zoom"
    const val ADBLOCK = "adblock"
    const val BLOCK_POPUPS = "popups"
    const val SEEK_STEP = "seekStep"
    const val THIRD_PARTY = "thirdParty"
    const val REDUCE_MOTION = "reduceMotion"
    const val TIP_SHOWN = "tipShown"
}

val SearchEngines = mapOf(
    "Google" to "https://www.google.com/search?q=%s",
    "Bing" to "https://www.bing.com/search?q=%s",
    "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
)

/** Persistent, Compose-observable app data: settings, favorites, history. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("luna", Context.MODE_PRIVATE)

    private val defaults = mapOf(
        K.ENGINE to "Google", K.LAUNCH to "home",
        K.SHOW_CONTINUE to "true", K.SHOW_RECENT to "true",
        K.CURSOR_SPEED to "3", K.CURSOR_START to "true",
        K.DESKTOP to "true", K.ZOOM to "125",
        K.ADBLOCK to "true", K.BLOCK_POPUPS to "true",
        K.SEEK_STEP to "10", K.THIRD_PARTY to "true",
        K.REDUCE_MOTION to "false", K.TIP_SHOWN to "false",
    )

    val settings = mutableStateMapOf<String, String>().apply {
        putAll(defaults)
        defaults.keys.forEach { k -> sp.getString("s.$k", null)?.let { put(k, it) } }
    }

    fun str(k: String): String = settings[k] ?: defaults[k] ?: ""
    fun bool(k: String): Boolean = str(k) == "true"
    fun int(k: String): Int = str(k).toIntOrNull() ?: 0
    fun put(k: String, v: Any) {
        settings[k] = v.toString()
        sp.edit().putString("s.$k", v.toString()).apply()
    }

    val favorites = mutableStateListOf<Site>().apply { addAll(readSites("favorites")) }
    val history = mutableStateListOf<HistoryEntry>().apply { addAll(readHistory()) }
    var lastSession by mutableStateOf(readSites("last").firstOrNull())
        private set

    // ---- Favorites ----
    fun isFavorite(url: String) = favorites.any { it.url == url }

    /** Returns true when the site is now a favorite. */
    fun toggleFavorite(site: Site): Boolean {
        val existing = favorites.indexOfFirst { it.url == site.url }
        val added = existing < 0
        if (added) favorites.add(0, site) else favorites.removeAt(existing)
        writeSites("favorites", favorites)
        return added
    }

    fun removeFavorite(site: Site) {
        favorites.remove(site)
        writeSites("favorites", favorites)
    }

    // ---- History ----
    fun recordVisit(title: String, url: String) {
        if (!url.startsWith("http")) return
        val t = title.ifBlank { hostOf(url) }
        history.removeAll { it.url == url }
        history.add(0, HistoryEntry(t, url, System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        lastSession = Site(t, url)
        writeHistory()
        writeSites("last", listOf(Site(t, url)))
    }

    fun clearHistory() {
        history.clear()
        lastSession = null
        writeHistory()
        writeSites("last", emptyList())
    }

    // ---- Address resolution ----
    fun resolve(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*").matches(t)) return t
        if (t.startsWith("localhost") || Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$").matches(t)) return "http://$t"
        if (!t.contains(' ') && t.contains('.')) return "https://$t"
        val template = SearchEngines[str(K.ENGINE)] ?: SearchEngines.getValue("Google")
        return template.replace("%s", URLEncoder.encode(t, "UTF-8"))
    }

    // ---- Persistence helpers ----
    private fun readSites(key: String): List<Site> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Site(o.getString("t"), o.getString("u")) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeSites(key: String, list: List<Site>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url)) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    private fun readHistory(): List<HistoryEntry> = try {
        val arr = JSONArray(sp.getString("history", "[]"))
        (0 until arr.length()).map {
            arr.getJSONObject(it).let { o -> HistoryEntry(o.getString("t"), o.getString("u"), o.getLong("ts")) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeHistory() {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url).put("ts", it.time)) }
        sp.edit().putString("history", arr.toString()).apply()
    }

    private companion object {
        const val MAX_HISTORY = 300
    }
}
