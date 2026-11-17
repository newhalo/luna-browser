package com.luna.browser.browser

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.ViewConfiguration
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.luna.browser.AppState
import com.luna.browser.R
import com.luna.browser.data.AdBlocker
import com.luna.browser.data.K
import com.luna.browser.data.ResumeRules
import com.luna.browser.data.Site
import com.luna.browser.data.hostOf
import com.luna.browser.data.youtubeThumb
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import com.luna.browser.data.filter.Type
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min

enum class ErrorKind { Offline, NotFound, Timeout, Ssl, Other }

/** A page-load error. Texts are resource ids so they follow the language chosen at display time. */
data class PageError(val kind: ErrorKind, @StringRes val title: Int, @StringRes val message: Int, val sslHost: String? = null)

/** Sites the viewer chose to open despite a certificate problem. Memory only: gone when Luna closes. */
object SslExceptions {
    private val hosts: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    fun allow(host: String) { if (host.isNotEmpty()) hosts.add(host.lowercase()) }
    fun allowed(host: String) = host.isNotEmpty() && host.lowercase() in hosts
}

data class VideoState(
    val paused: Boolean,
    val position: Double,
    val duration: Double,
    val buffering: Boolean,
    val failed: Boolean,
)

data class Osd(val label: String, val icon: ImageVector?, val id: Long)

/**
 * Owns the WebView and everything around it: loading state, error mapping, ad blocking,
 * the virtual cursor, and remote-control handling for pages and fullscreen video.
 */
class BrowserController(private val activity: Activity, private val app: AppState, val isPrivate: Boolean = false) {
    // Each profile has its own cookies and site data (where the web engine can separate them): set before anything loads.
    // A private tab uses the shared private profile instead and leaves no trace in the history, resume or thumbnails.
    private val webProfile = if (isPrivate) WebProfiles.PRIVATE else app.store.profileId
    val webView = WebView(activity).also { WebProfiles.apply(it, webProfile) }
    private val cookies = WebProfiles.cookies(webProfile)
    private val handler = Handler(Looper.getMainLooper())
    private val density = activity.resources.displayMetrics.density

    // ---- Page state (observed by Compose) ----
    var url by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var loading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var error by mutableStateOf<PageError?>(null)
        private set

    // ---- Cursor ----
    var cursorMode by mutableStateOf(app.store.bool(K.CURSOR_START))
        private set
    var cursorX by mutableFloatStateOf(0f)
        private set
    var cursorY by mutableFloatStateOf(0f)
        private set
    var cursorActive by mutableStateOf(true)
        private set
    var clickPulse by mutableIntStateOf(0)
        private set
    private var viewW = 0
    private var viewH = 0
    private val held = mutableSetOf<Int>()
    private var heldSince = 0L
    private var lastTick = 0L
    private var lastMove = 0L
    private var wheelRemainder = 0f
    private var okLong = false

    // ---- Overlays ----
    var toolbarVisible by mutableStateOf(false)
        private set
    var toolbarTouch by mutableLongStateOf(0L)
        private set
    var customView by mutableStateOf<View?>(null)
        private set
    var video by mutableStateOf<VideoState?>(null)
        private set
    var controlsVisible by mutableStateOf(false)
        private set
    var osd by mutableStateOf<Osd?>(null)
        private set
    /** True for a moment while the page is screenshotted, so the pointer stays out of thumbnails. */
    var capturing by mutableStateOf(false)
        private set
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var lastControls = 0L

    private val baseUa: String
    private val desktopUa: String
    private var uaChanged = false

    init {
        val def = WebSettings.getDefaultUserAgent(activity)
        val chrome = Regex("Chrome/[\\d.]+").find(def)?.value ?: "Chrome/124.0.0.0"
        baseUa = def.replace("; wv", "").replace(Regex("Version/[\\d.]+ "), "").replace(" Mobile", "")
        desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"

        // Debug builds only: lets Chrome DevTools on a PC (over adb) inspect pages while testing on the TV.
        if ((activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        cookies.setAcceptCookie(true)
        webView.apply {
            // Pages assume a white canvas; a dark one makes dark text unreadable (seen on 24h.com.vn).
            setBackgroundColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportMultipleWindows(true)
                builtInZoomControls = false
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            setDownloadListener { _, _, _, _, _ -> app.toast(R.string.toast_no_downloads) }
        }
        applySettings()
        uaChanged = false
    }

    // ---- Lifecycle ----
    fun applySettings() {
        val s = webView.settings
        val ref = webView.url ?: url
        val ua = uaFor(ref)
        if (s.userAgentString != ua) {
            s.userAgentString = ua
            uaChanged = true
        }
        s.textZoom = if (ref.startsWith("http")) app.store.zoomFor(ref) else app.store.int(K.ZOOM)
        s.javaScriptCanOpenWindowsAutomatically = !app.store.bool(K.BLOCK_POPUPS)
        cookies.setAcceptThirdPartyCookies(webView, app.store.bool(K.THIRD_PARTY))
    }

    // ---- Per-site settings ----
    private var lastSiteKey = ""
    private var uaFixedFor = ""

    private fun uaFor(target: String) =
        if (if (target.startsWith("http")) app.store.desktopFor(target) else app.store.bool(K.DESKTOP)) desktopUa else baseUa

    /** Whether the blocker is on for the page being shown (a site can switch it off for itself). */
    private fun adblockOn(): Boolean {
        val page = filterPageUrl.ifEmpty { url }
        return if (page.startsWith("http")) app.store.adblockFor(page) else app.store.bool(K.ADBLOCK)
    }

    /**
     * Applies the settings of the site about to be opened BEFORE the request goes out (the user agent
     * has to be right for the very first request). A control-mode choice for a site is applied when
     * entering that site; sites without one keep whatever mode the viewer is in.
     */
    private fun prepareFor(target: String) {
        if (!target.startsWith("http")) return
        val st = app.store
        webView.settings.userAgentString = uaFor(target)
        webView.settings.textZoom = st.zoomFor(target)
        val key = st.siteKey(target)
        if (key != lastSiteKey) {
            lastSiteKey = key
            st.pointerFor(target)?.let { if (cursorMode != it) { cursorMode = it; held.clear() } }
        }
    }

    // The per-site panel (Compose): while it is open the keys belong to it.
    var sitePanel by mutableStateOf(false)
        private set
    private var siteNeedsReload = false

    fun showSitePanel() { toolbarVisible = false; siteNeedsReload = false; sitePanel = true }

    /** Something that needs a reload (user agent, ad blocking) was changed in the panel. */
    fun siteChangeNeedsReload() { siteNeedsReload = true }

    fun hideSitePanel() {
        sitePanel = false
        siteSettingsChanged(reload = siteNeedsReload)
        siteNeedsReload = false
        webView.requestFocus()
    }

    /** Re-applies the settings of the current site (after a change in the panel or in Settings). */
    fun siteSettingsChanged(reload: Boolean) {
        val ref = webView.url ?: url
        if (!ref.startsWith("http")) return
        val st = app.store
        webView.settings.userAgentString = uaFor(ref)
        webView.settings.textZoom = st.zoomFor(ref)
        st.pointerFor(ref)?.let { if (cursorMode != it) { cursorMode = it; held.clear() } }
        if (reload) reload()
    }

    /** True while the browser screen is showing; the inline video watcher only runs then. */
    private var active = false

    fun onEnter() {
        active = true
        startInlineWatch()
        applySettings()
        if (uaChanged && webView.url != null) webView.reload()
        uaChanged = false
        webView.onResume()
        toolbarVisible = false
        webView.requestFocus()
        scheduleIdleHide()
        if (!app.store.bool(K.TIP_SHOWN)) {
            app.store.put(K.TIP_SHOWN, true)
            app.toast(R.string.toast_tip)
        }
    }

    fun onLeave() {
        active = false
        handler.removeCallbacks(inlineWatch)
        // Remember where the video was left before the page is paused.
        if (customView == null) {
            webView.evaluateJavascript(videoJs("", "state", 0)) { r ->
                parse(r)?.let { saveProgress(it, flush = true) }
            }
        }
        held.clear()
        if (speaking) stopSpeaking(keepPlace = true)
        webView.onPause()
    }

    fun destroy() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        active = false
        handler.removeCallbacksAndMessages(null)
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        if (isPrivate) runCatching { webView.clearCache(true); webView.clearHistory(); webView.clearFormData() }
        webView.destroy()
    }

    // ---- Navigation actions ----
    /**
     * Opens [target]. [typed] is what the viewer typed when it was a bare site name: then, if the site cannot be reached, Luna also
     * tries `www.` and plain http, and as a last resort searches for the text.
     */
    fun load(target: String, typed: String? = null) {
        fallbacks.clear()
        fallbacks.addAll(WwwFallback.candidates(target, bare = typed != null))
        searchFallback = typed
        loadInternal(target)
    }

    private fun loadInternal(target: String) {
        prepareFor(target)
        error = null
        url = target
        filterPageUrl = target
        webView.loadUrl(target)
    }

    /** Addresses still to try if the page just requested cannot be reached (see [load]). */
    private val fallbacks = ArrayDeque<String>()
    private var searchFallback: String? = null

    /** Opens the page the viewer was warned about, for this session only. */
    fun proceedAnyway(host: String) {
        SslExceptions.allow(host)
        error = null
        if (url.isNotEmpty()) webView.loadUrl(url) else webView.reload()
    }

    fun reload() {
        error = null
        webView.reload()
    }

    fun retry() {
        val target = error?.let { url } ?: url
        error = null
        if (webView.url != null && webView.url != "about:blank") webView.reload() else if (target.isNotEmpty()) webView.loadUrl(target)
    }

    fun goBack() { if (webView.canGoBack()) webView.goBack() }
    fun goForward() { if (webView.canGoForward()) webView.goForward() }

    fun toggleFavorite() {
        if (url.isEmpty()) return
        val added = app.store.toggleFavorite(Site(title.ifBlank { url }, url))
        app.toast(if (added) R.string.toast_fav_added else R.string.toast_fav_removed)
    }

    fun showToolbar() {
        if (customView != null) return
        toolbarVisible = true
        touchToolbar()
    }

    fun hideToolbar() {
        toolbarVisible = false
        webView.requestFocus()
    }

    fun touchToolbar() { toolbarTouch = SystemClock.uptimeMillis() }

    // ---- Reading mode ----
    /** True while the clean reading view of the page is shown (it replaces the page; Back leaves it). */
    var reader by mutableStateOf(false)
        private set
    private var readerPending = false
    private var readerOrigin = ""
    private var article: Article? = null

    /** The reading page's text size (an index into [ReaderMode.sizes]); remembered for every article. */
    var readerSize by mutableIntStateOf(ReaderMode.clampSize(app.store.int(K.READER_SIZE)))
        private set

    /** True while the article is being read aloud. */
    var speaking by mutableStateOf(false)
        private set

    /** Bumped on every key in the reading page: the on-screen hints show for a few seconds after it. */
    var readerHudAt by mutableLongStateOf(0L)
        private set
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var resumeAt = -1       // block to continue from after a pause
    private var spokenNow = -1
    private var lastUtterance = ""

    fun toggleReader() {
        if (reader) exitReader() else enterReader()
    }

    private fun enterReader() {
        if (!url.startsWith("http") || error != null || customView != null) { app.toast(R.string.reader_none); return }
        hideToolbar()
        webView.evaluateJavascript(ReaderMode.EXTRACT_JS) { raw ->
            val article = ReaderMode.parse(raw)
            if (article == null) { app.toast(R.string.reader_none); return@evaluateJavascript }
            readerOrigin = url
            readerPending = true
            reader = true
            this.article = article
            resumeAt = -1
            held.clear()
            // No base address: the reading page gets an opaque origin of its own, never the site's (it has no scripts
            // anyway). The history entry keeps the article's address, so Back and the history behave.
            webView.loadDataWithBaseURL(
                null,
                ReaderMode.html(article, app.s(R.string.reader_minutes, ReaderMode.minutes(article)), readerSize),
                "text/html", "utf-8", readerOrigin,
            )
            readerHudAt = SystemClock.uptimeMillis()
        }
    }

    private fun exitReader() {
        stopSpeaking(keepPlace = false)
        article = null
        reader = false
        readerPending = false
        error = null
        // The reading page is one extra entry in the page history: step back over it (the page is shown again as it was).
        if (webView.canGoBack()) webView.goBack()
        else if (readerOrigin.startsWith("http")) webView.loadUrl(readerOrigin)
    }

    /** The reading page is a data: document; the address shown, saved and filtered stays that of the article. */
    private fun pageAddress(u: String) { if (!u.startsWith("data:")) { url = u; filterPageUrl = u } }

    /** Up / Down scroll by a third of the screen (hold to keep going); Up at the very top opens the toolbar. Left / Right change the text size. */
    private fun readerKey(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return true
        readerHudAt = SystemClock.uptimeMillis()
        val step = (viewH * 0.34f).toInt().coerceAtLeast((120 * density).toInt())
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> webView.scrollBy(0, step)
            KeyEvent.KEYCODE_DPAD_UP -> if (webView.canScrollVertically(-1)) webView.scrollBy(0, -step) else if (e.repeatCount == 0) showToolbar()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (e.repeatCount == 0) changeReaderSize(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (e.repeatCount == 0) changeReaderSize(+1)
        }
        return true
    }

    private fun changeReaderSize(delta: Int) {
        val next = ReaderMode.clampSize(readerSize + delta)
        if (next == readerSize) { app.toast(app.s(R.string.reader_size, ReaderMode.sizePercent(next))); return }
        readerSize = next
        app.store.put(K.READER_SIZE, next)
        webView.evaluateJavascript(ReaderMode.setSizeJs(next), null)
        app.toast(app.s(R.string.reader_size, ReaderMode.sizePercent(next)))
    }

    // ---- Read aloud ----
    /** OK in the reading page: start reading from the top (or from where it was paused), or pause. */
    fun toggleSpeak() {
        readerHudAt = SystemClock.uptimeMillis()
        if (!reader || article == null) return
        if (speaking) { stopSpeaking(keepPlace = true); return }
        startSpeaking()
    }

    private fun startSpeaking() {
        val engine = tts
        if (engine == null) {
            // The engine starts up in the background the first time (a moment on a TV): speak as soon as it is ready.
            app.toast(R.string.reader_tts_starting)
            tts = TextToSpeech(activity) { status ->
                handler.post {
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) { tts?.setOnUtteranceProgressListener(ttsListener); if (reader) speakFrom() }
                    else { tts?.shutdown(); tts = null; app.toast(R.string.reader_no_tts) }
                }
            }
            return
        }
        if (!ttsReady) return
        speakFrom()
    }

    private fun speakFrom() {
        val a = article ?: return
        val engine = tts ?: return
        val loc = runCatching { java.util.Locale.forLanguageTag(a.lang.replace('_', '-')) }.getOrNull()
        val use = if (loc != null && a.lang.isNotEmpty() && engine.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) loc else app.locale
        if (engine.isLanguageAvailable(use) >= TextToSpeech.LANG_AVAILABLE) engine.language = use
        val items = ReaderMode.speakable(a).filter { it.first >= maxOf(resumeAt, 0) }
        if (items.isEmpty()) { resumeAt = -1; return }
        engine.stop()
        var n = 0
        val all = items.flatMap { (i, t) -> ReaderMode.chunks(t).map { i to it } }
        all.forEachIndexed { k, (i, chunk) ->
            val id = "$i:$k" + if (k == all.lastIndex) ":end" else ""
            if (k == all.lastIndex) lastUtterance = id
            engine.speak(chunk, if (n++ == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        speaking = true
    }

    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val i = utteranceId?.substringBefore(':')?.toIntOrNull() ?: return
            handler.post { if (reader) { spokenNow = i; webView.evaluateJavascript(ReaderMode.highlightJs(i), null) } }
        }
        override fun onDone(utteranceId: String?) {
            if (utteranceId == lastUtterance) handler.post { speaking = false; resumeAt = -1; spokenNow = -1; if (reader) webView.evaluateJavascript(ReaderMode.CLEAR_HIGHLIGHT_JS, null) }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { handler.post { speaking = false } }
    }

    /** Stops speaking; with [keepPlace] the next OK carries on from the paragraph being read. */
    private fun stopSpeaking(keepPlace: Boolean) {
        runCatching { tts?.stop() }
        if (speaking && keepPlace) resumeAt = spokenNow.coerceAtLeast(0) else resumeAt = -1
        speaking = false
        if (!keepPlace && reader) webView.evaluateJavascript(ReaderMode.CLEAR_HIGHLIGHT_JS, null)
    }

    fun toggleMode() {
        cursorMode = !cursorMode
        held.clear()
        webView.requestFocus()
        app.toast(if (cursorMode) R.string.toast_mode_pointer else R.string.toast_mode_links)
    }

    /** Back handling inside the browser. Returns true when consumed. */
    fun onBack(): Boolean {
        if (customView != null) {
            if (controlsVisible) controlsVisible = false else exitFullscreen()
            return true
        }
        if (sitePanel) { hideSitePanel(); return true }
        if (toolbarVisible) { hideToolbar(); return true }
        if (reader) { exitReader(); return true }
        if (webView.canGoBack()) { webView.goBack(); return true }
        return false
    }

    fun onLongBack() {
        if (customView != null) exitFullscreen()
        showToolbar()
    }

    fun onViewSize(w: Int, h: Int) {
        val first = viewW == 0
        viewW = w; viewH = h
        if (first || cursorX > w || cursorY > h) {
            cursorX = w / 2f; cursorY = h / 2f
        }
    }

    // ---- WebViewClient / ChromeClient ----
    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme ?: return false
            if (scheme != "http" && scheme != "https" && scheme != "about" && scheme != "file" && scheme != "data" && scheme != "blob") return true
            if (!request.isForMainFrame) return false
            val target = request.url.toString()
            // Click-hijack: the page swaps itself for a known ad / betting page.
            if (adblockOn() && url.startsWith("http") && app.ads.shouldBlock(target, url, Type.DOCUMENT)) {
                notePopupBlocked(R.string.toast_blocked_redirect)
                return true
            }
            if (app.store.bool(K.BLOCK_POPUPS)) {
                // A page that sends itself to another site on its own, without the viewer pressing anything.
                if (!request.hasGesture() && !request.isRedirect && url.startsWith("http") &&
                    !PopupGuard.sameSite(target, url) && !PopupGuard.isAuth(target) &&
                    SystemClock.uptimeMillis() - pageStartedAt > 2500
                ) {
                    notePopupBlocked(R.string.toast_blocked_auto)
                    return true
                }
            }
            prepareFor(target)
            return false
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (adblockOn() && !request.isForMainFrame &&
                app.ads.shouldBlock(request.url.toString(), filterPageUrl, AdBlocker.typeOf(request))
            ) {
                return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
            pageStartedAt = SystemClock.uptimeMillis()
            // The reading view is a page of its own: any other navigation (a link, Back) ends it.
            if (readerPending) readerPending = false else if (reader) reader = false
            resumeTried = null   // a new document: the saved position may apply again
            pageUrl?.let { u ->
                val want = uaFor(u)
                if (u.startsWith("http") && view.settings.userAgentString != want) {
                    view.settings.userAgentString = want
                    // Reached without passing through load() / a link (e.g. a redirect): restart once with the right agent.
                    if (uaFixedFor != u) { uaFixedFor = u; handler.post { view.reload() } }
                }
                if (u.startsWith("http")) view.settings.textZoom = app.store.zoomFor(u)
            }
            injectFallbackScripts(view)
            error = null
            loading = true
            progress = 0.05f
            pageUrl?.let { pageAddress(it) }
            injectCosmetics(view)
            syncNav()
            scheduleIdleHide()
        }

        override fun onPageFinished(view: WebView, pageUrl: String?) {
            loading = false
            progress = 1f
            if (error == null) { fallbacks.clear(); searchFallback = null }   // reached: later failures are another page's
            pageUrl?.let { if (!it.startsWith("data:")) url = it }
            title = view.title ?: ""
            syncNav()
            if (error == null && pageUrl != null && !pageUrl.startsWith("data:")) onVisited(pageUrl, title)
            injectCosmetics(view)
            if (app.store.bool(K.BLOCK_POPUPS)) view.evaluateJavascript(PopupGuard.OVERLAY_JS, null)
            if (!docStartScript) view.evaluateJavascript(PopupGuard.VIDEO_BRIDGE_JS, null)
        }

        override fun doUpdateVisitedHistory(view: WebView, pageUrl: String?, isReload: Boolean) {
            pageUrl?.let { pageAddress(it) }
            syncNav()
            // Single-page sites (YouTube) change videos without a new page load.
            if (!isReload && !loading && error == null && pageUrl != null && !pageUrl.startsWith("data:")) onVisited(pageUrl, "")
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
            if (!request.isForMainFrame) return
            val desc = err.description?.toString() ?: ""
            if (desc.contains("ERR_ABORTED") || desc.contains("BLOCKED_BY_CLIENT")) return
            // A site that does not answer (no address, no certificate, no connection) often works with "www." or plain http;
            // text that is not a site at all ends up as a search.
            val connectFailure = err.errorCode in setOf(
                WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT,
                WebViewClient.ERROR_IO, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
            )
            if (connectFailure) {
                fallbacks.removeFirstOrNull()?.let { alt -> handler.post { loadInternal(alt) }; return }
                if (err.errorCode == WebViewClient.ERROR_HOST_LOOKUP) {
                    searchFallback?.let { q -> searchFallback = null; handler.post { loadInternal(app.store.searchUrl(q)) }; return }
                }
            }
            loading = false
            error = mapError(err.errorCode)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, err: SslError) {
            val host = runCatching { android.net.Uri.parse(err.url).host }.getOrNull().orEmpty()
            if (SslExceptions.allowed(host)) { handler.proceed(); return }
            handler.cancel()
            loading = false
            error = PageError(ErrorKind.Ssl, R.string.err_ssl_t, R.string.err_ssl_blocked_m, sslHost = host.ifEmpty { null })
        }
    }

    /** Addresses already retried with "www." (once each, so a failing www page is not retried again). */
    private val wwwTried = HashSet<String>()

    private fun mapError(code: Int): PageError {
        val cm = activity.getSystemService(ConnectivityManager::class.java)
        if (cm?.activeNetwork == null) {
            return PageError(ErrorKind.Offline, R.string.err_offline_t, R.string.err_offline_m)
        }
        return when (code) {
            WebViewClient.ERROR_HOST_LOOKUP ->
                PageError(ErrorKind.NotFound, R.string.err_notfound_t, R.string.err_notfound_m)
            WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO ->
                PageError(ErrorKind.Timeout, R.string.err_timeout_t, R.string.err_timeout_m)
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                PageError(ErrorKind.Ssl, R.string.err_ssl_t, R.string.err_ssl_failed_m)
            else ->
                PageError(ErrorKind.Other, R.string.err_other_t, R.string.err_other_m)
        }
    }

    private fun syncNav() {
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
    }

    private val chrome = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress / 100f
            if (newProgress < 100) loading = true
        }

        override fun onReceivedTitle(view: WebView, t: String?) {
            title = t ?: ""
            if (!isPrivate) view.url?.let { app.store.updateTitle(it, title) }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            val u = view.url ?: return
            if (isPrivate || icon == null || icon.width < 32) return   // a saved icon would name the site on disk
            val f = File(app.store.iconsDir, hostOf(u) + ".png")
            val host = hostOf(u)
            Thread {
                runCatching { f.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    .onSuccess { handler.post { app.store.iconSaved(host) } }
            }.start()
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customCb?.onCustomViewHidden()
            view.keepScreenOn = true
            customCb = callback
            toolbarVisible = false
            customView = view
            video = null
            showControls()
            startPolling()
            // Going fullscreen means "watch": some players pause on the tap that opened fullscreen.
            handler.postDelayed({ if (customView != null) togglePlay(true) }, 600)
        }

        override fun onHideCustomView() {
            customView?.keepScreenOn = false
            // Remember where the video was left before its state is dropped.
            video?.let { saveProgress(it, flush = true) }
            resumeTried = null
            customView = null
            if (active) startInlineWatch()
            customCb = null
            controlsVisible = false
            video = null
            handler.removeCallbacks(poll)
            webView.requestFocus()
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) {
                if (app.store.bool(K.BLOCK_POPUPS)) notePopupBlocked(R.string.toast_blocked_popup)
                return false
            }
            val temp = WebView(activity)
            temp.webViewClient = object : WebViewClient() {
                private var done = false
                private fun redirect(v: WebView, target: String?) {
                    if (done || target == null || target == "about:blank") return
                    done = true
                    v.stopLoading()
                    handler.post { v.destroy() }
                    // There are no tabs on a TV: only same-site windows (or sign-in pages) may replace the page.
                    val listed = adblockOn() && app.ads.blocksPopup(target, url)
                    if (!listed && (!app.store.bool(K.BLOCK_POPUPS) || PopupGuard.allowPopup(target, url))) load(target)
                    else notePopupBlocked(R.string.toast_blocked_adwindow)
                }
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    redirect(v, request.url.toString()); return true
                }
                override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) = redirect(v, u)
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = temp
            resultMsg.sendToTarget()
            return true
        }
    }

    // ---- Ad filtering ----
    /** Page URL for filter decisions; read from WebView's network threads. */
    @Volatile
    private var filterPageUrl = ""

    /** Hides ad elements with the filter lists' cosmetic rules for this page. */
    private fun injectCosmetics(view: WebView) {
        if (!adblockOn()) return
        val page = filterPageUrl.ifEmpty { return }
        val css = app.ads.cosmeticCss(page)
        if (css.isEmpty()) return
        view.evaluateJavascript(
            "(function(c){var s=document.getElementById('__luna_cos');if(!s){s=document.createElement('style');s.id='__luna_cos';" +
                "(document.head||document.documentElement).appendChild(s);}s.textContent=c;})(${JSONObject.quote(css)})",
            null,
        )
    }

    // ---- Pop-up guard ----
    private var pageStartedAt = 0L
    private var docStartScript = false

    /**
     * Old WebViews (before the document-start script API, roughly Chrome 76) can't inject into every frame
     * early. Fall back to injecting into the top page as soon as it starts and again when it finishes:
     * pop-up and overlay protection still work, but a player inside a cross-origin iframe can't be bridged.
     */
    private fun injectFallbackScripts(view: WebView) {
        if (docStartScript) return
        if (app.store.bool(K.BLOCK_POPUPS)) view.evaluateJavascript(PopupGuard.OVERLAY_JS, null)
        view.evaluateJavascript(PopupGuard.VIDEO_BRIDGE_JS, null)
    }
    private var lastPopupToast = 0L

    private fun notePopupBlocked(@StringRes msg: Int) {
        app.ads.countBlocked()
        val now = SystemClock.uptimeMillis()
        if (now - lastPopupToast > 4000) {
            lastPopupToast = now
            app.toast(msg)
        }
    }

    // ---- Thumbnails ----
    private var artUrl = ""
    private val artTask = Runnable { captureArt(artUrl) }

    private fun onVisited(pageUrl: String, pageTitle: String) {
        app.tabs.saveSoon()
        if (isPrivate) return
        app.store.recordVisit(pageTitle, pageUrl)
        artUrl = pageUrl
        handler.removeCallbacks(artTask)
        handler.postDelayed(artTask, 3500)
    }

    private fun captureArt(u: String) {
        if (webView.url != u || error != null) return
        if (youtubeThumb(u) == null) {
            webView.evaluateJavascript(OG_JS) { r ->
                val img = runCatching { JSONArray("[$r]").optString(0) }.getOrNull()
                if (img != null && (img.startsWith("https://") || img.startsWith("http://"))) app.store.updateArt(u, image = img)
            }
        }
        snapshot(u)
    }

    /** Screenshots the visible page (GPU content included) into a small JPEG for home-screen cards. */
    private fun snapshot(u: String) {
        if (isPrivate) return
        if (customView != null || toolbarVisible || webView.width == 0 || !webView.isAttachedToWindow) return
        capturing = true
        handler.postDelayed({
            val loc = IntArray(2)
            webView.getLocationInWindow(loc)
            val w = 480
            val h = (w.toFloat() * webView.height / webView.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(
                    activity.window, Rect(loc[0], loc[1], loc[0] + webView.width, loc[1] + webView.height), bmp,
                    { res ->
                        capturing = false
                        if (res == PixelCopy.SUCCESS) saveSnap(u, bmp)
                    }, handler,
                )
            } catch (e: Exception) {
                capturing = false
            }
        }, 80)
    }

    /**
     * A fresh screenshot of the page for the tab switcher, then [done] (always called, at most about half a second later).
     * The toolbar is hidden first so it is not in the picture.
     */
    fun captureTabThumb(done: () -> Unit) {
        val u = url
        var called = false
        fun finish() { if (!called) { called = true; done() } }
        if (isPrivate || !u.startsWith("http") || error != null || customView != null || webView.width == 0 || !webView.isAttachedToWindow || reader) { finish(); return }
        val wasToolbar = toolbarVisible
        toolbarVisible = false
        capturing = true
        handler.postDelayed({ finish() }, 600)        // never keep the viewer waiting
        handler.postDelayed({
            val loc = IntArray(2)
            webView.getLocationInWindow(loc)
            val w = 480
            val h = (w.toFloat() * webView.height / webView.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(
                    activity.window, Rect(loc[0], loc[1], loc[0] + webView.width, loc[1] + webView.height), bmp,
                    { res ->
                        capturing = false
                        if (res == PixelCopy.SUCCESS) {
                            val f = app.store.tabThumbFile(u)
                            Thread {
                                runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                                handler.post { app.store.tabThumbSaved(); finish() }
                            }.start()
                        } else finish()
                    }, handler,
                )
            } catch (e: Exception) {
                capturing = false
                finish()
            }
        }, if (wasToolbar) 320L else 60L)
    }

    private fun saveSnap(u: String, bmp: Bitmap) {
        val f = File(app.store.thumbsDir, "${u.hashCode().toUInt()}.jpg")
        Thread {
            runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
                .onSuccess { handler.post { app.store.updateArt(u, snap = f.path) } }
        }.start()
    }

    // ---- Key handling ----
    fun handleKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        val down = e.action == KeyEvent.ACTION_DOWN
        // Remotes differ: many (Google TV, Chromecast) have no Menu key, so Info and Bookmark open the toolbar too.
        // Long Back and "Up at the very top of the page" always work.
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO || code == KeyEvent.KEYCODE_BOOKMARK) {
            if (!down) { if (sitePanel) hideSitePanel() else if (toolbarVisible) hideToolbar() else showToolbar() }
            return true
        }
        // Red coloured key (present on many TV remotes): switch pointer / link mode without holding OK.
        if (code == KeyEvent.KEYCODE_PROG_RED && customView == null && !toolbarVisible && error == null) {
            if (!down) toggleMode()
            return true
        }
        if (customView != null) return handleVideoKey(e)
        // Toolbar and error panels are Compose: let the focus system drive them.
        if (toolbarVisible || sitePanel || error != null) return false
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down && e.repeatCount == 0) { if (reader) toggleSpeak() else mediaKey(code) }
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (down) webView.pageDown(false); return true }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (down) webView.pageUp(false); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> return if (reader) { if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) toggleSpeak(); true } else handleOk(e)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                return if (reader) readerKey(e) else if (cursorMode) handleCursorKey(e) else focusModeKey(e)
        }
        return false
    }

    private fun handleOk(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) okLong = false
            else if (!okLong && e.eventTime - e.downTime >= 600) { okLong = true; toggleMode() }
            return true
        }
        if (!okLong) {
            if (cursorMode) clickAtCursor() else {
                webView.requestFocus()
                webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            }
        }
        okLong = false
        return true
    }

    private fun focusModeKey(e: KeyEvent): Boolean {
        webView.requestFocus()
        val handled = webView.dispatchKeyEvent(e)
        if (!handled && e.action == KeyEvent.ACTION_DOWN) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> webView.scrollBy(0, (160 * density).toInt())
                KeyEvent.KEYCODE_DPAD_UP -> if (webView.canScrollVertically(-1)) webView.scrollBy(0, -(160 * density).toInt()) else if (e.repeatCount == 0) showToolbar()
            }
        }
        return true
    }

    // ---- Virtual cursor ----
    private val ticker = object : Runnable {
        override fun run() {
            if (held.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTick).coerceIn(1, 50) / 1000f
            lastTick = now
            val dx = (if (KeyEvent.KEYCODE_DPAD_RIGHT in held) 1 else 0) - (if (KeyEvent.KEYCODE_DPAD_LEFT in held) 1 else 0)
            val dy = (if (KeyEvent.KEYCODE_DPAD_DOWN in held) 1 else 0) - (if (KeyEvent.KEYCODE_DPAD_UP in held) 1 else 0)
            val accel = 1f + min((now - heldSince) / 1000f * 2f, 3f) // ramps up to 4x over 1.5 s
            val base = (250 + 150 * app.store.int(K.CURSOR_SPEED)) * (max(viewW, 960) / 1920f)
            moveCursor(dx * base * accel * dt, dy * base * accel * dt)
            handler.postDelayed(this, 16)
        }
    }

    private fun handleCursorKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (e.action == KeyEvent.ACTION_UP) {
            if (code == pendingCode) { handler.removeCallbacks(startHold); pendingCode = 0; jumpTo(code, true); return true }
            if (held.remove(code) && held.isEmpty() && freeMoved) { freeMoved = false; snapToNearby() }
            return true
        }
        if (e.repeatCount > 0 || code in held || code == pendingCode) return true
        // Pressing Up once the pointer already sits at the very top reveals the toolbar.
        if (code == KeyEvent.KEYCODE_DPAD_UP && held.isEmpty() && cursorY <= 2f && !webView.canScrollVertically(-1)) {
            showToolbar()
            return true
        }
        if (snapOn) {
            // A short press jumps to the nearest clickable item in that direction; holding moves the pointer freely.
            if (pendingCode != 0) { handler.removeCallbacks(startHold); beginHold() }
            if (held.isEmpty() && pendingCode == 0) { pendingCode = code; handler.postDelayed(startHold, HOLD_MS); return true }
            held.add(code)
            return true
        }
        val wasEmpty = held.isEmpty()
        held.add(code)
        if (wasEmpty) {
            heldSince = SystemClock.uptimeMillis()
            lastTick = heldSince
            val nudge = 8 * density
            moveCursor(
                nudge * ((if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else 0) - (if (code == KeyEvent.KEYCODE_DPAD_LEFT) 1 else 0)),
                nudge * ((if (code == KeyEvent.KEYCODE_DPAD_DOWN) 1 else 0) - (if (code == KeyEvent.KEYCODE_DPAD_UP) 1 else 0)),
            )
            handler.post(ticker)
        }
        return true
    }

    // ---- Pointer snapping ----
    private val snapOn get() = app.store.bool(K.SNAP)
    private var pendingCode = 0
    private var freeMoved = false
    private val startHold = Runnable { beginHold() }

    /** The arrow stayed down: from now on the pointer moves freely. */
    private fun beginHold() {
        val c = pendingCode
        pendingCode = 0
        if (c == 0 || !cursorMode || customView != null) return
        val first = held.isEmpty()
        held.add(c)
        freeMoved = true
        if (first) { heldSince = SystemClock.uptimeMillis(); lastTick = heldSince; handler.post(ticker) }
    }

    /** Clickable things on screen, as [left, top, right, bottom, fixed] in view pixels; the answer arrives on the main thread. */
    private fun queryTargets(done: (List<IntArray>) -> Unit) {
        webView.evaluateJavascript(TARGETS_JS) { v ->
            val out = ArrayList<IntArray>()
            runCatching {
                val a = org.json.JSONArray(v)
                for (i in 0 until a.length()) {
                    val r = a.getJSONArray(i)
                    out.add(intArrayOf(r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3), r.optInt(4, 0)))
                }
            }
            done(out)
        }
    }

    private fun placeCursor(x: Float, y: Float) {
        cursorX = x.coerceIn(0f, viewW - 1f)
        cursorY = y.coerceIn(0f, viewH - 1f)
        lastMove = SystemClock.uptimeMillis()
        cursorActive = true
        scheduleIdleHide()
        sendMouse(MotionEvent.ACTION_HOVER_MOVE, 0)
    }

    /** Short press: move the pointer onto the nearest clickable item in the arrow's direction. */
    private fun jumpTo(code: Int, retry: Boolean, fromY: Float? = null) {
        if (viewW == 0 || !cursorMode) return
        queryTargets { list ->
            val best = pickTarget(list, cursorX, fromY ?: cursorY, code)
            if (best != null) { placeCursor((best[0] + best[2]) / 2f, (best[1] + best[3]) / 2f); return@queryTargets }
            val vertical = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
            if (code == KeyEvent.KEYCODE_DPAD_UP && !webView.canScrollVertically(-1)) {
                if (cursorY <= 2f || list.isEmpty()) showToolbar() else placeCursor(cursorX, 0f)
                return@queryTargets
            }
            if (vertical) {
                // Nothing that way on screen: scroll once, then look again (the second look never scrolls).
                if (retry) {
                    val before = webView.scrollY
                    scrollWheel((if (code == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1) * viewH * 0.4f)
                    cursorActive = true
                    scheduleIdleHide()
                    // Look again from where the pointer sat on the page, which has moved up or down by the scrolled distance.
                    handler.postDelayed({ jumpTo(code, false, cursorY - (webView.scrollY - before)) }, 260)
                }
            } else if (retry) {
                // Nothing more that way: the pointer may sit on a row that scrolls sideways (episode lists), so scroll it once and look again.
                scrollWheelH((if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1) * viewW * 0.3f)
                cursorActive = true
                scheduleIdleHide()
                handler.postDelayed({ jumpTo(code, false) }, 220)
            } else {
                val s = 40 * density
                moveCursor(if (code == KeyEvent.KEYCODE_DPAD_RIGHT) s else -s, 0f)
            }
        }
    }

    /** Only items near the pointer count (see [SnapRules]), so a press never sends the pointer across the page. */
    private fun pickTarget(list: List<IntArray>, cx: Float, cy: Float, code: Int): IntArray? {
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> SnapRules.RIGHT
            KeyEvent.KEYCODE_DPAD_LEFT -> SnapRules.LEFT
            KeyEvent.KEYCODE_DPAD_DOWN -> SnapRules.DOWN
            else -> SnapRules.UP
        }
        return SnapRules.pick(list, cx, cy, dir, viewW, viewH, density)
    }

    /** After free movement: if the pointer stopped on or right next to a clickable item, centre it on that item. */
    private fun snapToNearby() {
        queryTargets { list ->
            var best: IntArray? = null
            var bd = 36 * density
            var ba = Long.MAX_VALUE
            val big = viewW.toLong() * viewH / 4
            for (r in list) {
                val area = (r[2] - r[0]).toLong() * (r[3] - r[1])
                if (area > big) continue
                val ddx = maxOf(r[0] - cursorX, 0f, cursorX - r[2])
                val ddy = maxOf(r[1] - cursorY, 0f, cursorY - r[3])
                val d = kotlin.math.hypot(ddx, ddy)
                if (d < bd || (d == bd && area < ba)) { bd = d; ba = area; best = r }
            }
            best?.let { placeCursor((it[0] + it[2]) / 2f, (it[1] + it[3]) / 2f) }
        }
    }

    // ---- Phone touchpad ----
    /** True when the pointer is what a touchpad should move: pointer mode with nothing on top of the page. */
    val pointerInteractive: Boolean
        get() = cursorMode && !reader && customView == null && !toolbarVisible && !sitePanel && error == null

    /** Moves the pointer by fractions of the view size (so a phone of any size feels alike), honouring the speed setting. */
    fun moveCursorBy(dx: Float, dy: Float) {
        if (viewW == 0) return
        val gain = 0.55f + 0.2f * app.store.int(K.CURSOR_SPEED)
        moveCursor(dx * viewW * gain, dy * viewH * gain)
    }

    /** Scrolls the page under the pointer; [dy] is a fraction of the view height, positive = content moves down. */
    fun scrollByFraction(dy: Float) {
        if (viewH == 0) return
        cursorActive = true
        scheduleIdleHide()
        scrollWheel(-dy * viewH * 1.6f)
    }

    fun clickAtPointer() = clickAtCursor()

    private fun moveCursor(dx: Float, dy: Float) {
        if (viewW == 0) return
        cursorX = (cursorX + dx).coerceIn(0f, viewW - 1f)
        val ny = cursorY + dy
        // Pushing against the top/bottom edge scrolls the page under the pointer.
        if (ny < 0f && dy < 0f) scrollWheel(ny) else if (ny > viewH - 1f && dy > 0f) scrollWheel(ny - (viewH - 1f))
        cursorY = ny.coerceIn(0f, viewH - 1f)
        lastMove = SystemClock.uptimeMillis()
        cursorActive = true
        scheduleIdleHide()
        sendMouse(MotionEvent.ACTION_HOVER_MOVE, 0)
    }

    private fun scheduleIdleHide() {
        handler.removeCallbacks(idleHide)
        handler.postDelayed(idleHide, 3000)
    }

    private val idleHide = Runnable {
        if (held.isEmpty()) {
            cursorActive = false
            // Pointer just faded out: a good moment to refresh the thumbnail with what the viewer is reading.
            val now = SystemClock.uptimeMillis()
            if (!loading && error == null && url.startsWith("http") && now - lastSnap > 15_000) {
                lastSnap = now
                snapshot(url)
            }
        }
    }
    private var lastSnap = 0L

    /** Scroll by pixels (positive = down) using a mouse-wheel event so inner scrollers work too. */
    private fun scrollWheel(pixels: Float) {
        val factor = ViewConfiguration.get(activity).getScaledVerticalScrollFactor().coerceAtLeast(1f)
        wheelRemainder += pixels * 1.4f
        val ticks = wheelRemainder / factor
        if (kotlin.math.abs(ticks) < 0.05f) return
        wheelRemainder = 0f
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = cursorX; y = cursorY; setAxisValue(MotionEvent.AXIS_VSCROLL, -ticks) })
        val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        if (!webView.dispatchGenericMotionEvent(ev)) webView.scrollBy(0, pixels.toInt())
        ev.recycle()
    }

    /** Scroll sideways by pixels (positive = right) under the pointer, for rows that overflow horizontally. */
    private fun scrollWheelH(pixels: Float) {
        val factor = ViewConfiguration.get(activity).getScaledHorizontalScrollFactor().coerceAtLeast(1f)
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = cursorX; y = cursorY; setAxisValue(MotionEvent.AXIS_HSCROLL, pixels * 1.4f / factor) })
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        webView.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private fun sendMouse(action: Int, buttons: Int, downTime: Long = SystemClock.uptimeMillis()) {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = cursorX; y = cursorY; pressure = 1f; size = 1f })
        val ev = MotionEvent.obtain(downTime, now, action, 1, props, coords, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        if (action == MotionEvent.ACTION_HOVER_MOVE) webView.dispatchGenericMotionEvent(ev) else webView.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun clickAtCursor() {
        cursorActive = true
        scheduleIdleHide()
        clickPulse++
        // Clicks are delivered as a finger tap: Chromium ignores synthetic mouse-button events,
        // while taps work on every page and inside cross-site player iframes.
        // The view takes focus only AFTER the tap: when it gains focus the page re-focuses the element it had focused
        // before (often at the top of the page) and scrolls it into view, so the tap landed on the wrong thing
        // (episode buttons far down a page "did nothing" and the page jumped up).
        val t = SystemClock.uptimeMillis()
        sendTap(MotionEvent.ACTION_DOWN, t)
        handler.postDelayed({ sendTap(MotionEvent.ACTION_UP, t); if (!webView.hasFocus()) webView.requestFocus() }, 50)
    }

    private fun sendTap(action: Int, downTime: Long) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = cursorX; y = cursorY; pressure = 1f; size = 1f })
        val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        webView.dispatchTouchEvent(ev)
        ev.recycle()
    }

    // ---- Video ----
    /**
     * Uses the in-page bridge (reaches videos inside cross-site player iframes) when present,
     * otherwise acts on the top page's own video with [action].
     */
    private fun videoJs(action: String, cmd: String, value: Int) = """(function(){if(window.__lunaCmd)return window.__lunaCmd('$cmd',$value);
var vs=document.getElementsByTagName('video'),b=null,ba=-1;
for(var i=0;i<vs.length;i++){var r=vs[i].getBoundingClientRect(),a=r.width*r.height;if(a>ba){ba=a;b=vs[i];}}
if(!b)return null;$action
return [b.paused,b.currentTime||0,isFinite(b.duration)?b.duration:0,(b.readyState<3&&!b.paused&&!b.ended),!!b.error];})()"""

    private fun runVideo(
        action: String,
        cmd: String = "state",
        value: Int = 0,
        after: (VideoState) -> VideoState = { it },
        done: (VideoState) -> Unit = {},
    ) {
        webView.evaluateJavascript(videoJs(action, cmd, value)) { r ->
            val s = parse(r)?.let(after)
            if (s != null) { video = s; done(s) }
        }
    }

    private fun parse(r: String?): VideoState? {
        if (r == null || r == "null") return null
        return try {
            val a = JSONArray(r)
            VideoState(a.getBoolean(0), a.getDouble(1), a.getDouble(2), a.getBoolean(3), a.getBoolean(4))
        } catch (e: Exception) { null }
    }

    private val playJs = "if(b.paused){var p=b.play();if(p&&p.catch)p.catch(function(){});}"
    private val pauseJs = "if(!b.paused)b.pause();"
    private val toggleJs = "if(b.paused){var p=b.play();if(p&&p.catch)p.catch(function(){});}else{b.pause();}"

    private fun flash(label: String, icon: ImageVector? = null, hold: Boolean = false) {
        val o = Osd(label, icon, SystemClock.uptimeMillis())
        osd = o
        handler.postDelayed({ if (osd?.id == o.id) osd = null }, if (hold) 1500 else 900)
    }

    private fun togglePlay(force: Boolean? = null) {
        val (js, paused) = when (force) { true -> playJs to false; false -> pauseJs to true; null -> toggleJs to null }
        val cmd = when (force) { true -> "play"; false -> "pause"; null -> "toggle" }
        runVideo(js, cmd, 0, after = { s -> s.copy(paused = paused ?: !s.paused) }) { s ->
            if (SystemClock.uptimeMillis() < resumeFlashUntil) return@runVideo
            flash(app.s(if (s.paused) R.string.vid_paused else R.string.vid_playing), if (s.paused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow)
        }
    }

    // ---- Seeking: presses and key-repeats are accumulated, previewed, then sent as ONE seek ----
    /** Target position (seconds) while the viewer is still choosing; null when not seeking. */
    var seekPreview by mutableStateOf<Double?>(null)
        private set
    private var pendingSeek = 0
    private val commitSeek = Runnable {
        val s = pendingSeek
        pendingSeek = 0
        if (s == 0) { seekPreview = null; return@Runnable }
        val js = "b.currentTime=Math.max(0,Math.min(isFinite(b.duration)?b.duration:1e9,b.currentTime+($s)));"
        runVideo(js, "seek", s) { seekPreview = null }
        // If the page has no reachable video, still clear the preview.
        handler.postDelayed({ if (pendingSeek == 0) seekPreview = null }, 800)
    }

    /**
     * Adds [seconds] to the pending seek and shows where it will land. The video itself is only
     * told once the viewer stops pressing, so holding the arrow no longer reloads the stream
     * (and stalls) at every step.
     */
    private fun queueSeek(seconds: Int, settleMs: Long = 650) {
        pendingSeek += seconds
        val v = video
        seekPreview = v?.let { (it.position + pendingSeek).coerceIn(0.0, if (it.duration > 0) it.duration else Double.MAX_VALUE) }
        val forward = pendingSeek >= 0
        val label = seekPreview?.let {
            app.s(if (forward) R.string.vid_forward_to else R.string.vid_back_to, fmtTime(it), signed(pendingSeek))
        } ?: app.s(if (forward) R.string.vid_forward_s else R.string.vid_back_s, kotlin.math.abs(pendingSeek))
        flash(label, null, hold = true)
        handler.removeCallbacks(commitSeek)
        handler.postDelayed(commitSeek, settleMs)
    }

    /** Key released: commit soon instead of waiting the full settle time. */
    private fun settleSeek() {
        if (pendingSeek == 0) return
        handler.removeCallbacks(commitSeek)
        handler.postDelayed(commitSeek, 250)
    }

    /** Step for one press or key-repeat: grows the longer the arrow is held (10 s → 30 s → 60 s). */
    private fun seekStep(repeat: Int): Int {
        val base = app.store.int(K.SEEK_STEP)
        return when {
            repeat < 12 -> base
            repeat < 30 -> base * 3
            else -> base * 6
        }
    }

    private fun signed(s: Int): String {
        val a = kotlin.math.abs(s)
        val t = if (a >= 60) "${a / 60}:${"%02d".format(a % 60)}" else "${a}s"
        return (if (s >= 0) "+" else "−") + t
    }

    private fun fmtTime(sec: Double): String {
        val t = sec.toLong().coerceAtLeast(0)
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }

    private fun mediaKey(code: Int) {
        val step = app.store.int(K.SEEK_STEP)
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> togglePlay()
            KeyEvent.KEYCODE_MEDIA_PLAY -> togglePlay(true)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> togglePlay(false)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> queueSeek(step * 3)
            KeyEvent.KEYCODE_MEDIA_REWIND -> queueSeek(-step * 3)
        }
        if (customView != null) showControls()
    }

    private fun handleVideoKey(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val rc = e.repeatCount
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!down) { if (!controlsVisible) showControls() else { togglePlay(); showControls() } }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down && rc == 0) mediaKey(e.keyCode)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val sign = if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                when {
                    !down -> settleSeek()
                    // Every press counts; while held, every 3rd repeat (~8 steps/s) with a growing step.
                    rc == 0 || rc % 3 == 0 -> { queueSeek(sign * seekStep(rc)); showControls() }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) showControls(); return true }
        }
        return false // volume and other system keys keep working
    }

    private fun showControls() {
        controlsVisible = true
        lastControls = SystemClock.uptimeMillis()
        handler.postDelayed({
            if (SystemClock.uptimeMillis() - lastControls >= 3900) controlsVisible = false
        }, 4000)
    }

    fun exitFullscreen() {
        webView.evaluateJavascript("if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen();", null)
        chrome.onHideCustomView()
        customCb?.onCustomViewHidden()
    }

    private val poll = object : Runnable {
        override fun run() {
            if (customView == null) return
            runVideo("", done = { trackProgress(it) })
            handler.postDelayed(this, 600)
        }
    }

    // ---- Resume: remember where each long video was left, and go back there next time ----
    /** The video (by [ResumeRules.key]) whose saved position was already offered in this fullscreen session. */
    private var resumeTried: String? = null

    /** The "Resumed from 12:34" message is not replaced by the generic "Playing" one before this time. */
    private var resumeFlashUntil = 0L

    private fun saveProgress(s: VideoState, flush: Boolean = false) {
        val u = url
        if (isPrivate || u.isBlank() || s.failed || (s.buffering && !flush)) return
        // Until the saved position has been applied, a freshly started video must not overwrite it.
        if (resumeTried != ResumeRules.key(u)) return
        app.store.savePosition(u, s.position, s.duration, flush)
    }

    private fun trackProgress(s: VideoState) {
        val u = url
        if (isPrivate || u.isBlank() || s.failed || s.buffering) return
        val key = ResumeRules.key(u)
        if (resumeTried != key && s.duration >= ResumeRules.MIN_DURATION) {
            resumeTried = key
            val saved = app.store.resumeFor(u)
            val delta = saved?.let { ResumeRules.restoreDelta(it, s.position, s.duration) }
            if (saved != null && delta != null) {
                val js = "b.currentTime=Math.max(0,Math.min(isFinite(b.duration)?b.duration:1e9,b.currentTime+($delta)));"
                runVideo(js, "seek", delta)
                val msg = app.s(R.string.vid_resumed, ResumeRules.clock(saved.pos))
                if (customView != null) {
                    flash(msg, Icons.Rounded.PlayArrow, hold = true)
                    resumeFlashUntil = SystemClock.uptimeMillis() + 1800
                } else {
                    app.toast(msg)
                }
                return
            }
        }
        saveProgress(s)
    }

    /**
     * Types [text] into the text field that has focus on the page (used by the phone remote).
     * [submit] also presses Enter / submits the form. [done] gets false when no field has focus.
     */
    fun typeIntoFocused(text: String, submit: Boolean, done: (Boolean) -> Unit) {
        val js = TYPE_JS.replace("__TEXT__", JSONObject.quote(text)).replace("__SUBMIT__", submit.toString())
        webView.evaluateJavascript(js) { r -> done(r == "\"ok\"") }
    }

    /**
     * Watches a video that plays inside the page (not fullscreen), so its position is remembered too.
     * Cheap: one tiny script every few seconds while the browser is showing; pages without a video
     * back off to a slower check.
     */
    private var noVideoChecks = 0
    private val inlineWatch = object : Runnable {
        override fun run() {
            if (!active || customView != null) return
            webView.evaluateJavascript(videoJs("", "state", 0)) { r ->
                val s = parse(r)
                if (s == null) noVideoChecks++ else { noVideoChecks = 0; trackProgress(s) }
                if (active && customView == null) handler.postDelayed(this, if (noVideoChecks > 2) 12_000 else 4_000)
            }
        }
    }

    private fun startInlineWatch() {
        noVideoChecks = 0
        handler.removeCallbacks(inlineWatch)
        handler.postDelayed(inlineWatch, 4_000)
    }

    private fun startPolling() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    private companion object {
        const val HOLD_MS = 220L
        /** Lists the visible clickable elements (links, buttons, inputs, anything with a pointer cursor) in view pixels. */
        const val TARGETS_JS = """(function(){try{var de=document.documentElement,v=window.visualViewport,s=(v&&v.scale>0)?v.scale:1,ox=v?(+v.offsetLeft||0):0,oy=v?(+v.offsetTop||0):0,
d=(window.devicePixelRatio||1)*s,W=(v&&v.width>0)?v.width:(de.clientWidth||+window.innerWidth||0),H=(v&&v.height>0)?v.height:(de.clientHeight||+window.innerHeight||0),out=[],seen={};
function ok(e){var b=e.getBoundingClientRect();if(b.width<8||b.height<8)return;
var l=Math.max(b.left,ox),t=Math.max(b.top,oy),r=Math.min(b.right,ox+W),bt=Math.min(b.bottom,oy+H);if(r-l<8||bt-t<8)return;
var h=document.elementFromPoint((l+r)/2,(t+bt)/2);if(!h||!(h===e||e.contains(h)))return;
var s=getComputedStyle(e);if(s.visibility==='hidden'||s.pointerEvents==='none'||parseFloat(s.opacity)<0.05)return;
var hr=e.href;if(hr&&typeof hr==='string'){var pr=seen[hr]||(seen[hr]=[]);for(var u=0;u<pr.length;u++){var q=pr[u];if(Math.max(q[0]-r,l-q[2],0)<40&&Math.max(q[1]-bt,t-q[3],0)<40)return;}pr.push([l,t,r,bt]);}
var f=0;for(var n=e,k=0;n&&k<8;n=n.parentElement,k++){if(getComputedStyle(n).position==='fixed'){f=1;break;}}
out.push([Math.round((l-ox)*d),Math.round((t-oy)*d),Math.round((r-ox)*d),Math.round((bt-oy)*d),f]);}
var q=document.querySelectorAll('a[href],button,input:not([type=hidden]),select,textarea,summary,label[for],[role=button],[role=link],[role=tab],[role=menuitem],[role=checkbox],[onclick],[tabindex]:not([tabindex="-1"])');
for(var i=0;i<q.length&&out.length<250;i++)ok(q[i]);
if(out.length<250){var c=document.querySelectorAll('div,span,li,img,td,svg');var n=0;
for(var j=0;j<c.length&&n<1200&&out.length<250;j++){var e=c[j];var b=e.getBoundingClientRect();if(b.bottom<oy||b.top>oy+H||b.right<ox||b.left>ox+W)continue;n++;
if(getComputedStyle(e).cursor!=='pointer')continue;var p=e.parentElement;if(p&&getComputedStyle(p).cursor==='pointer')continue;if(e.closest('a[href],button'))continue;ok(e);}}
return out;}catch(x){return [];}})()"""
        /** Sets the value of the focused input the way a user typing would (frameworks listen to input events). */
        const val TYPE_JS = """(function(){var e=document.activeElement;
while(e&&e.shadowRoot&&e.shadowRoot.activeElement)e=e.shadowRoot.activeElement;
if(!e)return 'none';var t=__TEXT__,tag=(e.tagName||'').toUpperCase();
if(tag==='INPUT'||tag==='TEXTAREA'){var p=tag==='INPUT'?HTMLInputElement.prototype:HTMLTextAreaElement.prototype;
var d=Object.getOwnPropertyDescriptor(p,'value');if(d&&d.set)d.set.call(e,t);else e.value=t;
e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}
else if(e.isContentEditable){e.textContent=t;e.dispatchEvent(new Event('input',{bubbles:true}));}
else return 'none';
if(__SUBMIT__){['keydown','keypress','keyup'].forEach(function(n){e.dispatchEvent(new KeyboardEvent(n,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));});
var f=e.form;if(f){if(f.requestSubmit)f.requestSubmit();else f.submit();}}
return 'ok';})()"""
        const val OG_JS = """(function(){var m=document.querySelector('meta[property="og:image"],meta[name="og:image"],meta[name="twitter:image"],meta[property="twitter:image"]');return m?m.content:null})()"""
    }

    // Declared last so every property above (clients, runnables) is initialised first.
    init {
        webView.webViewClient = client
        webView.webChromeClient = chrome
        // Run the overlay sweeper before any page script, so click-catchers never get a first click.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, PopupGuard.OVERLAY_JS, setOf("*"))
                WebViewCompat.addDocumentStartJavaScript(webView, PopupGuard.VIDEO_BRIDGE_JS, setOf("*"))
                docStartScript = true
            }
        }
    }
}
