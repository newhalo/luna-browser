package com.luna.browser.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tab as TabIcon
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.luna.browser.R
import com.luna.browser.Screen
import com.luna.browser.data.K
import com.luna.browser.data.MediaItem
import com.luna.browser.data.TileSize
import com.luna.browser.data.hostOf
import com.luna.browser.ui.components.Backdrop
import com.luna.browser.ui.components.CardRow
import com.luna.browser.ui.components.LightCrossfade
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
import com.luna.browser.ui.profiles.ProfileChip
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
    // Read only inside Backdrop / HeroInfo, so moving focus doesn't recompose the whole screen.
    val hero = remember { mutableStateOf<MediaItem?>(null) }
    val searchFr = remember { FocusRequester() }
    val firstCardFr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (app.focusMemory["home"] == null || app.focusMemory["home"] == "search") runCatching { searchFr.requestFocus() }
    }

    // Rows are rebuilt only when history / favourites / artwork actually change.
    val showContinue = store.bool(K.SHOW_CONTINUE)
    val lang = app.lang
    val tileScale = TileSize.scale(store.str(K.TILE_SIZE))
    val inPrivate = app.tabs.current.isPrivate   // a private tab does not show what was watched before
    val rows = remember(store.version, showContinue, lang, tileScale, inPrivate) {
        buildList {
            val cont = if (showContinue && !inPrivate) store.continueItems() else emptyList()
            if (cont.isNotEmpty()) add(Triple(R.string.home_continue, cont, 272.dp * tileScale))
            add(Triple(R.string.home_your_sites, store.siteItems(lang), 216.dp * tileScale))
            val favs = store.favoriteItems()
            if (favs.isNotEmpty()) add(Triple(R.string.home_favorites, favs, 216.dp * tileScale))
        }
    }
    val onCardFocus = remember { { item: MediaItem -> hero.value = item } }
    // One requester per card: UP from a card goes to the card above it. The default focus search would
    // skip the (scrolled out of view) row above and land on the search bar.
    val requesters = remember(rows) {
        rows.mapIndexed { r, row -> List(row.second.size) { i -> if (r == 0 && i == 0) firstCardFr else FocusRequester() } }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop({ hero.value?.art }, animated = store.bool(K.ANIMATED_BG))
        Column(Modifier.fillMaxSize().padding(top = Dim.SafeV)) {
            Header(app)
            HeroInfo(app, { hero.value }, app.online)
            val scope = rememberCoroutineScope()
            val rowY = remember { mutableMapOf<Int, Int>() }
            // The rows are not in a scroll container: Compose's "scroll the focused child into view" would
            // fight the row snap below and leave rows half-scrolled. Instead the focused row is slid to the
            // top of this area with a plain translation (focus search still sees the real layout).
            val pivot = remember { Animatable(0f) }
            val pivotMs = ms(260)
            SearchRow(app, searchFr, firstCardFr) {
                hero.value = null
                scope.launch { pivot.animateTo(0f, tween(pivotMs)) }   // back to the first row when leaving the rows
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer { translationY = -pivot.value }
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rows.forEachIndexed { r, (title, items, width) ->
                        // Netflix-style pivot: the focused row always snaps to the top of the rows area.
                        Box(
                            Modifier
                                .onGloballyPositioned { rowY[r] = it.positionInParent().y.toInt() }
                                .onFocusChanged { f ->
                                    if (f.hasFocus) scope.launch { pivot.animateTo((rowY[r] ?: 0).toFloat(), tween(pivotMs)) }
                                },
                        ) {
                            CardRow(app, stringResource(title), items, width, requesters[r], requesters.getOrNull(r - 1), onFocus = onCardFocus) { app.resume(it.url) }
                        }
                    }
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
            if (app.store.profiles.size > 1 || app.store.isGuest) ProfileChip(app, Modifier.rememberFocus(app, "home", "nav:profile"))
            if (app.tabs.hasContent) {
                TvButton(stringResource(R.string.tb_tabs), { app.navigate(Screen.Tabs) }, Modifier.rememberFocus(app, "home", "nav:tabs"), Icons.Rounded.TabIcon, container = Glass)
            }
            TvButton(stringResource(R.string.nav_history), { app.navigate(Screen.History) }, Modifier.rememberFocus(app, "home", "nav:history"), Icons.Rounded.History, container = Glass)
            TvButton(stringResource(R.string.nav_favorites), { app.navigate(Screen.Favorites) }, Modifier.rememberFocus(app, "home", "nav:fav"), Icons.Rounded.Star, container = Glass)
            TvButton(stringResource(R.string.nav_settings), { app.navigate(Screen.Settings) }, Modifier.rememberFocus(app, "home", "nav:settings"), Icons.Rounded.Settings, container = Glass)
            Txt(now, T.title.copy(fontSize = 26.sp, fontWeight = FontWeight.Light), Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun HeroInfo(app: AppState, hero: () -> MediaItem?, online: Boolean) {
    Box(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = Dim.SafeH), contentAlignment = Alignment.BottomStart) {
        LightCrossfade(hero(), ms(220)) { h ->
            Column(Modifier.width(640.dp)) {
                if (h == null) {
                    val who = if (app.store.profiles.size > 1 && !app.store.isGuest) ", " + app.profileLabel(app.store.currentProfile) else ""
                    Txt(greeting(app) + who, T.display.copy(fontSize = 42.sp), maxLines = 1)
                    if (!online) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WifiOff, null, tint = Danger, modifier = Modifier.size(22.dp))
                            Txt("  " + stringResource(R.string.home_offline), T.body, color = Danger)
                        }
                    } else {
                        val upd = app.updateAvailable
                        val oldEngine = com.luna.browser.data.WebViewCheck.needsAttention(app.webViewLevel)
                        if (upd != null) Txt(stringResource(R.string.upd_home, upd), T.body.copy(fontSize = 22.sp), color = Accent)
                        else if (oldEngine) Txt(stringResource(R.string.web_home), T.body.copy(fontSize = 22.sp), color = Danger)
                        else if (app.tabs.current.isPrivate) Txt(stringResource(R.string.priv_hint), T.body.copy(fontSize = 22.sp), color = Accent)
                        else if (app.store.isGuest) Txt(stringResource(R.string.prof_guest_hint), T.body.copy(fontSize = 22.sp), color = Accent)
                        else Txt(stringResource(R.string.home_prompt), T.body.copy(fontSize = 22.sp), color = TextSecondary)
                    }
                } else {
                    Txt(h.title, T.display.copy(fontSize = 38.sp), maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(h.subtitle)
                        if (h.time > 0) Pill(timeAgo(app, h.time))
                        Txt(stringResource(R.string.home_press_ok), T.caption, color = TextSecondary)
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
            modifier = Modifier.width(if (app.voiceAvailable) 430.dp else 560.dp).height(58.dp)
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
                Txt("  " + stringResource(R.string.home_search_hint), T.body, color = TextSecondary)
            }
        }
        if (app.voiceAvailable) {
            TvButton(
                stringResource(R.string.home_voice), { app.startVoice() },
                Modifier.height(58.dp).focusProperties { down = firstCardFr }.onFocusChanged { if (it.isFocused) onFocus() },
                Icons.Rounded.Mic, container = Glass,
            )
        }
        TvButton(
            stringResource(R.string.priv_btn), { app.newPrivateTab() },
            Modifier.height(58.dp).rememberFocus(app, "home", "search:private").focusProperties { down = firstCardFr }.onFocusChanged { if (it.isFocused) onFocus() },
            Icons.Rounded.VisibilityOff, container = Glass,
        )
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
        (app.store.favoriteItems() + if (app.tabs.current.isPrivate) emptyList() else app.store.continueItems(300))
            .distinctBy { it.url }
            .filter { it.url.contains(q, true) || it.title.contains(q, true) }
            .take(4)

    Dialog(onDismissRequest = { app.addressOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.padding(top = 64.dp).width(760.dp)) {
                Txt(stringResource(R.string.addr_title), T.title)
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
                    if (text.text.isEmpty()) Txt(stringResource(R.string.addr_example), T.body.copy(fontSize = 24.sp), Modifier.padding(horizontal = 28.dp), TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                suggestions.forEach { s ->
                    FocusSurface({ app.addressOpen = false; app.openUrl(s.url) }, Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 6.dp).height(62.dp), focusScale = 1.02f) {
                        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                            SiteIcon(s.subtitle, s.icon, 36.dp)
                            Column(Modifier.padding(start = 14.dp)) {
                                Txt(s.title, T.label)
                                Txt(hostOf(s.url), T.caption)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(stringResource(R.string.addr_open), { submit() }, primary = true, icon = Icons.Rounded.Search)
                    if (app.voiceAvailable) TvButton(stringResource(R.string.addr_voice), { app.addressOpen = false; app.startVoice() }, icon = Icons.Rounded.Mic)
                    TvButton(stringResource(R.string.addr_cancel), { app.addressOpen = false })
                }
            }
        }
    }
}
