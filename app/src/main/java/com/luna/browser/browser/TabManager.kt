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
class Tab(url: String = "", title: String = "", val isPrivate: Boolean = false) {
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

    /** Loads the tabs of the (new) active profile; the caller has already released the old ones with [destroyAll]. */
    fun reload() {
        endSplit()
        tabs.forEach { it.freeze() }
        tabs.clear()
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
            val keep = setOfNotNull(index, partnerIndex())
            val victim = TabPolicy.victim(live, tabs.indexOf(t), keep, maxLive) ?: break
            tabs[victim].freeze()
        }
        val saved = t.savedPair()
        val c = BrowserController(app.activity, app, t.isPrivate)
        t.controller = c
        if (saved.first.startsWith("http")) c.load(saved.first)
        return c
    }

    // ---- Split screen: two tabs side by side; the current tab is the half that gets the keys ----
    var splitLeft by mutableStateOf<Tab?>(null)
        private set
    var splitRight by mutableStateOf<Tab?>(null)
        private set

    val splitActive: Boolean
        get() { val l = splitLeft; val r = splitRight; return l != null && r != null && l !== r && tabs.contains(l) && tabs.contains(r) && (current === l || current === r) }

    /** The index of the other half of the split screen, or null. */
    fun partnerIndex(): Int? {
        if (!splitActive) return null
        val p = if (current === splitLeft) splitRight else splitLeft
        return tabs.indexOf(p).takeIf { it >= 0 }
    }

    /** The other half's controller (made live if it was frozen), or null without a split screen. */
    fun partnerController(): BrowserController? = partnerIndex()?.let { live(tabs[it]) }

    /** Shows tab [other] next to the current one: the current tab stays on the left. */
    fun startSplit(other: Int) {
        if (other !in tabs.indices || other == index) return
        splitLeft = current
        splitRight = tabs[other]
    }

    fun endSplit() {
        splitLeft = null
        splitRight = null
    }

    /** The other half becomes current (gets the keys). */
    fun switchPane() { partnerIndex()?.let { select(it) } }

    /** A partner for a new split screen (most recently used other tab with a page), or null. */
    fun suggestPartner(): Int? = TabPolicy.splitPartner(tabs.map { it.lastUsed }, tabs.map { !it.isEmpty }, index)

    fun select(i: Int) {
        if (i !in tabs.indices) return
        index = i
        saveSoon()
    }

    /** Adds an empty tab and makes it current; false when the limit is reached. */
    fun add(isPrivate: Boolean = false): Boolean {
        if (tabs.size >= TabPolicy.MAX_TABS) return false
        tabs.add(Tab(isPrivate = isPrivate))
        index = tabs.lastIndex
        saveSoon()
        return true
    }

    fun close(i: Int) {
        if (i !in tabs.indices) return
        if (tabs[i] === splitLeft || tabs[i] === splitRight) endSplit()
        val wasPrivate = tabs[i].isPrivate
        if (!wasPrivate) tabs[i].url.takeIf { it.startsWith("http") }?.let { app.store.deleteTabThumb(it) }
        tabs[i].freeze()
        tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(Tab())
        index = TabPolicy.indexAfterClose(index, i, tabs.size)
        saveSoon()
        if (wasPrivate) endPrivateIfNone()
    }

    fun closeOthers(keep: Int) {
        if (keep !in tabs.indices) return
        endSplit()
        var closedPrivate = false
        for (i in tabs.lastIndex downTo 0) if (i != keep) { closedPrivate = closedPrivate || tabs[i].isPrivate; tabs[i].freeze(); tabs.removeAt(i) }
        index = 0
        saveSoon()
        if (closedPrivate) endPrivateIfNone()
    }

    /** The tab showing [url], or -1. */
    fun indexOfUrl(url: String): Int = tabs.indexOfFirst { !it.isPrivate && it.url == url }

    /** Whether the private session is over (no private tab left): its sign-ins and site data are erased. */
    private fun endPrivateIfNone() {
        if (tabs.any { it.isPrivate }) return
        WebProfiles.wipePrivate()
        // The engine may still hold the closed WebView for a moment, which blocks removing the profile: once more later.
        handler.postDelayed({ if (tabs.none { it.isPrivate }) WebProfiles.wipePrivate() }, 1500)
    }

    // ---- Persistence ----
    private val save = Runnable {
        // Private tabs are never written down.
        val (keep, cur) = TabPolicy.persistable(tabs.map { it.isPrivate }, index)
        app.store.writeTabs(TabPolicy.toJson(keep.map { tabs[it].savedPair() }, cur))
    }

    /** Called whenever tabs or their pages change; coalesced, since pages change often. */
    fun saveSoon() {
        handler.removeCallbacks(save)
        handler.postDelayed(save, 1500)
    }

    fun destroyAll() {
        handler.removeCallbacks(save)
        save.run()
        val hadPrivate = tabs.any { it.isPrivate }
        tabs.forEach { it.freeze() }
        if (hadPrivate) WebProfiles.wipePrivate()
    }
}
