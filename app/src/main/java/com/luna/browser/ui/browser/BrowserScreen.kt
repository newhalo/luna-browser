package com.luna.browser.ui.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tab as TabIcon
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.luna.browser.AppState
import com.luna.browser.R
import com.luna.browser.Screen
import com.luna.browser.browser.BrowserController
import com.luna.browser.browser.ErrorKind
import com.luna.browser.browser.PageError
import com.luna.browser.data.K
import com.luna.browser.data.SiteOverride
import com.luna.browser.data.SiteSettings
import com.luna.browser.data.hostOf
import com.luna.browser.ui.components.SettingRow
import com.luna.browser.ui.components.SiteIcon
import com.luna.browser.ui.theme.Scrim
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.IconLabelButton
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.Danger
import com.luna.browser.ui.components.qrBitmap
import com.luna.browser.data.ReportLink
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.FilterQuality
import com.luna.browser.ui.theme.Outline
import com.luna.browser.ui.theme.Surface
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary
import com.luna.browser.ui.theme.ms
import kotlinx.coroutines.delay

@Composable
fun BrowserScreen(app: AppState) {
    val b = app.browser
    // Each tab has its own controller and WebView: start from scratch when the tab changes.
    androidx.compose.runtime.key(b) { BrowserContent(app, b) }
}

@Composable
private fun BrowserContent(app: AppState, b: BrowserController) {
    LaunchedEffect(b.toolbarVisible, b.error, b.sitePanel) {
        if (!b.toolbarVisible && b.error == null && !b.sitePanel) b.webView.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
        AndroidView(
            factory = { b.webView.also { v -> (v.parent as? ViewGroup)?.removeView(v) } },
            modifier = Modifier.fillMaxSize().onSizeChanged { b.onViewSize(it.width, it.height) },
        )

        if (b.cursorMode && !b.reader && b.customView == null && !b.toolbarVisible && !b.sitePanel && b.error == null && !b.capturing) CursorOverlay(b)

        // Slim progress bar
        if (b.loading && b.error == null && b.customView == null) {
            val p by animateFloatAsState(b.progress.coerceIn(0.04f, 1f), tween(ms(200)), label = "progress")
            Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x33FFFFFF)).align(Alignment.TopStart)) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Accent))
            }
        }

        b.error?.let { ErrorPanel(app, it) }
        if (b.customView != null) FullscreenLayer(b) else OsdFlash(b)
        if (b.reader) ReaderHud(b)
        Toolbar(app, b)
        if (b.sitePanel) SitePanel(app, b)
    }
}

// ---------------------------------------------------------------- Cursor

@Composable
private fun CursorOverlay(b: BrowserController) {
    val alpha by animateFloatAsState(if (b.cursorActive) 1f else 0f, tween(ms(220)), label = "cursorAlpha")
    val pulse = remember { Animatable(1f) }
    val pulseMs = ms(280)
    LaunchedEffect(b.clickPulse) {
        if (b.clickPulse > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(pulseMs))
        }
    }
    val d = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        val x = b.cursorX
        val y = b.cursorY
        if (pulse.value < 1f) {
            drawCircle(Accent.copy(alpha = (1f - pulse.value) * 0.8f), radius = (10f + 36f * pulse.value) * d, center = Offset(x, y), style = Stroke(3f * d))
        }
        val s = 26f * d
        val arrow = Path().apply {
            moveTo(x, y)
            lineTo(x, y + s * 0.86f)
            lineTo(x + s * 0.22f, y + s * 0.66f)
            lineTo(x + s * 0.36f, y + s)
            lineTo(x + s * 0.50f, y + s * 0.93f)
            lineTo(x + s * 0.36f, y + s * 0.61f)
            lineTo(x + s * 0.64f, y + s * 0.61f)
            close()
        }
        drawPath(arrow, Color.White.copy(alpha = alpha))
        drawPath(arrow, Color.Black.copy(alpha = alpha), style = Stroke(2f * d))
    }
}

// ---------------------------------------------------------------- Toolbar

@Composable
private fun Toolbar(app: AppState, b: BrowserController) {
    LaunchedEffect(b.toolbarVisible, b.toolbarTouch) {
        if (b.toolbarVisible) {
            delay(8000)
            b.hideToolbar()
        }
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = b.toolbarVisible,
            enter = slideInVertically(tween(ms(220))) { -it } + fadeIn(tween(ms(200))),
            exit = slideOutVertically(tween(ms(160))) { -it } + fadeOut(tween(ms(140))),
        ) {
            val addressFr = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { addressFr.requestFocus() } }
            // Two rows: the address on top (wide, readable), the actions below. Down from the actions closes it.
            var inActions by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xE6121622))
                    .border(1.dp, Outline, RoundedCornerShape(26.dp))
                    .padding(12.dp)
                    .onPreviewKeyEvent { e ->
                        b.touchToolbar()
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && inActions) { b.hideToolbar(); true } else false
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FocusSurface(
                    onClick = { b.hideToolbar(); app.openAddress(b.url) },
                    modifier = Modifier.fillMaxWidth().height(66.dp).focusRequester(addressFr),
                    shape = RoundedCornerShape(33.dp),
                ) {
                    Row(Modifier.padding(horizontal = 22.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (b.url.startsWith("https")) Icons.Rounded.Lock else Icons.Rounded.Search, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            Txt(if (b.url.isEmpty()) stringResource(R.string.tb_address_hint) else hostOf(b.url), T.label)
                            Txt(b.title.ifBlank { stringResource(R.string.tb_edit_hint) }, T.caption)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().onFocusChanged { inActions = it.hasFocus },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconLabelButton(stringResource(R.string.tb_back), Icons.AutoMirrored.Rounded.ArrowBack, { b.goBack() }, enabled = b.canGoBack)
                    IconLabelButton(stringResource(R.string.tb_forward), Icons.AutoMirrored.Rounded.ArrowForward, { b.goForward() }, enabled = b.canGoForward)
                    IconLabelButton(stringResource(R.string.tb_refresh), Icons.Rounded.Refresh, { b.reload(); b.hideToolbar() })
                    IconLabelButton(stringResource(R.string.tb_home), Icons.Rounded.Home, { app.goHome() })
                    IconLabelButton(stringResource(R.string.tb_tabs), Icons.Rounded.TabIcon, { app.openTabs() }, highlighted = app.tabs.count > 1)
                    val fav = app.store.isFavorite(b.url)
                    IconLabelButton(stringResource(if (fav) R.string.tb_saved else R.string.tb_save), if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, { b.toggleFavorite() }, highlighted = fav)
                    IconLabelButton(
                        stringResource(R.string.tb_read), Icons.Rounded.MenuBook, { b.toggleReader() },
                        enabled = b.url.startsWith("http"), highlighted = b.reader,
                    )
                    IconLabelButton(stringResource(if (b.cursorMode) R.string.tb_mode_pointer else R.string.tb_mode_links), if (b.cursorMode) Icons.Rounded.Mouse else Icons.Rounded.TouchApp, { b.toggleMode() })
                    IconLabelButton(
                        stringResource(R.string.tb_site), Icons.Rounded.Tune, { b.showSitePanel() },
                        enabled = b.url.startsWith("http"), highlighted = app.store.overrideFor(b.url).let { !it.isEmpty },
                    )
                    IconLabelButton(stringResource(R.string.tb_settings), Icons.Rounded.Settings, { app.navigate(Screen.Settings) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Reading mode

/** Key hints over the reading page: for a few seconds after a key press, and all the time while the article is read aloud. */
@Composable
private fun BoxScope.ReaderHud(b: BrowserController) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(b.readerHudAt) {
        visible = true
        delay(5000)
        visible = false
    }
    AnimatedVisibility(
        visible = visible || b.speaking,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
        enter = fadeIn(tween(ms(160))), exit = fadeOut(tween(ms(240))),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xE6121622)).border(1.dp, Outline, RoundedCornerShape(30.dp))
                .padding(horizontal = 26.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(if (b.speaking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Accent, modifier = Modifier.size(30.dp))
            Txt(stringResource(if (b.speaking) R.string.reader_hud_speaking else R.string.reader_hud_idle), T.label)
        }
    }
}

// ---------------------------------------------------------------- Per-site settings

/**
 * "This site" panel: every row cycles through Default → options when OK is pressed, and applies at once
 * (text size, control mode) or when the panel closes (desktop site and ad blocking reload the page).
 */
@Composable
private fun SitePanel(app: AppState, b: BrowserController) {
    val st = app.store
    val url = b.url
    val o = st.overrideFor(url)            // reads the snapshot map, so the rows update as they change
    val host = hostOf(url)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val on = stringResource(R.string.set_on)
    val off = stringResource(R.string.set_off)
    fun onOff(v: Boolean) = if (v) on else off
    val defDesktop = stringResource(R.string.site_default_v, onOff(st.bool(K.DESKTOP)))
    val defZoom = stringResource(R.string.site_default_v, "${st.int(K.ZOOM)}%")
    val defBlock = stringResource(R.string.site_default_v, onOff(st.bool(K.ADBLOCK)))
    val defMode = stringResource(R.string.site_default_v, stringResource(if (st.bool(K.CURSOR_START)) R.string.site_pointer else R.string.site_links))
    val pointer = stringResource(R.string.site_pointer)
    val links = stringResource(R.string.site_links)
    fun chip(text: String) = "‹  $text  ›"
    fun set(next: SiteOverride) = st.setOverride(url, next)
    var reporting by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(780.dp).heightIn(max = 520.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xF2121622)).border(1.dp, Outline, RoundedCornerShape(28.dp))
                .padding(26.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (reporting) { ReportView(app, url) { reporting = false }; return@Column }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(host, st.iconFor(host), 48.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Txt(host, T.title)
                    Txt(stringResource(R.string.site_sub), T.caption)
                }
            }
            Spacer(Modifier.height(4.dp))
            SettingRow(
                stringResource(R.string.site_desktop), stringResource(R.string.site_desktop_d),
                chip(when (o.desktop) { null -> defDesktop; true -> on; false -> off }), false,
                modifier = Modifier.focusRequester(first),
            ) { set(o.copy(desktop = SiteSettings.next(o.desktop, listOf(null, true, false)))); b.siteChangeNeedsReload() }
            SettingRow(
                stringResource(R.string.site_zoom), stringResource(R.string.site_zoom_d),
                chip(o.zoom?.let { "$it%" } ?: defZoom), false,
            ) { set(o.copy(zoom = SiteSettings.next(o.zoom, listOf(null, 100, 125, 150, 175)))); b.siteSettingsChanged(reload = false) }
            SettingRow(
                stringResource(R.string.site_adblock), stringResource(R.string.site_adblock_d),
                chip(when (o.adblock) { null -> defBlock; true -> on; false -> off }), false,
            ) { set(o.copy(adblock = SiteSettings.next(o.adblock, listOf(null, true, false)))); b.siteChangeNeedsReload() }
            SettingRow(
                stringResource(R.string.site_mode), stringResource(R.string.site_mode_d),
                chip(when (o.pointer) { null -> defMode; true -> pointer; false -> links }), false,
            ) { set(o.copy(pointer = SiteSettings.next(o.pointer, listOf(null, true, false)))); b.siteSettingsChanged(reload = false) }
            if (!o.isEmpty) {
                SettingRow(
                    stringResource(R.string.site_reset), stringResource(R.string.site_reset_d), "", false, danger = true,
                ) { set(SiteOverride()); b.siteChangeNeedsReload(); b.siteSettingsChanged(reload = false); app.toast(R.string.toast_site_reset) }
            }
            SettingRow(stringResource(R.string.site_report), stringResource(R.string.site_report_d), "", false) { reporting = true }
            Txt(stringResource(R.string.site_hint), T.caption, Modifier.padding(top = 4.dp, start = 6.dp), color = TextSecondary)
        }
    }
}

/** "Report a problem": a QR code that opens a pre-filled GitHub report on the viewer's phone. Nothing is sent by the TV. */
@Composable
private fun ColumnScope.ReportView(app: AppState, url: String, onBack: () -> Unit) {
    val link = remember(url) { app.reportLink(url) }
    val page = remember(url) { ReportLink.cleanPage(url) ?: "" }
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { back.requestFocus() } }
    Txt(stringResource(R.string.report_title), T.title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (link != null) {
            val qr = remember(link) { qrBitmap(link) }
            Image(qr, null, Modifier.size(220.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), filterQuality = FilterQuality.None)
        }
        Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(stringResource(R.string.report_scan), T.body, maxLines = 3)
            Txt(stringResource(R.string.report_includes, page), T.caption, maxLines = 3, color = TextSecondary)
            Txt(stringResource(R.string.report_privacy), T.caption, maxLines = 4, color = TextSecondary)
        }
    }
    TvButton(stringResource(R.string.report_back), onBack, Modifier.focusRequester(back), icon = Icons.AutoMirrored.Rounded.ArrowBack)
}

// ---------------------------------------------------------------- Errors

@Composable
private fun ErrorPanel(app: AppState, err: PageError) {
    val b = app.browser
    val fr = remember { FocusRequester() }
    LaunchedEffect(err) { runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Surface), contentAlignment = Alignment.Center) {
                Icon(if (err.kind == ErrorKind.Offline) Icons.Rounded.WifiOff else Icons.Rounded.ErrorOutline, null, tint = Danger, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(20.dp))
            Txt(stringResource(err.title), T.display.copy(fontSize = 34.sp), align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Txt(stringResource(err.message), T.body.copy(color = TextSecondary), maxLines = 3, align = TextAlign.Center, modifier = Modifier.width(560.dp), color = TextSecondary)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(stringResource(R.string.err_retry), { b.retry() }, Modifier.focusRequester(fr), Icons.Rounded.Refresh, primary = true)
                TvButton(stringResource(R.string.err_change), { app.openAddress(b.url) }, icon = Icons.Rounded.Search)
                TvButton(stringResource(R.string.err_home), { app.goHome() }, icon = Icons.Rounded.Home)
            }
        }
    }
}

// ---------------------------------------------------------------- Fullscreen video

@Composable
private fun FullscreenLayer(b: BrowserController) {
    val cv = b.customView ?: return
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) } },
            modifier = Modifier.fillMaxSize(),
            update = { fl ->
                if (fl.childCount == 0 || fl.getChildAt(0) !== cv) {
                    fl.removeAllViews()
                    (cv.parent as? ViewGroup)?.removeView(cv)
                    fl.addView(cv, FrameLayout.LayoutParams(-1, -1))
                }
            },
        )
        VideoOverlay(b)
    }
}

private fun fmt(sec: Double): String {
    val t = sec.toLong().coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun BoxScope.VideoOverlay(b: BrowserController) {
    val v = b.video
    // Buffering — unobtrusive spinner in the corner, with a text label (not colour only).
    if (v != null && v.buffering && !v.failed) {
        Row(
            Modifier.align(Alignment.TopEnd).padding(32.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            Txt("  " + stringResource(R.string.vid_loading), T.label)
        }
    }
    if (v != null && v.failed) {
        Column(
            Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xE6101420)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Danger, modifier = Modifier.size(48.dp))
            Txt(stringResource(R.string.vid_failed), T.title, Modifier.padding(top = 8.dp))
            Txt(stringResource(R.string.vid_failed_hint), T.caption)
        }
    }
    // Centre flash for play / pause / seek feedback
    b.osd?.let { o ->
        Row(
            Modifier.align(Alignment.Center).clip(RoundedCornerShape(40.dp)).background(Color(0xB3000000)).padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (o.icon != null) Icon(o.icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
            Txt((if (o.icon != null) "  " else "") + o.label, T.title)
        }
    }
    // Controls
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = b.controlsVisible, enter = fadeIn(tween(ms(180))), exit = fadeOut(tween(ms(180)))) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 32.dp),
            ) {
                // While choosing a seek target, the bar shows the target (bright) ahead of / behind
                // the real position (dim); the video is only told once the viewer stops pressing.
                val preview = b.seekPreview
                val dur = v?.duration ?: 0.0
                val frac = if (v != null && dur > 0) (v.position / dur).toFloat().coerceIn(0f, 1f) else 0f
                val target = if (preview != null && dur > 0) (preview / dur).toFloat().coerceIn(0f, 1f) else null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        when {
                            v == null -> stringResource(R.string.vid_no_video)
                            preview != null -> stringResource(R.string.vid_skip_to, fmt(preview))
                            v.paused -> stringResource(R.string.vid_paused)
                            else -> stringResource(R.string.vid_playing)
                        },
                        T.label,
                    )
                    Spacer(Modifier.weight(1f))
                    if (v != null) Txt("${fmt(preview ?: v.position)} / ${if (dur > 0) fmt(dur) else stringResource(R.string.vid_live)}", T.label)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x44FFFFFF))) {
                    if (target != null) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent.copy(alpha = 0.45f)))
                        Box(Modifier.fillMaxWidth(target).fillMaxHeight().background(Accent))
                    } else {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Txt(stringResource(R.string.vid_hint), T.caption, color = Color(0xCCFFFFFF))
            }
        }
    }
}

/** Play / pause / seek feedback for videos inside the page (not fullscreen). */
@Composable
private fun BoxScope.OsdFlash(b: BrowserController) {
    val o = b.osd ?: return
    Row(
        Modifier.align(Alignment.Center).clip(RoundedCornerShape(40.dp)).background(Color(0xCC000000)).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (o.icon != null) Icon(o.icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
        Txt((if (o.icon != null) "  " else "") + o.label, T.title)
    }
}
