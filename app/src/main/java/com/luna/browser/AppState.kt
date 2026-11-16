package com.luna.browser

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luna.browser.browser.TabManager
import com.luna.browser.browser.TabPolicy
import com.luna.browser.browser.BrowserController
import com.luna.browser.data.AdBlocker
import com.luna.browser.data.K
import com.luna.browser.data.Site
import com.luna.browser.data.hostOf
import com.luna.browser.data.Store
import com.luna.browser.remote.PadGestures
import com.luna.browser.remote.PhoneRemote
import com.luna.browser.remote.RemoteAction
import com.luna.browser.remote.SwipeSteps
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

enum class Screen { Home, Browser, Settings, History, Favorites, Tabs, Profiles }

/** The keys the phone pad may press (anything else is refused). */
private val REMOTE_KEYS = mapOf(
    "DPAD_UP" to KeyEvent.KEYCODE_DPAD_UP, "DPAD_DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
    "DPAD_LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "DPAD_RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
    "DPAD_CENTER" to KeyEvent.KEYCODE_DPAD_CENTER, "BACK" to KeyEvent.KEYCODE_BACK, "MENU" to KeyEvent.KEYCODE_MENU,
    "MEDIA_PLAY_PAUSE" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    "MEDIA_REWIND" to KeyEvent.KEYCODE_MEDIA_REWIND, "MEDIA_FAST_FORWARD" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
)

/** Single source of truth for navigation, shared services and global key handling. */
class AppState(val activity: Activity) {
    val store = Store(activity)
    val ads = AdBlocker(activity)
    val tabs = TabManager(this)

    /** The page being shown: the current tab's controller (its WebView is created on first use). */
    val browser: BrowserController get() = tabs.controller()
    private var shownBrowser: BrowserController? = null

    var screen by mutableStateOf(Screen.Home)
        private set
    private val stack = ArrayDeque<Screen>()

    var addressOpen by mutableStateOf(false)

    /** A screen's own overlay (an edit panel) can claim Back first: return true when it closed something. */
    var closeOverlay: (() -> Boolean)? = null
    var addressInitial = ""
    var toastMsg by mutableStateOf<String?>(null)
    var online by mutableStateOf(true)
    val focusMemory = mutableMapOf<String, String>()
    var onExit: () -> Unit = {}
    var voiceAvailable by mutableStateOf(false)
    var startVoice: () -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    val scope = kotlinx.coroutines.MainScope()

    // ---- Phone remote ----
    /** True while Luna is on screen: the phone remote only listens then. */
    var foreground by mutableStateOf(false)

    val remote = PhoneRemote(
        pin = { store.str(K.PHONE_PIN) },
        labels = {
            mapOf(
                "ph_title" to s(R.string.ph_title), "ph_pin" to s(R.string.ph_pin), "ph_pair" to s(R.string.ph_pair),
                "ph_hint" to s(R.string.ph_hint), "ph_open" to s(R.string.ph_open), "ph_search" to s(R.string.ph_search),
                "ph_type" to s(R.string.ph_type), "ph_enter" to s(R.string.ph_enter), "ph_sent" to s(R.string.ph_sent),
                "ph_bad_pin" to s(R.string.ph_bad_pin), "ph_locked" to s(R.string.ph_locked), "ph_nofield" to s(R.string.ph_nofield),
                "ph_tab_remote" to s(R.string.ph_tab_remote), "ph_tab_type" to s(R.string.ph_tab_type),
                "ph_share_ask" to s(R.string.ph_share_ask), "ph_share_no" to s(R.string.ph_share_no), "ph_bm_title" to s(R.string.ph_bm_title),
                "ph_bm_text" to s(R.string.ph_bm_text), "ph_bm_copy" to s(R.string.ph_bm_copy), "ph_copied" to s(R.string.ph_copied),
                "ph_tab_favs" to s(R.string.ph_tab_favs), "ph_fav_save" to s(R.string.ph_fav_save), "ph_fav_saved" to s(R.string.ph_fav_saved),
                "ph_fav_empty" to s(R.string.ph_fav_empty), "ph_fav_remove" to s(R.string.ph_fav_remove), "ph_fav_confirm" to s(R.string.ph_fav_confirm),
                "ph_fav_err" to s(R.string.ph_fav_err),
                "ph_pad_dpad" to s(R.string.ph_pad_dpad), "ph_pad_touch" to s(R.string.ph_pad_touch), "ph_pad_hint" to s(R.string.ph_pad_hint),
            )
        },
        perform = { action, text, done -> main.post { performRemote(action, text, done) } },
        favorites = { store.favoritesJson },
    )

    /** The pre-filled GitHub report address for the page at [url] (shown as a QR code), or null for non-web pages. */
    fun reportLink(url: String): String? {
        val version = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "?"
        val web = runCatching { android.webkit.WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
        val lists = ads.lastUpdated.takeIf { it > 0 }?.let { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(it)) }
        return com.luna.browser.data.ReportLink.build(url, com.luna.browser.data.ReportLink.environment(version, web, lists, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"))
    }

    // ---- The TV's web engine ----
    /** Version of the system WebView (Chromium), e.g. "157.0.8103.38", or null when it can't be read. */
    val webViewVersion: String? by lazy { runCatching { android.webkit.WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() }
    val webViewLevel: com.luna.browser.data.WebViewCheck.Level by lazy { com.luna.browser.data.WebViewCheck.level(webViewVersion) }

    // ---- Updates (builds installed outside a store only) ----
    /** The installed version, without a build suffix such as "-debug". */
    val versionName: String by lazy {
        runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull()
            ?.substringBefore('-') ?: "0"
    }

    /** True when a store installed Luna: the store updates it, so Luna doesn't. */
    val storeInstall: Boolean by lazy {
        val installer = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) activity.packageManager.getInstallSourceInfo(activity.packageName).installingPackageName
            else @Suppress("DEPRECATION") activity.packageManager.getInstallerPackageName(activity.packageName)
        }.getOrNull()
        com.luna.browser.data.UpdateCheck.isStoreInstall(installer)
    }

    /** The newer version found by the last check, or null. Observable (reads the settings map). */
    val updateAvailable: String?
        get() = store.str(K.UPDATE_LATEST).takeIf { com.luna.browser.data.UpdateCheck.isNewer(it, versionName) }

    val updateUrl: String get() = store.str(K.UPDATE_URL)

    var updateChecking by mutableStateOf(false)
        private set

    /** Asks GitHub for the latest release. Returns true when the answer was understood (newer or not), false on any failure. */
    suspend fun checkUpdate(): Boolean {
        if (storeInstall || updateChecking) return false
        updateChecking = true
        try {
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val c = java.net.URL(com.luna.browser.data.UpdateCheck.API).openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 10_000
                    c.readTimeout = 15_000
                    c.setRequestProperty("Accept", "application/vnd.github+json")
                    c.setRequestProperty("User-Agent", "LunaBrowser/$versionName (Android TV)")
                    try {
                        if (c.responseCode != 200) null else c.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                if (out.size() + n > com.luna.browser.data.UpdateCheck.MAX_BYTES) return@use null
                                out.write(buf, 0, n)
                            }
                            out.toString("UTF-8")
                        }
                    } finally { c.disconnect() }
                }.getOrNull()
            }
            val release = body?.let { com.luna.browser.data.UpdateCheck.fromJson(it) } ?: return false
            store.put(K.UPDATE_LATEST, release.version)
            store.put(K.UPDATE_URL, release.url)
            store.put(K.UPDATE_LAST, System.currentTimeMillis())
            return true
        } finally {
            updateChecking = false
        }
    }

    private fun updateDue() = !storeInstall && store.bool(K.UPDATE_CHECK) &&
        System.currentTimeMillis() - (store.str(K.UPDATE_LAST).toLongOrNull() ?: 0L) > com.luna.browser.data.UpdateCheck.DAY_MS

    private var addressCache: Pair<Long, String?> = 0L to null

    /** "http://192.168.1.20:8765" while the phone remote is listening, else null. Cached: it asks the network stack. */
    fun phoneAddress(): String? {
        val port = remote.port
        if (port == 0) return null
        val now = System.currentTimeMillis()
        if (now - addressCache.first > 5_000) addressCache = now to PhoneRemote.localAddress()
        return addressCache.second?.let { "http://$it:$port" }
    }

    private fun performRemote(action: RemoteAction, text: String, done: (Boolean) -> Unit) {
        if (locked && action != RemoteAction.Ping) return done(false)    // the PIN picker is showing: the phone can't open anything
        when (action) {
            RemoteAction.Ping -> done(true)
            RemoteAction.Open, RemoteAction.Search -> {
                val url = if (action == RemoteAction.Search) store.searchUrl(text) else store.resolve(text)
                // Only web pages: no file://, intent:// and the like, even with the right PIN.
                if (!url.startsWith("http://") && !url.startsWith("https://")) return done(false)
                toast(R.string.toast_phone_open, text.take(60))
                browser.load(url)
                if (screen != Screen.Browser) navigate(Screen.Browser)
                done(true)
            }
            RemoteAction.Type, RemoteAction.TypeEnter ->
                if (screen == Screen.Browser) browser.typeIntoFocused(text, action == RemoteAction.TypeEnter, done) else done(false)
            RemoteAction.Key, RemoteAction.KeyDown, RemoteAction.KeyUp -> {
                val code = REMOTE_KEYS[text]
                if (code == null) done(false) else { injectKey(code, action); done(true) }
            }
            RemoteAction.FavAdd -> {
                // "address" or "address<newline>title". Only a web address (never a search phrase or another scheme).
                val url = com.luna.browser.data.FavoriteInput.address(text.substringBefore('\n')) ?: return done(false)
                val title = text.substringAfter('\n', "").trim().ifEmpty { store.history.firstOrNull { it.url == url }?.title ?: hostOf(url) }
                val ok = store.addFavorite(Site(title.take(120), url))
                if (ok) toast(R.string.toast_fav_added)
                done(ok)
            }
            RemoteAction.FavDel -> {
                store.favorites.firstOrNull { it.url == text.trim() }?.let { store.removeFavorite(it); toast(R.string.toast_fav_removed) }
                done(true)
            }
            RemoteAction.Volume -> done(adjustVolume(text))
            RemoteAction.Home -> { goHome(); done(true) }
            RemoteAction.Move -> {
                val m = PadGestures.parse(text)
                if (m == null) done(false) else { padMove(m.first, m.second); done(true) }
            }
            RemoteAction.Scroll -> {
                val m = PadGestures.parse(text)
                if (m == null) done(false) else {
                    if (screen == Screen.Browser && browser.customView == null) browser.scrollByFraction(m.second / 1000f)
                    done(true)
                }
            }
            RemoteAction.Tap -> {
                swipe.reset()
                if (padPointer()) browser.clickAtPointer() else injectKey(KeyEvent.KEYCODE_DPAD_CENTER, RemoteAction.Key)
                done(true)
            }
        }
    }

    // Keys from the phone pad go through the activity like the real remote's, so everything (focus
    // navigation, the pointer, fullscreen video controls) reacts exactly the same.
    private val padDown = HashMap<Int, Pair<Long, Int>>()   // key code -> (down time, repeat count)
    private val padRelease = HashMap<Int, Runnable>()

    private fun sendKey(code: Int, action: Int, repeat: Int, downTime: Long) {
        activity.dispatchKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), action, code, repeat))
    }

    private fun injectKey(code: Int, action: RemoteAction) {
        val now = SystemClock.uptimeMillis()
        when (action) {
            RemoteAction.Key -> {
                sendKey(code, KeyEvent.ACTION_DOWN, 0, now)
                sendKey(code, KeyEvent.ACTION_UP, 0, now)
            }
            RemoteAction.KeyDown -> {
                val held = padDown[code]
                val (downTime, repeat) = if (held == null) now to 0 else held.first to held.second + 1
                padDown[code] = downTime to repeat
                sendKey(code, KeyEvent.ACTION_DOWN, repeat, downTime)
                // If the phone disappears while a key is held, release it so the pointer doesn't run away.
                padRelease.remove(code)?.let { main.removeCallbacks(it) }
                Runnable { injectKey(code, RemoteAction.KeyUp) }.also { padRelease[code] = it; main.postDelayed(it, 1500) }
            }
            RemoteAction.KeyUp -> {
                val held = padDown.remove(code) ?: return
                padRelease.remove(code)?.let { main.removeCallbacks(it) }
                sendKey(code, KeyEvent.ACTION_UP, 0, held.first)
            }
            else -> Unit
        }
    }

    // ---- Touchpad ----
    private val swipe = SwipeSteps()

    /** True when the page's pointer is what the touchpad should move (browser, pointer mode, nothing on top). */
    private fun padPointer() = screen == Screen.Browser && browser.pointerInteractive

    private fun padMove(dx: Int, dy: Int) {
        if (padPointer()) {
            browser.moveCursorBy(dx / 1000f, dy / 1000f)
        } else {
            // Menus, home, link mode, video controls: swipes become D-pad presses.
            swipe.feed(dx, dy).forEach { d ->
                val code = when (d) {
                    'L' -> KeyEvent.KEYCODE_DPAD_LEFT
                    'R' -> KeyEvent.KEYCODE_DPAD_RIGHT
                    'U' -> KeyEvent.KEYCODE_DPAD_UP
                    else -> KeyEvent.KEYCODE_DPAD_DOWN
                }
                injectKey(code, RemoteAction.Key)
            }
        }
    }

    private fun adjustVolume(what: String): Boolean {
        val am = activity.getSystemService(android.media.AudioManager::class.java) ?: return false
        val dir = when (what) {
            "up" -> android.media.AudioManager.ADJUST_RAISE
            "down" -> android.media.AudioManager.ADJUST_LOWER
            "mute" -> android.media.AudioManager.ADJUST_TOGGLE_MUTE
            else -> return false
        }
        am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI)
        return true
    }

    init {
        scope.launch {
            snapshotFlow { foreground && store.bool(K.PHONE) }.distinctUntilChanged().collect { on ->
                if (on) remote.start() else remote.stop()
            }
        }
        // Filter lists refresh in the background (first run, then daily) — but not during start-up,
        // when download + recompile would compete with the first frames.
        scope.launch {
            kotlinx.coroutines.delay(20_000)
            if (ads.needsUpdate()) ads.update()
        }
        // A daily "is there a newer Luna?" check, a little after the filter lists.
        scope.launch {
            kotlinx.coroutines.delay(45_000)
            if (updateDue()) checkUpdate()
        }
        // Load the WebView (Chromium) provider off the UI thread, so opening the first page later
        // doesn't freeze the home screen. getDefaultUserAgent() is documented as thread-safe.
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            Thread.sleep(1500)
            runCatching { android.webkit.WebSettings.getDefaultUserAgent(activity) }
        }.start()
    }
    private var backLong = false
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    fun toast(msg: String) {
        toastMsg = msg
    }

    fun toast(@StringRes id: Int, vararg args: Any) = toast(s(id, *args))

    // ---- Language ----
    /** What the viewer chose in Settings: "auto" (follow the TV) or a code from [com.luna.browser.data.Languages]. */
    val languageSetting: String get() = store.str(K.LANGUAGE)

    private val systemLocale: Locale get() = activity.resources.configuration.locales[0]

    /** The locale the UI is shown in (observable through the language setting). */
    val locale: Locale
        get() = languageSetting.let { if (it == "auto") systemLocale else Locale.forLanguageTag(it) }

    /** Two-letter language code driving regional defaults (quick sites, search engines, filter lists). */
    val lang: String get() = locale.language

    private var ctxTag = ""
    private var ctxCache: Context = activity

    /**
     * A context whose resources follow [locale]. Used by every string lookup, so changing the language
     * in Settings takes effect immediately, without restarting the activity.
     */
    val ctx: Context
        get() {
            val tag = languageSetting
            if (tag != ctxTag) {
                ctxCache = if (tag == "auto") activity else activity.createConfigurationContext(
                    Configuration(activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
                )
                ctxTag = tag
            }
            return ctxCache
        }

    fun s(@StringRes id: Int, vararg args: Any): String = ctx.getString(id, *args)

    fun plural(@PluralsRes id: Int, n: Int): String = ctx.resources.getQuantityString(id, n, n)

    // ---- Navigation ----
    fun navigate(to: Screen) {
        if (to == screen) return
        stack.addLast(screen)
        show(to)
    }

    fun goHome() {
        stack.clear()
        show(Screen.Home)
    }

    private fun show(to: Screen) {
        // The controller that was shown is the one to leave, even if the current tab changed meanwhile.
        if (screen == Screen.Browser) { shownBrowser?.onLeave(); shownBrowser = null }
        screen = to
        if (to == Screen.Browser) { val b = browser; shownBrowser = b; b.onEnter() }
    }

    /** The tab switcher; from a page it first takes a fresh thumbnail of that page (a moment, never more than half a second). */
    fun openTabs() {
        val b = if (screen == Screen.Browser) shownBrowser else null
        if (b == null) { navigate(Screen.Tabs); return }
        b.captureTabThumb { if (screen == Screen.Browser) navigate(Screen.Tabs) }
    }

    /** Opens a URL or search query in the browser. */
    fun openUrl(input: String) {
        if (locked) return
        val url = store.resolve(input)
        if (url.isEmpty()) return
        // A site typed without a scheme may need "www." or plain http, or turn out not to be a site at all (then it is searched for).
        val typed = if (com.luna.browser.data.AddressInput.classify(input) == com.luna.browser.data.AddressInput.Kind.Bare) input.trim() else null
        browser.load(url, typed)
        if (screen != Screen.Browser) navigate(Screen.Browser)
    }

    /** Goes back to the tab that already shows [url], or opens it in the current tab. */
    fun resume(url: String) {
        val i = tabs.indexOfUrl(url)
        if (i >= 0) openTab(i) else openUrl(url)
    }

    /** Shows tab [i]; an empty tab has nothing to show, so it opens Home to pick something. */
    fun openTab(i: Int) {
        tabs.select(i)
        stack.clear()
        if (tabs.current.isEmpty) { show(Screen.Home); return }
        stack.addLast(Screen.Home)   // Back from a tab goes Home, as for any page opened from there
        show(Screen.Browser)
    }

    /** A new, empty tab; Home opens so something can be chosen for it. */
    fun newTab() {
        if (!tabs.add()) { toast(R.string.toast_tab_limit, TabPolicy.MAX_TABS); return }
        stack.clear()
        show(Screen.Home)
    }

    /** A new, empty private tab (no history, resume positions, thumbnails or saved tab; its own sign-ins); Home opens to pick something. */
    fun newPrivateTab() {
        if (!tabs.add(isPrivate = true)) { toast(R.string.toast_tab_limit, TabPolicy.MAX_TABS); return }
        stack.clear()
        show(Screen.Home)
        if (!com.luna.browser.browser.WebProfiles.supported()) toast(R.string.priv_shared)
    }

    fun closeTab(i: Int) {
        val wasPrivate = tabs.tabs.getOrNull(i)?.isPrivate == true
        tabs.close(i)
        val ended = wasPrivate && tabs.tabs.none { it.isPrivate }
        if (tabs.count == 1 && tabs.current.isEmpty) goHome()
        if (ended) toast(R.string.priv_cleared) else if (!(tabs.count == 1 && tabs.current.isEmpty)) toast(R.string.toast_tab_closed)
    }

    fun closeOtherTabs(keep: Int) {
        tabs.closeOthers(keep)
        toast(R.string.toast_tab_closed)
    }

    fun openAddress(initial: String = "") {
        addressInitial = initial
        addressOpen = true
    }

    // ---- Profile PINs ----
    /** True from start-up until the PIN of the remembered profile has been entered: nothing opens meanwhile. */
    var locked by mutableStateOf(store.hasPin(store.profileId))
        private set

    /** Checks [pin] for profile [id]: null when right, otherwise the message to show (wrong, or how long to wait). */
    fun tryPin(id: String, pin: String): String? {
        val now = System.currentTimeMillis()
        val until = store.pinLockedUntil()
        // A clock set far back can't extend the wait beyond the longest one.
        if (now < until && until - now <= 300_000L) return s(R.string.prof_pin_locked, ((until - now + 999) / 1000).toInt())
        if (store.checkPin(id, pin)) { store.setPinFailures(0, 0L); return null }
        val fails = store.pinFailures() + 1
        val wait = com.luna.browser.data.PinRules.lockSeconds(fails)
        store.setPinFailures(fails, if (wait > 0) now + wait * 1000L else 0L)
        return if (wait > 0) s(R.string.prof_pin_locked, wait) else s(R.string.prof_pin_wrong)
    }

    /** The picker's way in: the remembered profile's PIN was right (or it has none). */
    fun unlock() { locked = false }

    /** The name shown for a profile: the default one has no name of its own until it is renamed. */
    fun profileLabel(p: com.luna.browser.data.Profile) = p.name.ifEmpty { s(R.string.prof_main) }

    /**
     * Switches who is watching: the open tabs of the current profile are saved and released, the new profile's history,
     * favourites, positions and tabs are loaded, and Home opens. Leaving the guest erases its data and web sign-ins.
     */
    fun switchProfile(id: String) {
        locked = false
        if (id == store.profileId) { goHome(); return }
        val leavingGuest = store.isGuest
        tabs.destroyAll()
        store.switchProfile(id)
        if (leavingGuest) com.luna.browser.browser.WebProfiles.delete(com.luna.browser.data.ProfileRules.GUEST)
        tabs.reload()
        goHome()
        toast(if (store.isGuest) s(R.string.prof_guest_on) else s(R.string.prof_switched, profileLabel(store.currentProfile)))
    }

    fun deleteProfile(id: String) {
        if (store.deleteProfile(id)) {
            com.luna.browser.browser.WebProfiles.delete(id)
            toast(R.string.prof_deleted)
        }
    }

    fun launch() {
        // Private sign-ins never outlive the app, also after a crash.
        com.luna.browser.browser.WebProfiles.wipePrivate()
        // A profile with a PIN opens behind the picker; "Who's watching?" can also be asked for at every start.
        if (locked) { show(Screen.Profiles); return }
        if (store.profiles.size > 1 && store.bool(K.ASK_PROFILE)) { show(Screen.Profiles); return }
        val last = store.lastSession
        if (store.str(K.LAUNCH) == "resume" && last != null) openUrl(last.url)
    }

    // ---- Keys ----
    fun handleKey(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) return handleBack(e)
        if (screen == Screen.Browser) return browser.handleKey(e)
        return false
    }

    private fun handleBack(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) backLong = false
            else if (!backLong && e.eventTime - e.downTime >= 600) {
                backLong = true
                if (screen == Screen.Browser) browser.onLongBack()
            }
            return true
        }
        if (backLong) {
            backLong = false
            return true
        }
        back()
        return true
    }

    fun back() {
        when {
            closeOverlay?.invoke() == true -> Unit
            addressOpen -> addressOpen = false
            screen == Screen.Browser && browser.onBack() -> Unit
            stack.isNotEmpty() -> show(stack.removeLast())
            locked -> onExit()                       // no way past the PIN picker with Back
            screen != Screen.Home -> show(Screen.Home)
            else -> onExit()
        }
    }

    // ---- Network ----
    fun registerNetwork() {
        val cm = activity.getSystemService(ConnectivityManager::class.java) ?: return
        online = cm.activeNetwork != null
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { online = true } }
            override fun onLost(network: Network) { main.post { online = cm.activeNetwork != null } }
        }
        netCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    fun destroy() {
        remote.stop()
        netCallback?.let { runCatching { activity.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } }
        tabs.destroyAll()
        // A guest session ends with the app: its data and its web sign-ins are erased.
        if (store.isGuest) {
            store.endGuest()
            com.luna.browser.browser.WebProfiles.delete(com.luna.browser.data.ProfileRules.GUEST)
        }
        scope.cancel()
    }
}
