package com.luna.browser.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.data.K
import com.luna.browser.data.MediaItem
import com.luna.browser.data.Site
import com.luna.browser.ui.components.Art
import com.luna.browser.ui.components.Aurora
import com.luna.browser.ui.components.EmptyState
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.SiteIcon
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.components.rememberFocus
import com.luna.browser.ui.components.timeAgo
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Glass
import com.luna.browser.ui.theme.T

@Composable
private fun LibraryFrame(app: AppState, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Aurora(animated = app.store.bool(K.ANIMATED_BG), dim = 0.6f)
        Column(Modifier.fillMaxSize().padding(horizontal = Dim.SafeH, vertical = Dim.SafeV)) { content() }
    }
}

/** List row with a 16:9 thumbnail on the left. */
@Composable
private fun ThumbRow(item: MediaItem, meta: String, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick, modifier.fillMaxWidth().height(96.dp), container = Glass, focusScale = 1.02f) {
        Row(Modifier.fillMaxHeight().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(item.brand.first), Color(item.brand.second)))),
                contentAlignment = Alignment.Center,
            ) {
                if (item.art != null) Art(item.art, Modifier.fillMaxSize()) else SiteIcon(item.title, item.icon, 36.dp)
            }
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                Txt(item.title, T.body)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(item.title, item.icon, 18.dp)
                    Txt("  " + meta, T.caption)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(app: AppState) {
    val items = remember(app.store.version) { app.store.continueItems(300) }
    val homeFr = remember { FocusRequester() }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) runCatching { homeFr.requestFocus() } }

    LibraryFrame(app) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt("Lịch sử", T.display.copy(fontSize = 38.sp), Modifier.weight(1f))
            if (items.isNotEmpty()) {
                TvButton(if (confirm) "Bấm OK lần nữa để xóa" else "Xóa lịch sử", {
                    if (confirm) { app.store.clearHistory(); confirm = false; app.toast("Đã xóa lịch sử") } else confirm = true
                }, icon = Icons.Rounded.Delete, container = Glass)
            }
        }
        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.History, "Chưa có lịch sử",
                "Các trang và video bạn xem sẽ hiện ở đây kèm ảnh thu nhỏ để mở lại thật nhanh.",
                "Về trang chủ", { app.goHome() }, Modifier.weight(1f), homeFr,
            )
        } else {
            // Lazy: up to 300 entries, only the visible rows are composed.
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }) { i, it ->
                    ThumbRow(it, "${it.subtitle} · ${timeAgo(it.time)}", Modifier.padding(horizontal = 6.dp).rememberFocus(app, "history", "h$i")) { app.openUrl(it.url) }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(app: AppState) {
    val items = remember(app.store.version) { app.store.favoriteItems() }
    val homeFr = remember { FocusRequester() }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) runCatching { homeFr.requestFocus() } }

    LibraryFrame(app) {
        Txt("Yêu thích", T.display.copy(fontSize = 38.sp))
        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.Star, "Chưa có trang yêu thích",
                "Khi đang xem một trang, bấm Menu rồi chọn Lưu để giữ trang đó ở đây.",
                "Về trang chủ", { app.goHome() }, Modifier.weight(1f), homeFr,
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEachIndexed { i, it ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.weight(1f)) {
                            ThumbRow(it, it.subtitle, Modifier.rememberFocus(app, "fav", "o$i")) { app.openUrl(it.url) }
                        }
                        TvButton("Bỏ", {
                            app.store.removeFavorite(Site(it.title, it.url)); app.toast("Đã bỏ khỏi Yêu thích")
                        }, Modifier.width(110.dp).rememberFocus(app, "fav", "x$i"), Icons.Rounded.Close, container = Glass)
                    }
                }
            }
        }
    }
}
