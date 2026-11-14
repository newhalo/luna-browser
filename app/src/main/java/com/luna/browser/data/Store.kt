package com.luna.browser.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

data class Site(val title: String, val url: String) {
    val host: String get() = hostOf(url)
}

data class HistoryEntry(
    val title: String,
    val url: String,
    val time: Long,
    /** Remote preview image (og:image or a video thumbnail). */
    val image: String? = null,
    /** Local screenshot of the page. */
    val snap: String? = null,
) {
    val host: String by lazy { hostOf(url) }

    /** True for an article / video rather than a site's front page. */
    val deep: Boolean by lazy { Uri.parse(url).let { (it.path?.length ?: 0) > 1 || it.query != null } }

    /**
     * Best artwork for cards: a video/article thumbnail first, otherwise the page screenshot.
     * Computed once per entry (entries are immutable) — never touch the disk during composition.
     */
    val art: Any? by lazy {
        if (deep && image != null) image else snap?.let { File(it) } ?: image
    }
}

/** One card on the home screen. */
data class MediaItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val art: Any?,
    val icon: Any?,
    val brand: Pair<Long, Long>,
    val time: Long = 0L,
)

/**
 * Makes page titles card-friendly: drops "(3) " notification counters and a trailing
 * " - YouTube"-style site suffix, and rejects titles that are really just the URL.
 */
fun cleanTitle(raw: String, url: String): String {
    var t = raw.trim().replace(Regex("^\\(\\d+\\)\\s*"), "")
    val host = hostOf(url)
    if (t.isEmpty() || t.contains("://") || t.startsWith(host) || t.startsWith("www.")) return ""
    val name = host.substringBefore('.')
    val cut = listOf(" - ", " | ", " – ", " — ").maxOf { t.lastIndexOf(it) }
    if (cut > 0 && t.substring(cut).replace(" ", "").contains(name, ignoreCase = true)) t = t.substring(0, cut).trim()
    return t
}

fun hostOf(url: String): String =Uri.parse(url).host?.removePrefix("www.")?.removePrefix("m.") ?: url

/** YouTube video thumbnail for watch / shorts / youtu.be links. */
fun youtubeThumb(url: String): String? {
    val u = Uri.parse(url)
    val host = u.host ?: return null
    val id = when {
        host.endsWith("youtube.com") && u.path == "/watch" -> u.getQueryParameter("v")
        host.endsWith("youtube.com") && u.path?.startsWith("/shorts/") == true -> u.path?.removePrefix("/shorts/")
        host == "youtu.be" -> u.path?.trim('/')
        else -> null
    }
    return id?.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
}

val QuickSites = listOf(
    Site("YouTube", "https://www.youtube.com"),
    Site("Google", "https://www.google.com"),
    Site("Facebook", "https://www.facebook.com"),
    Site("VnExpress", "https://vnexpress.net"),
    Site("VTVgo", "https://vtvgo.vn"),
    Site("Tuổi Trẻ", "https://tuoitre.vn"),
    Site("Zing MP3", "https://zingmp3.vn"),
    Site("Wikipedia", "https://vi.wikipedia.org"),
)

/** Brand-like gradient for a site card when no thumbnail exists yet. */
fun brandOf(host: String): Pair<Long, Long> = when {
    host.contains("youtube") -> 0xFFE52D27 to 0xFF5C0A12
    host.contains("google") -> 0xFF4285F4 to 0xFF1B3A7A
    host.contains("facebook") -> 0xFF1877F2 to 0xFF0B3577
    host.contains("vnexpress") -> 0xFF9F224E to 0xFF3E0C1E
    host.contains("vtvgo") -> 0xFF00A0E3 to 0xFF0A3A66
    host.contains("tuoitre") -> 0xFFED1C24 to 0xFF5A0B0E
    host.contains("zingmp3") -> 0xFF8D22C3 to 0xFF35104D
    host.contains("wikipedia") -> 0xFF6B7280 to 0xFF1F2430
    else -> listOf(
        0xFF5B6CFF to 0xFF241B5E, 0xFFFF6B8A to 0xFF5A1730, 0xFF2BC0A0 to 0xFF0E3F40,
        0xFFFFA34D to 0xFF5A2A0E, 0xFFB57BFF to 0xFF3A1A5E, 0xFF38B6FF to 0xFF0E2F5A,
    )[(host.hashCode() and 0x7fffffff) % 6]
}

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
    const val ANIMATED_BG = "animatedBg"
    const val TIP_SHOWN = "tipShown"
}

val SearchEngines = mapOf(
    "Google" to "https://www.google.com/search?q=%s",
    "Bing" to "https://www.bing.com/search?q=%s",
    "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
    "Cốc Cốc" to "https://coccoc.com/search?query=%s",
)

/** Persistent, Compose-observable app data: settings, favorites, history, artwork. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("luna", Context.MODE_PRIVATE)
    val thumbsDir = File(ctx.filesDir, "thumbs").apply { mkdirs() }
    val iconsDir = File(ctx.filesDir, "icons").apply { mkdirs() }

    private val defaults = mapOf(
        K.ENGINE to "Google", K.LAUNCH to "home",
        K.SHOW_CONTINUE to "true", K.SHOW_RECENT to "true",
        K.CURSOR_SPEED to "3", K.CURSOR_START to "true",
        K.DESKTOP to "true", K.ZOOM to "125",
        K.ADBLOCK to "true", K.BLOCK_POPUPS to "true",
        K.SEEK_STEP to "10", K.THIRD_PARTY to "true",
        K.REDUCE_MOTION to "false", K.ANIMATED_BG to "true", K.TIP_SHOWN to "false",
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
    private val visits = mutableStateMapOf<String, Int>().apply {
        runCatching {
            val o = JSONObject(sp.getString("visits", "{}")!!)
            o.keys().forEach { put(it, o.getInt(it)) }
        }
    }
    var lastSession by mutableStateOf(readSites("last").firstOrNull())
        private set

    /** Bumped on every change to history, favourites or artwork; screens memoise their rows on it. */
    var version by mutableIntStateOf(0)
        private set

    /** Disk writes happen off the UI thread, in order. */
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    // ---- Favorites ----
    fun isFavorite(url: String) = favorites.any { it.url == url }

    /** Returns true when the site is now a favorite. */
    fun toggleFavorite(site: Site): Boolean {
        val existing = favorites.indexOfFirst { it.url == site.url }
        val added = existing < 0
        if (added) favorites.add(0, site) else favorites.removeAt(existing)
        version++
        writeSites("favorites", favorites)
        return added
    }

    fun removeFavorite(site: Site) {
        favorites.remove(site)
        version++
        writeSites("favorites", favorites)
    }

    // ---- History ----
    fun recordVisit(title: String, url: String) {
        if (!url.startsWith("http")) return
        val old = history.firstOrNull { it.url == url }
        val t = cleanTitle(title, url).ifBlank { old?.title ?: hostOf(url) }
        history.removeAll { it.url == url }
        history.add(0, HistoryEntry(t, url, System.currentTimeMillis(), old?.image ?: youtubeThumb(url), old?.snap))
        while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        if (old == null) {
            val h = hostOf(url)
            visits[h] = (visits[h] ?: 0) + 1
            sp.edit().putString("visits", JSONObject(visits.toMap()).toString()).apply()
        }
        lastSession = Site(t, url)
        version++
        writeHistory()
        writeSites("last", listOf(Site(t, url)))
    }

    fun updateTitle(url: String, rawTitle: String) {
        val title = cleanTitle(rawTitle, url)
        if (title.isBlank()) return
        val i = history.indexOfFirst { it.url == url }
        if (i < 0 || history[i].title == title) return
        history[i] = history[i].copy(title = title)
        version++
        if (lastSession?.url == url) {
            lastSession = Site(title, url)
            writeSites("last", listOf(Site(title, url)))
        }
        writeHistory()
    }

    fun updateArt(url: String, image: String? = null, snap: String? = null) {
        val i = history.indexOfFirst { it.url == url }
        if (i < 0) return
        val e = history[i]
        history[i] = e.copy(image = image ?: e.image, snap = snap ?: e.snap)
        version++
        writeHistory()
    }

    fun clearHistory() {
        history.clear()
        visits.clear()
        lastSession = null
        version++
        io.execute { thumbsDir.listFiles()?.forEach { it.delete() } }
        sp.edit().putString("visits", "{}").apply()
        writeHistory()
        writeSites("last", emptyList())
    }

    // ---- Artwork lookups ----
    /** Favicons on disk, listed once at start-up instead of a File.exists() per card per frame. */
    private val savedIcons: MutableSet<String> = java.util.Collections.synchronizedSet(
        HashSet(iconsDir.list()?.map { it.removeSuffix(".png") } ?: emptyList()),
    )

    fun iconSaved(host: String) {
        if (savedIcons.add(host)) version++
    }

    fun iconFor(host: String): Any? {
        if (host in savedIcons) return File(iconsDir, "$host.png")
        // Curated sites only: a public favicon service, never for the user's own browsing.
        if (QuickSites.any { it.host == host }) return "https://www.google.com/s2/favicons?domain=$host&sz=128"
        return null
    }

    fun artForUrl(url: String): Any? = history.firstOrNull { it.url == url }?.art

    fun artForHost(host: String): Any? =
        history.firstOrNull { it.host == host && it.deep && it.image != null }?.image
            ?: history.firstOrNull { it.host == host }?.art

    private fun siteName(host: String) = QuickSites.firstOrNull { it.host == host }?.title
        ?: host.substringBefore('.').replaceFirstChar { it.uppercase() }

    /** Frequently used sites first, topped up with the curated quick sites. */
    fun siteItems(limit: Int = 10): List<MediaItem> {
        val frequent = visits.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
        val hosts = (frequent + QuickSites.map { it.host }).distinct().take(limit)
        return hosts.map { h ->
            val quick = QuickSites.firstOrNull { it.host == h }
            MediaItem(
                key = "site:$h", title = siteName(h), subtitle = h,
                url = quick?.url ?: "https://$h", art = artForHost(h), icon = iconFor(h), brand = brandOf(h),
            )
        }
    }

    fun continueItems(limit: Int = 12): List<MediaItem> = history.take(limit).map {
        MediaItem("h:${it.url}", cleanTitle(it.title, it.url).ifBlank { it.title }, it.host, it.url, it.art, iconFor(it.host), brandOf(it.host), it.time)
    }

    fun favoriteItems(): List<MediaItem> = favorites.map {
        MediaItem("f:${it.url}", it.title, it.host, it.url, artForUrl(it.url) ?: artForHost(it.host), iconFor(it.host), brandOf(it.host))
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
        val copy = list.toList()
        io.execute {
            val arr = JSONArray()
            copy.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url)) }
            sp.edit().putString(key, arr.toString()).apply()
        }
    }

    private fun readHistory(): List<HistoryEntry> = try {
        val arr = JSONArray(sp.getString("history", "[]"))
        (0 until arr.length()).map {
            arr.getJSONObject(it).let { o ->
                HistoryEntry(
                    o.getString("t"), o.getString("u"), o.getLong("ts"),
                    o.optString("img").ifEmpty { null }, o.optString("snap").ifEmpty { null },
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeHistory() {
        val copy = history.toList()
        io.execute { writeHistoryNow(copy) }
    }

    private fun writeHistoryNow(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("t", it.title).put("u", it.url).put("ts", it.time)
                    .put("img", it.image ?: "").put("snap", it.snap ?: ""),
            )
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    private companion object {
        const val MAX_HISTORY = 300
    }
}
