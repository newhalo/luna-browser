package com.luna.browser.browser

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luna.browser.AppState
import com.luna.browser.data.DeviceProfile

/**
 * One page the viewer has open. It owns a [BrowserController] (a WebView with its pointer, video and page state)
 * only while it is "live"; a frozen tab keeps just its address and title and is loaded again when selected.
 */
class Tab(url: String = "", title: String = "") {
    var controller: BrowserController? = null
    var lastUsed = 0L

    private var savedUrl by mutableStateOf(url)
    private var savedTitle by mutableStateOf(title)

    /** The live page's address, otherwise the saved one (both observable from Compose). */
    val url: String get() = controller?.url?.takeIf { it.isNotBlank() } ?: savedUrl
    val title: String get() = controller?.title?.takeIf { it.isNotBlank() } ?: savedTitle
    val isEmpty: Boolean get() = url.isBlank()

    /** Releases the WebView but remembers where the tab was. */
    fun freeze() {
        controller?.let {
            savedUrl = it.url.ifBlank { savedUrl }
            savedTitle = it.title.ifBlank { savedTitle }
            it.destroy()
        }
        controller = null
    }

    internal fun savedPair() = url to title
    internal fun rememberSaved(u: String, t: String) { savedUrl = u; savedTitle = t }
}

/** The open tabs and which one is shown. Tabs are restored (frozen) after the app is closed. */
class TabManager(private val app: AppState) {
    val tabs = mutableStateListOf<Tab>()

    var index by mutableIntStateOf(0)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val maxLive = TabPolicy.maxLive(DeviceProfile.isLowEnd(app.activity))

    init {
        val (saved, cur) = TabPolicy.fromJson(app.store.readTabs())
        saved.forEach { (u, t) -> tabs.add(Tab(u, t)) }
        if (tabs.isEmpty()) tabs.add(Tab())
        index = cur.coerceIn(0, tabs.lastIndex)
    }

    val count: Int get() = tabs.size
    val current: Tab get() = tabs[index.coerceIn(0, tabs.lastIndex)]

    /** Whether any tab holds a page (the Home screen offers "Tabs" only then). */
    val hasContent: Boolean get() = tabs.any { !it.isEmpty }

    /** The controller of the tab being shown, created on first use (a WebView is not cheap on a TV). */
    fun controller(): BrowserController = live(current)

    private fun live(t: Tab): BrowserController {
        t.lastUsed = SystemClock.uptimeMillis()
        t.controller?.let { return it }
        // Make room first: freeze the least recently used live tabs until this one fits.
        while (true) {
            val live = tabs.indices.filter { tabs[it].controller != null }.map { TabPolicy.LiveTab(it, tabs[it].lastUsed) }
            val victim = TabPolicy.victim(live, tabs.indexOf(t), index, maxLive) ?: break
            tabs[victim].freeze()
        }
        val saved = t.savedPair()
        val c = BrowserController(app.activity, app)
        t.controller = c
        if (saved.first.startsWith("http")) c.load(saved.first)
        return c
    }

    fun select(i: Int) {
        if (i !in tabs.indices) return
        index = i
        saveSoon()
    }

    /** Adds an empty tab and makes it current; false when the limit is reached. */
    fun add(): Boolean {
        if (tabs.size >= TabPolicy.MAX_TABS) return false
        tabs.add(Tab())
        index = tabs.lastIndex
        saveSoon()
        return true
    }

    fun close(i: Int) {
        if (i !in tabs.indices) return
        tabs[i].freeze()
        tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(Tab())
        index = TabPolicy.indexAfterClose(index, i, tabs.size)
        saveSoon()
    }

    fun closeOthers(keep: Int) {
        if (keep !in tabs.indices) return
        for (i in tabs.lastIndex downTo 0) if (i != keep) { tabs[i].freeze(); tabs.removeAt(i) }
        index = 0
        saveSoon()
    }

    /** The tab showing [url], or -1. */
    fun indexOfUrl(url: String): Int = tabs.indexOfFirst { it.url == url }

    // ---- Persistence ----
    private val save = Runnable {
        app.store.writeTabs(TabPolicy.toJson(tabs.map { it.savedPair() }, index))
    }

    /** Called whenever tabs or their pages change; coalesced, since pages change often. */
    fun saveSoon() {
        handler.removeCallbacks(save)
        handler.postDelayed(save, 1500)
    }

    fun destroyAll() {
        handler.removeCallbacks(save)
        save.run()
        tabs.forEach { it.freeze() }
    }
}
