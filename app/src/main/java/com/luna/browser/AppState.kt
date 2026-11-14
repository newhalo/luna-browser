package com.luna.browser

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luna.browser.browser.BrowserController
import com.luna.browser.data.AdBlocker
import com.luna.browser.data.K
import com.luna.browser.data.Store
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

enum class Screen { Home, Browser, Settings, History, Favorites }

/** Single source of truth for navigation, shared services and global key handling. */
class AppState(val activity: Activity) {
    val store = Store(activity)
    val ads = AdBlocker(activity)
    val browser: BrowserController by lazy { BrowserController(activity, this) }

    var screen by mutableStateOf(Screen.Home)
        private set
    private val stack = ArrayDeque<Screen>()

    var addressOpen by mutableStateOf(false)
    var addressInitial = ""
    var toastMsg by mutableStateOf<String?>(null)
    var online by mutableStateOf(true)
    val focusMemory = mutableMapOf<String, String>()
    var onExit: () -> Unit = {}
    var voiceAvailable by mutableStateOf(false)
    var startVoice: () -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    val scope = kotlinx.coroutines.MainScope()

    init {
        // Filter lists refresh in the background (first run, then daily) — but not during start-up,
        // when download + recompile would compete with the first frames.
        scope.launch {
            kotlinx.coroutines.delay(20_000)
            if (ads.needsUpdate()) ads.update()
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
        if (screen == Screen.Browser) browser.onLeave()
        screen = to
        if (to == Screen.Browser) browser.onEnter()
    }

    /** Opens a URL or search query in the browser. */
    fun openUrl(input: String) {
        val url = store.resolve(input)
        if (url.isEmpty()) return
        browser.load(url)
        if (screen != Screen.Browser) navigate(Screen.Browser)
    }

    /** Re-shows the page that is already loaded, or loads it if the browser is empty. */
    fun resume(url: String) {
        if (browser.webView.url == url) navigate(Screen.Browser) else openUrl(url)
    }

    fun openAddress(initial: String = "") {
        addressInitial = initial
        addressOpen = true
    }

    fun launch() {
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
            addressOpen -> addressOpen = false
            screen == Screen.Browser && browser.onBack() -> Unit
            stack.isNotEmpty() -> show(stack.removeLast())
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
        netCallback?.let { runCatching { activity.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } }
        browser.destroy()
        scope.cancel()
    }
}
