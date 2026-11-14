package com.luna.browser.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** Host-based ad/tracker blocker. Built-in list ships in assets; the full list can be refreshed online. */
class AdBlocker(private val ctx: Context) {
    @Volatile
    private var hosts: Set<String> = emptySet()
    private val counter = AtomicInteger()
    private val main = Handler(Looper.getMainLooper())
    private val file = File(ctx.filesDir, "filters.txt")
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

    init {
        Thread { reload() }.start()
    }

    private fun reload() {
        val set = HashSet<String>()
        runCatching {
            ctx.assets.open("hosts.txt").bufferedReader().useLines { l -> l.forEach { parse(it)?.let(set::add) } }
        }
        if (file.exists()) runCatching {
            file.bufferedReader().useLines { l -> l.forEach { parse(it)?.let(set::add) } }
        }
        hosts = set
        main.post { listSize = set.size }
    }

    private fun parse(line: String): String? {
        val t = line.substringBefore('#').trim()
        if (t.isEmpty()) return null
        val parts = t.split(Regex("\\s+"))
        val h = when {
            parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
            parts.size == 1 -> parts[0]
            else -> return null
        }.lowercase()
        if (!h.contains('.') || h == "0.0.0.0") return null
        return h
    }

    fun shouldBlock(uri: Uri): Boolean {
        var h = uri.host?.lowercase() ?: return false
        val set = hosts
        while (h.contains('.')) {
            if (h in set) {
                main.post { blockedCount = counter.incrementAndGet() }
                return true
            }
            h = h.substringAfter('.')
        }
        return false
    }

    /** Downloads the full host list. Returns true on success; on failure [updateFailed] is set. */
    suspend fun update(): Boolean {
        if (updating) return false
        updating = true
        updateFailed = false
        val ok = withContext(Dispatchers.IO) {
            try {
                val c = URL(FILTER_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 25_000
                if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                val text = c.inputStream.bufferedReader().readText()
                if (text.lines().count { parse(it) != null } < 500) error("List looks empty")
                file.writeText(text)
                reload()
                true
            } catch (e: Exception) {
                false
            }
        }
        updating = false
        updateFailed = !ok
        if (ok) {
            lastUpdated = System.currentTimeMillis()
            prefs.edit().putLong("updated", lastUpdated).apply()
        }
        return ok
    }

    companion object {
        const val FILTER_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

        /** Hides common in-page ad containers. */
        const val COSMETIC_JS = """(function(){var s=document.getElementById('__luna_css');if(s)return;
s=document.createElement('style');s.id='__luna_css';
s.textContent='ins.adsbygoogle,[id^="google_ads"],[id^="div-gpt-ad"],.ad-container,.adsbox,iframe[src*="doubleclick.net"]{display:none!important}';
(document.head||document.documentElement).appendChild(s);})()"""
    }
}
