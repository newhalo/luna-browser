package com.luna.browser.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.luna.browser.data.filter.urlHost
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luna.browser.data.filter.FilterBuilder
import com.luna.browser.data.filter.FilterEngine
import com.luna.browser.data.filter.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * A filter list Luna uses. Lists are plain text in Adblock Plus syntax (or hosts format), so
 * community lists work unchanged. To support a new list, add an entry here — nothing else.
 */
data class FilterList(
    val id: String,
    val name: String,
    val description: String,
    /** Download URL; null for lists that only ship inside the app. */
    val url: String?,
    /** Bundled copy in assets, used until (or instead of) a download. */
    val asset: String? = null,
    val enabledByDefault: Boolean = true,
)

val FilterLists = listOf(
    FilterList(
        "luna", "Bộ lọc Luna", "Luật riêng của Luna cho trang phim và quảng cáo bật lên. Đi kèm ứng dụng.",
        url = null, asset = "filters/luna.txt",
    ),
    FilterList(
        "abpvn", "ABPVN", "Bộ lọc quảng cáo cho người Việt, do cộng đồng ABPVN cập nhật hằng ngày.",
        url = "https://raw.githubusercontent.com/abpvn/abpvn/master/filter/abpvn.txt",
    ),
    FilterList(
        "abpvn-hosts", "ABPVN – tên miền quảng cáo", "Hơn 20.000 tên miền quảng cáo, cá cược và theo dõi.",
        url = "https://abpvn.com/android/abpvn.txt",
    ),
)

data class ListStatus(val rules: Int = 0, val updatedAt: Long = 0L, val failed: Boolean = false)

/** Owns the filter lists, keeps them fresh and answers "should this be blocked?". */
class AdBlocker(private val ctx: Context) {
    @Volatile
    private var engine: FilterEngine = FilterBuilder().build()
    private val counter = AtomicInteger()
    private val main = Handler(Looper.getMainLooper())
    private val dir = File(ctx.filesDir, "filters").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("luna_filters", Context.MODE_PRIVATE)

    var blockedCount by mutableIntStateOf(0)
        private set
    var listSize by mutableIntStateOf(0)
        private set
    var updating by mutableStateOf(false)
        private set
    var updateFailed by mutableStateOf(false)
        private set
    var lastUpdated by mutableLongStateOf(prefs.getLong("updated", 0L))
        private set
    val status = mutableStateMapOf<String, ListStatus>()
    private val enabled = mutableStateMapOf<String, Boolean>().apply {
        FilterLists.forEach { put(it.id, prefs.getBoolean("on.${it.id}", it.enabledByDefault)) }
    }

    init {
        FilterLists.forEach { status[it.id] = ListStatus(updatedAt = prefs.getLong("at.${it.id}", 0L)) }
        // Low priority: compiling ~20k rules must not steal CPU from the first frames on a weak TV SoC.
        background { compile() }
    }

    private fun background(work: () -> Unit) = Thread {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        work()
    }.start()

    fun isEnabled(id: String) = enabled[id] == true

    fun setEnabled(id: String, on: Boolean) {
        enabled[id] = on
        prefs.edit().putBoolean("on.${id}", on).apply()
        background { compile() }
    }

    private fun fileOf(l: FilterList) = File(dir, "${l.id}.txt")

    private fun textOf(l: FilterList): String? {
        val f = fileOf(l)
        if (f.exists()) return runCatching { f.readText() }.getOrNull()
        return l.asset?.let { a -> runCatching { ctx.assets.open(a).bufferedReader().use { it.readText() } }.getOrNull() }
    }

    /** Rebuilds the engine from every enabled list (background thread). */
    @Synchronized
    private fun compile() {
        val b = FilterBuilder()
        val counts = HashMap<String, Int>()
        FilterLists.forEach { l ->
            if (enabled[l.id] != true) return@forEach
            textOf(l)?.let { counts[l.id] = b.add(it) }
        }
        val e = b.build()
        engine = e
        main.post {
            listSize = e.networkRuleCount + e.cosmeticRuleCount
            FilterLists.forEach { l -> status[l.id] = (status[l.id] ?: ListStatus()).copy(rules = counts[l.id] ?: 0) }
        }
    }

    // ---- Queries (called from WebView threads) ----

    fun shouldBlock(url: String, pageUrl: String, type: Int): Boolean {
        if (!url.startsWith("http")) return false
        val hit = engine.match(url, pageUrl, type) ?: return false
        countBlocked()
        // Debug builds: tools/sitecheck reads these lines to report which rule blocked what.
        if (debuggable) Log.d("LunaFilter", "blocked ${url.take(160)} on ${urlHost(pageUrl)} by $hit")
        return true
    }

    private val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** True when a filter list says a pop-up to [target] from [pageUrl] must not open. */
    fun blocksPopup(target: String, pageUrl: String) = engine.match(target, pageUrl, Type.POPUP) != null

    fun cosmeticCss(pageUrl: String): String = engine.css(pageUrl)

    fun countBlocked() {
        main.post { blockedCount = counter.incrementAndGet() }
    }

    // ---- Updates ----

    /** Downloads every enabled list that has a URL. Returns true if all succeeded. */
    suspend fun update(): Boolean {
        if (updating) return false
        updating = true
        updateFailed = false
        val results = withContext(Dispatchers.IO) {
            FilterLists.filter { it.url != null && enabled[it.id] == true }.associate { l -> l.id to download(l) }
        }
        val now = System.currentTimeMillis()
        results.forEach { (id, ok) ->
            val prev = status[id] ?: ListStatus()
            status[id] = if (ok) prev.copy(updatedAt = now, failed = false) else prev.copy(failed = true)
            if (ok) prefs.edit().putLong("at.$id", now).apply()
        }
        withContext(Dispatchers.IO) { compile() }
        val allOk = results.values.all { it }
        updating = false
        updateFailed = !allOk
        if (results.values.any { it }) {
            lastUpdated = now
            prefs.edit().putLong("updated", now).apply()
        }
        return allOk
    }

    private fun download(l: FilterList): Boolean = try {
        val c = URL(l.url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "LunaBrowser/0.2 (Android TV)")
        if (c.responseCode != 200) error("HTTP ${c.responseCode}")
        val text = c.inputStream.bufferedReader().use { it.readText() }
        // Guard against captive portals / error pages replacing a real list.
        if (text.lines().count { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("#") } < 50) error("too short")
        val tmp = File(dir, "${l.id}.tmp")
        tmp.writeText(text)
        tmp.renameTo(fileOf(l))
    } catch (e: Exception) {
        false
    }

    /** Lists auto-refresh once a day, and right away on first run (nothing downloaded yet). */
    fun needsUpdate(): Boolean {
        val missing = FilterLists.any { it.url != null && enabled[it.id] == true && !fileOf(it).exists() }
        return missing || System.currentTimeMillis() - lastUpdated > 24 * 3600_000L
    }

    companion object {
        private val scriptExt = Regex("\\.(m?js)(\\?|$)")
        private val imageExt = Regex("\\.(png|jpe?g|gif|webp|avif|svg|ico|bmp)(\\?|$)")
        private val cssExt = Regex("\\.css(\\?|$)")
        private val mediaExt = Regex("\\.(mp4|webm|m3u8|ts|mp3|aac|m4a|m4s|mpd)(\\?|$)")
        private val fontExt = Regex("\\.(woff2?|ttf|otf|eot)(\\?|$)")

        /** WebView doesn't report resource types; infer them from Accept headers and extensions. */
        fun typeOf(req: WebResourceRequest): Int {
            if (req.isForMainFrame) return Type.DOCUMENT
            val accept = req.requestHeaders["Accept"] ?: req.requestHeaders["accept"] ?: ""
            val path = req.url.path?.lowercase() ?: ""
            return when {
                accept.startsWith("text/html") -> Type.SUBDOCUMENT
                accept.startsWith("image/") || imageExt.containsMatchIn(path) -> Type.IMAGE
                accept.startsWith("text/css") || cssExt.containsMatchIn(path) -> Type.STYLESHEET
                scriptExt.containsMatchIn(path) -> Type.SCRIPT
                mediaExt.containsMatchIn(path) || accept.startsWith("video/") || accept.startsWith("audio/") -> Type.MEDIA
                fontExt.containsMatchIn(path) -> Type.FONT
                else -> Type.AMBIGUOUS
            }
        }
    }
}
