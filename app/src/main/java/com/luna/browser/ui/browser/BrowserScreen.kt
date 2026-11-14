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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.luna.browser.Screen
import com.luna.browser.browser.BrowserController
import com.luna.browser.browser.ErrorKind
import com.luna.browser.browser.PageError
import com.luna.browser.data.hostOf
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.IconLabelButton
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.Danger
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
    LaunchedEffect(b.toolbarVisible, b.error) {
        if (!b.toolbarVisible && b.error == null) b.webView.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
        AndroidView(
            factory = { b.webView.also { v -> (v.parent as? ViewGroup)?.removeView(v) } },
            modifier = Modifier.fillMaxSize().onSizeChanged { b.onViewSize(it.width, it.height) },
        )

        if (b.cursorMode && b.customView == null && !b.toolbarVisible && b.error == null) CursorOverlay(b)

        // Slim progress bar
        if (b.loading && b.error == null && b.customView == null) {
            val p by animateFloatAsState(b.progress.coerceIn(0.04f, 1f), tween(ms(200)), label = "progress")
            Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x33FFFFFF)).align(Alignment.TopStart)) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Accent))
            }
        }

        b.error?.let { ErrorPanel(app, it) }
        if (b.customView != null) FullscreenLayer(b)
        Toolbar(app, b)
    }
}

// ---------------------------------------------------------------- Cursor

@Composable
private fun CursorOverlay(b: BrowserController) {
    val alpha by animateFloatAsState(if (b.cursorActive) 1f else 0f, tween(ms(220)), label = "cursorAlpha")
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(b.clickPulse) {
        if (b.clickPulse > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(ms(280)))
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
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xF2101420))
                    .border(1.dp, Outline, RoundedCornerShape(26.dp))
                    .padding(10.dp)
                    .onPreviewKeyEvent { e ->
                        b.touchToolbar()
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { b.hideToolbar(); true } else false
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconLabelButton("Back", Icons.AutoMirrored.Rounded.ArrowBack, { b.goBack() }, enabled = b.canGoBack)
                IconLabelButton("Forward", Icons.AutoMirrored.Rounded.ArrowForward, { b.goForward() }, enabled = b.canGoForward)
                IconLabelButton("Refresh", Icons.Rounded.Refresh, { b.reload(); b.hideToolbar() })
                IconLabelButton("Home", Icons.Rounded.Home, { app.goHome() })
                FocusSurface(
                    onClick = { b.hideToolbar(); app.openAddress(b.url) },
                    modifier = Modifier.weight(1f).height(66.dp).focusRequester(addressFr),
                    shape = RoundedCornerShape(33.dp),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (b.url.startsWith("https")) Icons.Rounded.Lock else Icons.Rounded.Search, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Txt(if (b.url.isEmpty()) "Search or enter address" else hostOf(b.url), T.label)
                            Txt(b.title.ifBlank { "Press OK to type an address" }, T.caption.copy(fontSize = 14.sp))
                        }
                    }
                }
                val fav = app.store.isFavorite(b.url)
                IconLabelButton(if (fav) "Saved" else "Save", if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, { b.toggleFavorite() }, highlighted = fav)
                IconLabelButton(if (b.cursorMode) "Cursor" else "Focus", if (b.cursorMode) Icons.Rounded.Mouse else Icons.Rounded.TouchApp, { b.toggleMode() })
                IconLabelButton("Settings", Icons.Rounded.Settings, { app.navigate(Screen.Settings) })
            }
        }
    }
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
            Txt(err.title, T.display.copy(fontSize = 34.sp), align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Txt(err.message, T.body.copy(color = TextSecondary), maxLines = 3, align = TextAlign.Center, modifier = Modifier.width(560.dp), color = TextSecondary)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton("Try again", { b.retry() }, Modifier.focusRequester(fr), Icons.Rounded.Refresh, primary = true)
                TvButton("Change address", { app.openAddress(b.url) }, icon = Icons.Rounded.Search)
                TvButton("Home", { app.goHome() }, icon = Icons.Rounded.Home)
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
            Txt("  Buffering", T.label)
        }
    }
    if (v != null && v.failed) {
        Column(
            Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xE6101420)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Danger, modifier = Modifier.size(48.dp))
            Txt("This video can't be played", T.title, Modifier.padding(top = 8.dp))
            Txt("Press Back to leave fullscreen, then try again.", T.caption)
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
                val frac = if (v != null && v.duration > 0) (v.position / v.duration).toFloat().coerceIn(0f, 1f) else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(if (v == null) "No controllable video found" else if (v.paused) "Paused" else "Playing", T.label)
                    Spacer(Modifier.weight(1f))
                    if (v != null) Txt("${fmt(v.position)} / ${if (v.duration > 0) fmt(v.duration) else "Live"}", T.label)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x44FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                }
                Spacer(Modifier.height(12.dp))
                Txt("◀ ▶  Seek     OK  Play / Pause     Back  Hide controls", T.caption, color = Color(0xCCFFFFFF))
            }
        }
    }
}
