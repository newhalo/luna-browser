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
import com.luna.browser.data.filter.siteOf
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

    /** The page screenshot, when [art] is a remote image that might fail to load. */
    val altArt: Any? by lazy { snap?.let { File(it) }?.takeIf { art !is File } }
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
    /** Shown instead of [art] when that image fails to load (e.g. a remote og:image that is blocked). */
    val alt: Any? = null,
    /** 0..1 how much of the video was watched (0 = nothing to resume). */
    val progress: Float = 0f,
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

/** Sites everyone gets on Home; site names are proper nouns and are not translated. */
private val GlobalSites = listOf(
    Site("YouTube", "https://www.youtube.com"),
    Site("Google", "https://www.google.com"),
    Site("Facebook", "https://www.facebook.com"),
    Site("Reddit", "https://www.reddit.com"),
    Site("Twitch", "https://www.twitch.tv"),
)

/** Extra sites that matter in a language's home region, shown before the global ones. */
private val RegionalSites = mapOf(
    "vi" to listOf(
        Site("VnExpress", "https://vnexpress.net"), Site("Tuổi Trẻ", "https://tuoitre.vn"),
        Site("VTVgo", "https://vtvgo.vn"), Site("Zing MP3", "https://zingmp3.vn"),
    ),
    "es" to listOf(Site("El País", "https://elpais.com")),
    "fr" to listOf(Site("Le Monde", "https://www.lemonde.fr")),
    "de" to listOf(Site("Tagesschau", "https://www.tagesschau.de")),
    "pt" to listOf(Site("g1", "https://g1.globo.com")),
    "ru" to listOf(Site("Yandex", "https://yandex.ru"), Site("VK", "https://vk.com")),
    "ja" to listOf(Site("Yahoo! JAPAN", "https://www.yahoo.co.jp"), Site("NHK", "https://www.nhk.or.jp")),
    "ko" to listOf(Site("NAVER", "https://www.naver.com")),
    "zh" to listOf(Site("Bilibili", "https://www.bilibili.com"), Site("Baidu", "https://www.baidu.com")),
    "id" to listOf(Site("Detik", "https://www.detik.com")),
    "tr" to listOf(Site("Hürriyet", "https://www.hurriyet.com.tr")),
)

/** Wikipedia in the viewer's own language. */
private fun wikipedia(lang: String) = Site("Wikipedia", "https://${if (lang == "zh") "zh" else lang}.wikipedia.org")

/** The quick-access sites for a UI language code ("vi", "en", …). */
fun quickSitesFor(lang: String): List<Site> =
    // YouTube first (what most people open a TV browser for), then the home region, then the rest.
    (GlobalSites.take(1) + RegionalSites[lang].orEmpty() + GlobalSites.drop(1) + wikipedia(lang)).distinctBy { it.url }

/** Every site Luna knows by name, whatever the language (used to label and icon history entries). */
private val KnownSites: List<Site> = (RegionalSites.values.flatten() + GlobalSites).distinctBy { it.url }

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
    host.contains("reddit") -> 0xFFFF4500 to 0xFF5A1A00
    host.contains("twitch") -> 0xFF9146FF to 0xFF2D1A5E
    host.contains("yandex") -> 0xFFFC3F1D to 0xFF5A1608
    host.contains("naver") -> 0xFF03C75A to 0xFF0A4A27
    host.contains("bilibili") -> 0xFF00A1D6 to 0xFF0A3A55
    host.contains("baidu") -> 0xFF2932E1 to 0xFF121660
    host.contains("yahoo") -> 0xFF7B0099 to 0xFF2D0A3A
    host.contains("nhk") -> 0xFFE60012 to 0xFF50040A
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
    const val SNAP = "pointerSnap"
    const val DESKTOP = "desktop"
    const val ZOOM = "zoom"
    const val ADBLOCK = "adblock"
    const val BLOCK_POPUPS = "popups"
    const val SEEK_STEP = "seekStep"
    const val THIRD_PARTY = "thirdParty"
    const val REDUCE_MOTION = "reduceMotion"
    const val ANIMATED_BG = "animatedBg"
    const val THEME = "theme"
    const val GLASS = "glass"
    const val TIP_SHOWN = "tipShown"
    /** "auto" follows the system language; otherwise a language code from [Languages]. */
    const val LANGUAGE = "language"
    /** Lets a phone on the same Wi-Fi send addresses and text to the TV. */
    const val PHONE = "phoneRemote"
    const val PHONE_PIN = "phonePin"
    /** Size of the Home thumbnails: "large", "medium" or "small" (see [TileSize]). */
    const val TILE_SIZE = "tileSize"
    /** How the History page lists pages: "list" or "grid". */
    const val HISTORY_VIEW = "historyView"
    /** Once a day, ask GitHub whether a newer Luna exists (builds installed outside a store only). */
    const val UPDATE_CHECK = "updateCheck"
    const val UPDATE_LAST = "updateLast"
    const val UPDATE_LATEST = "updateLatest"
    const val UPDATE_URL = "updateUrl"
    /** The active profile's id (never the guest: a guest session is not remembered). */
    const val PROFILE = "profile"
    /** Show "Who's watching?" when Luna opens (only matters with more than one profile). */
    const val ASK_PROFILE = "askProfile"
    /** Text size of the reading page: an index into [com.luna.browser.browser.ReaderMode.sizes]. */
    const val READER_SIZE = "readerSize"
    /** Show every page dark (the web engine darkens pages that have no dark look of their own). Off by default. */
    const val DARK_PAGES = "darkPages"
    /** Hide cookie-consent banners (without accepting anything). */
    const val COOKIE_BANNERS = "cookieBanners"
}

/** Languages the UI is translated into (code to its own name, never translated). Keep in sync with res/xml/locales_config.xml. */
val Languages = listOf(
    "en" to "English", "vi" to "Tiếng Việt", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
    "pt" to "Português", "ru" to "Русский", "ja" to "日本語", "ko" to "한국어", "zh" to "中文（简体）",
)

val SearchEngines = mapOf(
    "Google" to "https://www.google.com/search?q=%s",
    "Bing" to "https://www.bing.com/search?q=%s",
    "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
    "Cốc Cốc" to "https://coccoc.com/search?query=%s",
    "Yandex" to "https://yandex.com/search/?text=%s",
    "Baidu" to "https://www.baidu.com/s?wd=%s",
    "NAVER" to "https://search.naver.com/search.naver?query=%s",
)

/** Search engines offered for a language: the global ones plus the regional favourite. */
fun searchEnginesFor(lang: String): List<String> =
    listOf("Google", "Bing", "DuckDuckGo") + when (lang) {
        "vi" -> listOf("Cốc Cốc")
        "ru" -> listOf("Yandex")
        "zh" -> listOf("Baidu")
        "ko" -> listOf("NAVER")
        else -> emptyList()
    }

/** Persistent, Compose-observable app data: settings, favorites, history, artwork. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("luna", Context.MODE_PRIVATE)
    val thumbsDir = File(ctx.filesDir, "thumbs").apply { mkdirs() }
    val iconsDir = File(ctx.filesDir, "icons").apply { mkdirs() }
    /** A fresh screenshot of each open tab (taken when the viewer leaves the page for the tab switcher). */
    val tabThumbsDir = File(ctx.filesDir, "tabthumbs").apply { mkdirs() }

    private val lowEnd = DeviceProfile.isLowEnd(ctx)

    private val defaults = mapOf(
        K.ENGINE to "Google", K.LAUNCH to "home",
        K.SHOW_CONTINUE to "true", K.SHOW_RECENT to "true",
        K.CURSOR_SPEED to "3", K.CURSOR_START to "true", K.SNAP to "true",
        K.DESKTOP to "true", K.ZOOM to "125",
        K.ADBLOCK to "true", K.BLOCK_POPUPS to "true",
        K.SEEK_STEP to "10", K.THIRD_PARTY to "true",
        // Entry-level TVs start with the light settings; everything can be switched back on in Settings.
        K.THEME to "luna", K.GLASS to "false", K.REDUCE_MOTION to lowEnd.toString(), K.ANIMATED_BG to (!lowEnd).toString(),
        K.TIP_SHOWN to "false", K.LANGUAGE to "auto",
        K.PHONE to "true", K.PHONE_PIN to "",
        K.TILE_SIZE to TileSize.LARGE, K.HISTORY_VIEW to "list",
        K.UPDATE_CHECK to "true", K.UPDATE_LAST to "0", K.UPDATE_LATEST to "", K.UPDATE_URL to "",
        K.PROFILE to ProfileRules.DEFAULT, K.ASK_PROFILE to "false", K.READER_SIZE to "1",
        K.DARK_PAGES to "false", K.COOKIE_BANNERS to "true",
    )

    val settings = mutableStateMapOf<String, String>().apply {
        putAll(defaults)
        defaults.keys.forEach { k -> sp.getString("s.$k", null)?.let { put(k, it) } }
    }

    init {
        // The pairing PIN is made once, on first run.
        if (str(K.PHONE_PIN).isEmpty()) newPin()
    }

    /** Replaces the pairing PIN (phones have to pair again). */
    fun newPin() = put(K.PHONE_PIN, "%06d".format(java.security.SecureRandom().nextInt(1_000_000)))

    fun str(k: String): String = settings[k] ?: defaults[k] ?: ""
    fun bool(k: String): Boolean = str(k) == "true"
    fun int(k: String): Int = str(k).toIntOrNull() ?: 0
    fun put(k: String, v: Any) {
        settings[k] = v.toString()
        sp.edit().putString("s.$k", v.toString()).apply()
    }

    // ---- Profiles ----
    val profiles = mutableStateListOf<Profile>().apply { addAll(ProfileRules.parse(sp.getString("profiles", null))) }

    /** The active profile's id: "0" (the original data), "1".."9", or "guest" (erased when the session ends). */
    var profileId by mutableStateOf(str(K.PROFILE).takeIf { id -> profiles.any { it.id == id } } ?: ProfileRules.DEFAULT)
        private set

    val isGuest: Boolean get() = profileId == ProfileRules.GUEST
    val currentProfile: Profile get() = profiles.firstOrNull { it.id == profileId } ?: profiles.first()

    // ---- Profile PINs (a salted hash per profile, see PinRules) ----
    private val pins = mutableStateMapOf<String, String>().apply {
        profiles.forEach { p -> sp.getString("pin.${p.id}", null)?.let { put(p.id, it) } }
    }

    fun hasPin(id: String) = id != ProfileRules.GUEST && pins.containsKey(id)

    fun checkPin(id: String, pin: String) = PinRules.verify(pin, pins[id])

    /** Sets (or, with null, removes) a profile's PIN. */
    fun setPin(id: String, pin: String?) {
        if (id == ProfileRules.GUEST || profiles.none { it.id == id }) return
        if (pin == null) { pins.remove(id); sp.edit().remove("pin.$id").apply() }
        else if (PinRules.valid(pin)) { val e = PinRules.encode(pin); pins[id] = e; sp.edit().putString("pin.$id", e).apply() }
    }

    // Wrong-PIN tries survive a restart (otherwise leaving and reopening the app would give five fresh tries every time).
    fun pinFailures(): Int = sp.getInt("pinFails", 0)
    fun pinLockedUntil(): Long = sp.getLong("pinUntil", 0L)
    fun setPinFailures(fails: Int, until: Long) { sp.edit().putInt("pinFails", fails).putLong("pinUntil", until).apply() }

    /** The storage key of [name] for the active profile. */
    private fun pk(name: String) = ProfileRules.key(profileId, name)

    val favorites = mutableStateListOf<Site>().apply { addAll(readSites(pk("favorites"))) }
    val history = mutableStateListOf<HistoryEntry>().apply { addAll(readHistory()) }

    private val visits = mutableStateMapOf<String, Int>().apply { putAll(readVisits()) }
    // ---- Resume positions ("continue where you left off") ----
    private val positions = HashMap<String, Resume>().apply { putAll(readPositions()) }
    private var positionsWritten = 0L

    /** Where the video on [url] was left, or null when there is nothing worth resuming. */
    fun resumeFor(url: String): Resume? = synchronized(positions) { positions[ResumeRules.key(url)] }

    /**
     * Remembers the playing position. Called about twice a second while a video is fullscreen, so
     * the disk is only touched every few seconds, or when [flush] is true (leaving the video).
     */
    fun savePosition(url: String, pos: Double, dur: Double, flush: Boolean = false) {
        val key = ResumeRules.key(url)
        synchronized(positions) {
            when {
                ResumeRules.finished(pos, dur) && dur >= ResumeRules.MIN_DURATION -> positions.remove(key)
                ResumeRules.shouldSave(pos, dur) -> positions[key] = Resume(pos, dur, System.currentTimeMillis())
                else -> return
            }
            if (positions.size > MAX_POSITIONS) {
                positions.entries.sortedBy { it.value.time }.take(positions.size - MAX_POSITIONS).forEach { positions.remove(it.key) }
            }
        }
        val now = System.currentTimeMillis()
        if (flush || now - positionsWritten > 5_000) {
            positionsWritten = now
            val copy = synchronized(positions) { HashMap(positions) }
            val posKey = pk("positions")      // the key is chosen now: the profile may change before the write runs
            io.execute {
                val o = JSONObject()
                copy.forEach { (k, r) -> o.put(k, JSONArray().put(r.pos).put(r.dur).put(r.time)) }
                sp.edit().putString(posKey, o.toString()).apply()
            }
            if (flush) version++
        }
    }

    // ---- Open tabs (restored, frozen, after the app is closed) ----
    fun readTabs(): String? = sp.getString(pk("tabs"), null)

    fun writeTabs(json: String) {
        val key = pk("tabs")
        io.execute { sp.edit().putString(key, json).apply() }
    }

    // ---- Per-site settings (each field falls back to the global setting when the site has no opinion) ----
    private val siteOverrides = mutableStateMapOf<String, SiteOverride>().apply {
        putAll(SiteSettings.parseAll(sp.getString("sites", "{}")!!))
    }

    fun siteKey(url: String) = SiteSettings.keyOf(url)

    fun overrideFor(url: String): SiteOverride = siteOverrides[siteKey(url)] ?: SiteOverride()

    fun setOverride(url: String, o: SiteOverride) {
        val k = siteKey(url)
        if (k.isEmpty()) return
        if (o.isEmpty) siteOverrides.remove(k) else siteOverrides[k] = o
        val json = SiteSettings.serializeAll(siteOverrides.toMap())
        io.execute { sp.edit().putString("sites", json).apply() }
    }

    val siteOverrideCount: Int get() = siteOverrides.size

    fun clearSiteOverrides() {
        siteOverrides.clear()
        io.execute { sp.edit().putString("sites", "{}").apply() }
    }

    fun desktopFor(url: String) = overrideFor(url).desktop ?: bool(K.DESKTOP)
    fun zoomFor(url: String) = overrideFor(url).zoom ?: int(K.ZOOM)
    fun adblockFor(url: String) = overrideFor(url).adblock ?: bool(K.ADBLOCK)
    fun darkFor(url: String) = overrideFor(url).dark ?: bool(K.DARK_PAGES)

    /** How often the active profile opened [host] (for ranking address suggestions). */
    fun visitsOf(host: String): Int = visits[host] ?: 0
    fun pointerFor(url: String): Boolean? = overrideFor(url).pointer

    var lastSession by mutableStateOf(readSites(pk("last")).firstOrNull())
        private set

    /** Bumped on every change to history, favourites or artwork; screens memoise their rows on it. */
    var version by mutableIntStateOf(0)
        private set

    /** Disk writes happen off the UI thread, in order. */
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    // ---- Favorites ----
    fun isFavorite(url: String) = favorites.any { it.url == url }

    /** The favourites as JSON text for the phone page; a snapshot, so the server thread never reads the live list. */
    @Volatile var favoritesJson: String = "[]"
        private set

    private fun syncFavorites() {
        val arr = JSONArray()
        favorites.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url)) }
        favoritesJson = arr.toString()
    }

    init {
        syncFavorites()
        // A guest session never survives a restart (the app was killed while a guest was active).
        wipeData(ProfileRules.GUEST)
    }

    /** Adds a favourite (at the top). True when it is now a favourite (also when it already was); false when the list is full. */
    fun addFavorite(site: Site): Boolean {
        if (isFavorite(site.url)) return true
        if (favorites.size >= MAX_FAVORITES) return false
        favorites.add(0, site)
        version++
        writeSites(pk("favorites"), favorites)
        syncFavorites()
        return true
    }

    /** Returns true when the site is now a favorite. */
    fun toggleFavorite(site: Site): Boolean {
        val existing = favorites.indexOfFirst { it.url == site.url }
        val added = existing < 0
        if (added) favorites.add(0, site) else favorites.removeAt(existing)
        version++
        writeSites(pk("favorites"), favorites)
        syncFavorites()
        return added
    }

    fun removeFavorite(site: Site) {
        favorites.remove(site)
        version++
        writeSites(pk("favorites"), favorites)
        syncFavorites()
    }

    // ---- History ----
    fun recordVisit(title: String, url: String) {
        if (!url.startsWith("http")) return
        // The same page / video reached through another address form (m. or www., a #fragment, a playlist
        // parameter) is one history entry.
        val key = ResumeRules.key(url)
        val old = history.firstOrNull { ResumeRules.key(it.url) == key }
        val t = cleanTitle(title, url).ifBlank { old?.title ?: hostOf(url) }
        history.removeAll { ResumeRules.key(it.url) == key }
        history.add(0, HistoryEntry(t, url, System.currentTimeMillis(), old?.image ?: youtubeThumb(url), old?.snap))
        while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        if (old == null) {
            val h = hostOf(url)
            visits[h] = (visits[h] ?: 0) + 1
            sp.edit().putString(pk("visits"), JSONObject(visits.toMap()).toString()).apply()
        }
        lastSession = Site(t, url)
        version++
        writeHistory()
        writeSites(pk("last"), listOf(Site(t, url)))
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
            writeSites(pk("last"), listOf(Site(title, url)))
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
        val snaps = history.mapNotNull { it.snap }
        history.clear()
        visits.clear()
        lastSession = null
        version++
        clearTabThumbs()
        // Only this profile's thumbnails: the other profiles' cards still need theirs.
        io.execute { snaps.forEach { runCatching { File(it).delete() } } }
        synchronized(positions) { positions.clear() }
        sp.edit().putString(pk("visits"), "{}").putString(pk("positions"), "{}").apply()
        writeHistory()
        writeSites(pk("last"), emptyList())
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
        // No third-party icon service: until the site has been opened once (and its own icon saved), the card shows
        // a lettered tile in the site's brand colours.
        return null
    }

    /** Bumped when a tab thumbnail is written, so the tab switcher shows it at once. */
    var tabThumbRev by mutableIntStateOf(0)
        private set

    fun tabThumbFile(url: String) = File(tabThumbsDir, "${url.hashCode().toUInt()}.jpg")

    /** The tab's own screenshot when there is one; reads [tabThumbRev] so Compose refreshes. */
    fun tabThumb(url: String): File? { tabThumbRev; return tabThumbFile(url).takeIf { it.exists() } }

    fun tabThumbSaved() { tabThumbRev++ }

    fun deleteTabThumb(url: String) { io.execute { runCatching { tabThumbFile(url).delete() } } }

    fun clearTabThumbs() { io.execute { tabThumbsDir.listFiles()?.forEach { it.delete() } } }

    fun artForUrl(url: String): Any? = history.firstOrNull { it.url == url }?.art

    fun artForHost(host: String): Any? =
        history.firstOrNull { it.host == host && it.deep && it.image != null }?.image
            ?: history.firstOrNull { it.host == host }?.art

    /** "VnExpress" for known sites, otherwise the main label of the domain: en.wikipedia.org -> "Wikipedia". */
    private fun siteName(host: String) = KnownSites.firstOrNull { it.host == host }?.title
        ?: siteOf(host).substringBefore('.').replaceFirstChar { it.uppercase() }

    /** Frequently used sites first, topped up with the curated quick sites. */
    fun siteItems(lang: String, limit: Int = 10): List<MediaItem> {
        val quickSites = quickSitesFor(lang)
        val frequent = visits.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
        val hosts = (frequent + quickSites.map { it.host }).distinct().take(limit)
        return hosts.map { h ->
            val quick = quickSites.firstOrNull { it.host == h } ?: KnownSites.firstOrNull { it.host == h }
            MediaItem(
                key = "site:$h", title = siteName(h), subtitle = h,
                url = quick?.url ?: "https://$h", art = artForHost(h), icon = iconFor(h), brand = brandOf(h),
            )
        }
    }

    fun continueItems(limit: Int = 12): List<MediaItem> = history.take(limit).map {
        MediaItem("h:${it.url}", cleanTitle(it.title, it.url).ifBlank { it.title }, it.host, it.url, it.art, iconFor(it.host), brandOf(it.host), it.time, it.altArt, resumeFor(it.url)?.fraction ?: 0f)
    }

    fun favoriteItems(): List<MediaItem> = favorites.map {
        MediaItem("f:${it.url}", it.title, it.host, it.url, artForUrl(it.url) ?: artForHost(it.host), iconFor(it.host), brandOf(it.host))
    }

    // ---- Address resolution ----
    fun resolve(input: String): String {
        val t = input.trim()
        return when (AddressInput.classify(t)) {
            AddressInput.Kind.Empty -> ""
            AddressInput.Kind.Explicit -> t
            AddressInput.Kind.Local -> "http://$t"
            AddressInput.Kind.Bare -> "https://$t"
            AddressInput.Kind.Search -> searchUrl(t)
        }
    }

    /** The address of a web search for [query] with the chosen search engine. */
    fun searchUrl(query: String): String {
        val template = SearchEngines[str(K.ENGINE)] ?: SearchEngines.getValue("Google")
        return template.replace("%s", URLEncoder.encode(query.trim(), "UTF-8"))
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

    private fun readHistory(key: String = pk("history")): List<HistoryEntry> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
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
        val key = pk("history")
        io.execute { writeHistoryNow(key, copy) }
    }

    private fun writeHistoryNow(key: String, list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("t", it.title).put("u", it.url).put("ts", it.time)
                    .put("img", it.image ?: "").put("snap", it.snap ?: ""),
            )
        }
        sp.edit().putString(key, arr.toString()).apply()
    }

    private fun readVisits(): Map<String, Int> = runCatching {
        val o = JSONObject(sp.getString(pk("visits"), "{}")!!)
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun readPositions(): Map<String, Resume> = runCatching {
        val o = JSONObject(sp.getString(pk("positions"), "{}")!!)
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> Resume(a.getDouble(0), a.getDouble(1), a.getLong(2)) } }
    }.getOrDefault(emptyMap())

    // ---- Profile switching and management ----
    /**
     * Makes [id] the active profile: its history, favourites, positions and tabs replace the current ones. Leaving the guest
     * erases everything the guest did. (Open tabs of the profile being left are saved by the caller before it switches.)
     */
    fun switchProfile(id: String) {
        if (id == profileId) return
        if (id != ProfileRules.GUEST && profiles.none { it.id == id }) return
        val leaving = profileId
        clearTabThumbs()
        profileId = id
        if (leaving == ProfileRules.GUEST) wipeData(ProfileRules.GUEST)
        if (id != ProfileRules.GUEST) put(K.PROFILE, id)
        reloadData()
    }

    /** Ends a guest session (the app is closing): back to the remembered profile, guest data erased. */
    fun endGuest() {
        if (!isGuest) return
        switchProfile(str(K.PROFILE).takeIf { id -> profiles.any { it.id == id } } ?: ProfileRules.DEFAULT)
    }

    private fun reloadData() {
        favorites.clear(); favorites.addAll(readSites(pk("favorites")))
        history.clear(); history.addAll(readHistory())
        visits.clear(); visits.putAll(readVisits())
        synchronized(positions) { positions.clear(); positions.putAll(readPositions()) }
        lastSession = readSites(pk("last")).firstOrNull()
        syncFavorites()
        version++
    }

    /** Erases a profile's stored data (and its thumbnails), in order after any write still queued for it. */
    private fun wipeData(id: String) {
        val snaps = readHistory(ProfileRules.key(id, "history")).mapNotNull { it.snap }
        val keys = ProfileRules.dataKeys.map { ProfileRules.key(id, it) }
        io.execute {
            sp.edit().apply { keys.forEach { remove(it) } }.apply()
            snaps.forEach { runCatching { File(it).delete() } }
        }
    }

    private fun saveProfiles() {
        sp.edit().putString("profiles", ProfileRules.serialize(profiles.toList())).apply()
    }

    /** A new profile, or null when the limit is reached. */
    fun addProfile(name: String): Profile? {
        val id = ProfileRules.nextId(profiles.toList()) ?: return null
        val p = Profile(id, ProfileRules.cleanName(name), ProfileRules.nextColor(profiles.toList()))
        profiles.add(p)
        saveProfiles()
        return p
    }

    fun renameProfile(id: String, name: String) {
        val i = profiles.indexOfFirst { it.id == id }
        if (i < 0) return
        profiles[i] = profiles[i].copy(name = ProfileRules.cleanName(name))
        saveProfiles()
    }

    fun recolorProfile(id: String, color: Int) {
        val i = profiles.indexOfFirst { it.id == id }
        if (i < 0) return
        profiles[i] = profiles[i].copy(color = color.coerceIn(0, ProfileRules.colors.size - 1))
        saveProfiles()
    }

    /** Deletes a profile and its data. The default profile and the one in use cannot be deleted. */
    fun deleteProfile(id: String): Boolean {
        if (id == ProfileRules.DEFAULT || id == profileId) return false
        val i = profiles.indexOfFirst { it.id == id }
        if (i < 0) return false
        profiles.removeAt(i)
        pins.remove(id)
        sp.edit().remove("pin.$id").apply()
        saveProfiles()
        wipeData(id)
        return true
    }

    private companion object {
        const val MAX_HISTORY = 300
        const val MAX_FAVORITES = 60
        const val MAX_POSITIONS = 150
    }
}
