package com.luna.browser.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luna.browser.AppState
import com.luna.browser.Screen
import com.luna.browser.data.K
import com.luna.browser.data.MediaItem
import com.luna.browser.data.hostOf
import com.luna.browser.ui.components.Backdrop
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.MediaCard
import com.luna.browser.ui.components.MoonLogo
import com.luna.browser.ui.components.Pill
import com.luna.browser.ui.components.SiteIcon
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.components.greeting
import com.luna.browser.ui.components.rememberFocus
import com.luna.browser.ui.components.timeAgo
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Danger
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Glass
import com.luna.browser.ui.theme.Scrim
import com.luna.browser.ui.theme.Surface
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary
import com.luna.browser.ui.theme.ms
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(app: AppState) {
    val store = app.store
    var hero by remember { mutableStateOf<MediaItem?>(null) }
    val searchFr = remember { FocusRequester() }
    val firstCardFr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (app.focusMemory["home"] == null || app.focusMemory["home"] == "search") runCatching { searchFr.requestFocus() }
    }

    val rows = buildList {
        val cont = if (store.bool(K.SHOW_CONTINUE)) store.continueItems() else emptyList()
        if (cont.isNotEmpty()) add(Triple("Xem tiếp", cont, 272.dp))
        add(Triple("Trang web của bạn", store.siteItems(), 216.dp))
        val favs = store.favoriteItems()
        if (favs.isNotEmpty()) add(Triple("Yêu thích", favs, 216.dp))
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(hero?.art, animated = store.bool(K.ANIMATED_BG))
        Column(Modifier.fillMaxSize().padding(top = Dim.SafeV)) {
            Header(app)
            HeroInfo(hero, app.online)
            SearchRow(app, searchFr, firstCardFr) { hero = null }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rows.forEachIndexed { r, (title, items, width) ->
                    MediaRow(app, title, items, width, if (r == 0) firstCardFr else null, onFocus = { hero = it }) { app.openUrl(it.url) }
                }
            }
        }
    }
}

@Composable
private fun Header(app: AppState) {
    var now by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            now = f.format(Date())
            delay(15_000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Dim.SafeH), verticalAlignment = Alignment.CenterVertically) {
        MoonLogo(Modifier.size(34.dp))
        Txt("  Luna", T.title.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvButton("Lịch sử", { app.navigate(Screen.History) }, Modifier.rememberFocus(app, "home", "nav:history"), Icons.Rounded.History, container = Glass)
            TvButton("Yêu thích", { app.navigate(Screen.Favorites) }, Modifier.rememberFocus(app, "home", "nav:fav"), Icons.Rounded.Star, container = Glass)
            TvButton("Cài đặt", { app.navigate(Screen.Settings) }, Modifier.rememberFocus(app, "home", "nav:settings"), Icons.Rounded.Settings, container = Glass)
            Txt(now, T.title.copy(fontSize = 26.sp, fontWeight = FontWeight.Light), Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun HeroInfo(hero: MediaItem?, online: Boolean) {
    Box(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = Dim.SafeH), contentAlignment = Alignment.BottomStart) {
        Crossfade(targetState = hero, animationSpec = tween(ms(220)), label = "hero") { h ->
            Column(Modifier.width(640.dp)) {
                if (h == null) {
                    Txt(greeting(), T.display.copy(fontSize = 42.sp))
                    if (!online) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WifiOff, null, tint = Danger, modifier = Modifier.size(22.dp))
                            Txt("  TV đang mất kết nối Internet", T.body, color = Danger)
                        }
                    } else Txt("Hôm nay bạn muốn xem gì?", T.body.copy(fontSize = 22.sp), color = TextSecondary)
                } else {
                    Txt(h.title, T.display.copy(fontSize = 38.sp), maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(h.subtitle)
                        if (h.time > 0) Pill(timeAgo(h.time))
                        Txt("Bấm OK để mở", T.caption, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(app: AppState, searchFr: FocusRequester, firstCardFr: FocusRequester, onFocus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Dim.SafeH, end = Dim.SafeH, top = 18.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusSurface(
            onClick = { app.openAddress() },
            modifier = Modifier.width(560.dp).height(58.dp)
                .rememberFocus(app, "home", "search")
                .focusRequester(searchFr)
                .focusProperties { down = firstCardFr }
                .onFocusChanged { if (it.isFocused) onFocus() },
            shape = RoundedCornerShape(29.dp),
            container = Glass,
            focusScale = 1.03f,
        ) {
            Row(Modifier.padding(horizontal = 24.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Accent, modifier = Modifier.size(28.dp))
                Txt("  Tìm kiếm hoặc nhập địa chỉ trang web", T.body, color = TextSecondary)
            }
        }
        if (app.voiceAvailable) {
            TvButton(
                "Tìm bằng giọng nói", { app.startVoice() },
                Modifier.height(58.dp).focusProperties { down = firstCardFr }.onFocusChanged { if (it.isFocused) onFocus() },
                Icons.Rounded.Mic, container = Glass,
            )
        }
    }
}

@Composable
private fun MediaRow(
    app: AppState,
    title: String,
    items: List<MediaItem>,
    width: Dp,
    firstFr: FocusRequester?,
    onFocus: (MediaItem) -> Unit,
    onClick: (MediaItem) -> Unit,
) {
    Column {
        Txt(title, T.title.copy(fontSize = 22.sp), Modifier.padding(start = Dim.SafeH, top = 10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dim.SafeH, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items.forEachIndexed { i, item ->
                MediaCard(
                    item, width, onFocus, { onClick(item) },
                    Modifier.rememberFocus(app, "home", item.key)
                        .then(if (i == 0 && firstFr != null) Modifier.focusRequester(firstFr) else Modifier),
                )
            }
        }
    }
}

/** Full-screen address entry. A real Dialog window, so focus can never leak to the screen behind. */
@Composable
fun AddressDialog(app: AppState) {
    var text by remember { mutableStateOf(TextFieldValue(app.addressInitial, TextRange(0, app.addressInitial.length))) }
    val fr = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        val q = text.text
        app.addressOpen = false
        app.openUrl(q)
    }
    LaunchedEffect(Unit) {
        runCatching { fr.requestFocus() }
        keyboard?.show()
    }
    val q = text.text.trim()
    val suggestions = if (q.length < 2) emptyList() else
        (app.store.favoriteItems() + app.store.continueItems(300))
            .distinctBy { it.url }
            .filter { it.url.contains(q, true) || it.title.contains(q, true) }
            .take(4)

    Dialog(onDismissRequest = { app.addressOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.padding(top = 64.dp).width(760.dp)) {
                Txt("Tìm kiếm hoặc nhập địa chỉ", T.title)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().height(68.dp)
                        .border(3.dp, Accent, RoundedCornerShape(34.dp))
                        .background(Surface, RoundedCornerShape(34.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = T.body.copy(fontSize = 24.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).focusRequester(fr),
                    )
                    if (text.text.isEmpty()) Txt("Ví dụ: youtube.com, hoặc phim hoạt hình", T.body.copy(fontSize = 24.sp), Modifier.padding(horizontal = 28.dp), TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                suggestions.forEach { s ->
                    FocusSurface({ app.addressOpen = false; app.openUrl(s.url) }, Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 6.dp).height(62.dp), focusScale = 1.02f) {
                        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                            SiteIcon(s.title, s.icon, 36.dp)
                            Column(Modifier.padding(start = 14.dp)) {
                                Txt(s.title, T.label)
                                Txt(hostOf(s.url), T.caption)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Mở", { submit() }, primary = true, icon = Icons.Rounded.Search)
                    if (app.voiceAvailable) TvButton("Giọng nói", { app.addressOpen = false; app.startVoice() }, icon = Icons.Rounded.Mic)
                    TvButton("Hủy", { app.addressOpen = false })
                }
            }
        }
    }
}
