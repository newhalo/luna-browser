package com.luna.browser.browser

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.luna.browser.AppState
import com.luna.browser.data.AdBlocker
import com.luna.browser.data.K
import com.luna.browser.data.Site
import org.json.JSONArray
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min

enum class ErrorKind { Offline, NotFound, Timeout, Ssl, Other }

data class PageError(val kind: ErrorKind, val title: String, val message: String)

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
class BrowserController(private val activity: Activity, private val app: AppState) {
    val webView = WebView(activity)
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

        CookieManager.getInstance().setAcceptCookie(true)
        webView.apply {
            setBackgroundColor(Color.parseColor("#0B0D12"))
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
            setDownloadListener { _, _, _, _, _ -> app.toast("Downloads aren't supported yet") }
        }
        applySettings()
        uaChanged = false
    }

    // ---- Lifecycle ----
    fun applySettings() {
        val s = webView.settings
        val ua = if (app.store.bool(K.DESKTOP)) desktopUa else baseUa
        if (s.userAgentString != ua) {
            s.userAgentString = ua
            uaChanged = true
        }
        s.textZoom = app.store.int(K.ZOOM)
        s.javaScriptCanOpenWindowsAutomatically = !app.store.bool(K.BLOCK_POPUPS)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, app.store.bool(K.THIRD_PARTY))
    }

    fun onEnter() {
        applySettings()
        if (uaChanged && webView.url != null) webView.reload()
        uaChanged = false
        webView.onResume()
        toolbarVisible = false
        webView.requestFocus()
        if (!app.store.bool(K.TIP_SHOWN)) {
            app.store.put(K.TIP_SHOWN, true)
            app.toast("Press Menu for the toolbar. Hold OK to switch cursor / focus mode.")
        }
    }

    fun onLeave() {
        held.clear()
        webView.onPause()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
    }

    // ---- Navigation actions ----
    fun load(target: String) {
        error = null
        url = target
        webView.loadUrl(target)
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
        app.toast(if (added) "Added to Favorites" else "Removed from Favorites")
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

    fun toggleMode() {
        cursorMode = !cursorMode
        held.clear()
        webView.requestFocus()
        app.toast(if (cursorMode) "Cursor mode: move the pointer, press OK to click" else "Focus mode: arrows jump between links")
    }

    /** Back handling inside the browser. Returns true when consumed. */
    fun onBack(): Boolean {
        if (customView != null) {
            if (controlsVisible) controlsVisible = false else exitFullscreen()
            return true
        }
        if (toolbarVisible) { hideToolbar(); return true }
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
            return scheme != "http" && scheme != "https" && scheme != "about" && scheme != "file" && scheme != "data" && scheme != "blob"
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (app.store.bool(K.ADBLOCK) && !request.isForMainFrame && app.ads.shouldBlock(request.url)) {
                return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
            error = null
            loading = true
            progress = 0.05f
            pageUrl?.let { url = it }
            syncNav()
        }

        override fun onPageFinished(view: WebView, pageUrl: String?) {
            loading = false
            progress = 1f
            pageUrl?.let { url = it }
            title = view.title ?: ""
            syncNav()
            if (error == null && pageUrl != null) app.store.recordVisit(title, pageUrl)
            if (app.store.bool(K.ADBLOCK)) view.evaluateJavascript(AdBlocker.COSMETIC_JS, null)
        }

        override fun doUpdateVisitedHistory(view: WebView, pageUrl: String?, isReload: Boolean) {
            pageUrl?.let { url = it }
            syncNav()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
            if (!request.isForMainFrame) return
            val desc = err.description?.toString() ?: ""
            if (desc.contains("ERR_ABORTED") || desc.contains("BLOCKED_BY_CLIENT")) return
            loading = false
            error = mapError(err.errorCode)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, err: SslError) {
            handler.cancel()
            loading = false
            error = PageError(ErrorKind.Ssl, "This connection isn't secure", "Luna blocked the page to keep your information safe. Try the address again or visit another site.")
        }
    }

    private fun mapError(code: Int): PageError {
        val cm = activity.getSystemService(ConnectivityManager::class.java)
        if (cm?.activeNetwork == null) {
            return PageError(ErrorKind.Offline, "No internet connection", "Check that your TV is connected to Wi-Fi or Ethernet, then try again.")
        }
        return when (code) {
            WebViewClient.ERROR_HOST_LOOKUP ->
                PageError(ErrorKind.NotFound, "Can't find this website", "The address may be misspelled, or the website may be down.")
            WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO ->
                PageError(ErrorKind.Timeout, "This website isn't responding", "It is taking too long to answer. Try again in a moment.")
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                PageError(ErrorKind.Ssl, "This connection isn't secure", "Luna couldn't set up a secure connection to this website.")
            else ->
                PageError(ErrorKind.Other, "This page couldn't be loaded", "Something went wrong while opening the page. Try again, or go back to the home screen.")
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
        }

        override fun onHideCustomView() {
            customView?.keepScreenOn = false
            customView = null
            customCb = null
            controlsVisible = false
            video = null
            handler.removeCallbacks(poll)
            webView.requestFocus()
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) return false
            val temp = WebView(activity)
            temp.webViewClient = object : WebViewClient() {
                private fun redirect(v: WebView, target: String?) {
                    if (target != null && target != "about:blank") {
                        v.stopLoading()
                        load(target)
                        handler.post { v.destroy() }
                    }
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

    // ---- Key handling ----
    fun handleKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        val down = e.action == KeyEvent.ACTION_DOWN
        if (code == KeyEvent.KEYCODE_MENU) {
            if (!down) { if (toolbarVisible) hideToolbar() else showToolbar() }
            return true
        }
        if (customView != null) return handleVideoKey(e)
        // Toolbar and error panels are Compose: let the focus system drive them.
        if (toolbarVisible || error != null) return false
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down && e.repeatCount == 0) mediaKey(code)
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (down) webView.pageDown(false); return true }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (down) webView.pageUp(false); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> return handleOk(e)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                return if (cursorMode) handleCursorKey(e) else focusModeKey(e)
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
            held.remove(code)
            return true
        }
        if (e.repeatCount > 0 || code in held) return true
        // Pressing Up once the pointer already sits at the very top reveals the toolbar.
        if (code == KeyEvent.KEYCODE_DPAD_UP && held.isEmpty() && cursorY <= 2f && !webView.canScrollVertically(-1)) {
            showToolbar()
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

    private val idleHide = Runnable { if (held.isEmpty()) cursorActive = false }

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
        webView.requestFocus()
        val t = SystemClock.uptimeMillis()
        sendMouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, t)
        handler.postDelayed({ sendMouse(MotionEvent.ACTION_UP, 0, t) }, 45)
    }

    // ---- Video ----
    private fun videoJs(action: String) = """(function(){var vs=document.getElementsByTagName('video'),b=null,ba=-1;
for(var i=0;i<vs.length;i++){var r=vs[i].getBoundingClientRect(),a=r.width*r.height;if(a>ba){ba=a;b=vs[i];}}
if(!b)return null;$action
return [b.paused,b.currentTime||0,isFinite(b.duration)?b.duration:0,(b.readyState<3&&!b.paused&&!b.ended),!!b.error];})()"""

    private fun runVideo(action: String, after: (VideoState) -> VideoState = { it }, done: (VideoState) -> Unit = {}) {
        webView.evaluateJavascript(videoJs(action)) { r ->
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

    private fun flash(label: String, icon: ImageVector? = null) {
        val o = Osd(label, icon, SystemClock.uptimeMillis())
        osd = o
        handler.postDelayed({ if (osd?.id == o.id) osd = null }, 900)
    }

    private fun togglePlay(force: Boolean? = null) {
        val (js, paused) = when (force) { true -> playJs to false; false -> pauseJs to true; null -> toggleJs to null }
        runVideo(js, after = { s -> s.copy(paused = paused ?: !s.paused) }) { s ->
            flash(if (s.paused) "Paused" else "Playing", if (s.paused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow)
        }
    }

    private fun seek(seconds: Int) {
        val js = "b.currentTime=Math.max(0,Math.min(isFinite(b.duration)?b.duration:1e9,b.currentTime+($seconds)));"
        runVideo(js) { flash(if (seconds > 0) "+${seconds}s" else "−${-seconds}s") }
    }

    private fun mediaKey(code: Int) {
        val step = app.store.int(K.SEEK_STEP)
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> togglePlay()
            KeyEvent.KEYCODE_MEDIA_PLAY -> togglePlay(true)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> togglePlay(false)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(step * 3)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-step * 3)
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
                if (down && (rc == 0 || rc % 5 == 0)) {
                    val sign = if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    seek(sign * app.store.int(K.SEEK_STEP) * (1 + min(rc / 10, 3)))
                    showControls()
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
            runVideo("")
            handler.postDelayed(this, 600)
        }
    }

    private fun startPolling() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    // Declared last so every property above (clients, runnables) is initialised first.
    init {
        webView.webViewClient = client
        webView.webChromeClient = chrome
    }
}
