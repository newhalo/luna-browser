package com.luna.browser.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tab as TabIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.R
import com.luna.browser.browser.Tab
import com.luna.browser.browser.TabPolicy
import com.luna.browser.data.K
import com.luna.browser.data.MediaItem
import com.luna.browser.data.brandOf
import com.luna.browser.data.hostOf
import com.luna.browser.ui.components.Aurora
import com.luna.browser.ui.components.CardRow
import com.luna.browser.ui.components.Pill
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Glass
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextSecondary

/**
 * The tab switcher: every open tab as a 16:9 card (the same cards as Home), OK opens it, and the row of actions
 * below (reached with Down) closes the highlighted tab, closes the others, or opens a new tab.
 */
@Composable
fun TabsScreen(app: AppState) {
    val tabs = app.tabs
    val store = app.store
    val items = tabs.tabs.mapIndexed { i, t -> tabItem(app, i, t) }
    val requesters = remember(tabs.count) { List(tabs.count) { FocusRequester() } }
    var focused by remember { mutableIntStateOf(tabs.index) }
    val shown = focused.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    LaunchedEffect(tabs.count) {
        // On entering: the tab that is showing; after closing one: the card that took its place.
        runCatching { requesters.getOrNull(shown.coerceAtMost(requesters.lastIndex))?.requestFocus() }
    }
    val newLabel = stringResource(R.string.tabs_new)

    Box(Modifier.fillMaxSize()) {
        Aurora(animated = store.bool(K.ANIMATED_BG), dim = 0.55f)
        Column(Modifier.fillMaxSize().padding(vertical = Dim.SafeV)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dim.SafeH), verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.tabs_title), T.display.copy(fontSize = 36.sp))
                Spacer(Modifier.weight(1f))
                Pill("${tabs.count} / ${TabPolicy.MAX_TABS}")
            }
            // What the highlighted card is: its name, address and whether it is the one showing.
            Column(Modifier.height(112.dp).padding(horizontal = Dim.SafeH, vertical = 14.dp), verticalArrangement = Arrangement.Center) {
                val t = tabs.tabs.getOrNull(shown)
                Txt(
                    if (t == null || t.isEmpty) newLabel else t.title.ifBlank { hostOf(t.url) },
                    T.display.copy(fontSize = 32.sp), maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t != null && !t.isEmpty) Pill(hostOf(t.url))
                    if (t != null && t.isPrivate) Pill(stringResource(R.string.priv_badge))
                    if (shown == tabs.index) Pill(stringResource(R.string.tabs_current))
                    Txt(stringResource(R.string.tabs_hint), T.caption, color = TextSecondary)
                }
            }
            CardRow(
                app, null, items, 272.dp, requesters, null,
                onFocus = { item -> item.key.removePrefix("t:").toIntOrNull()?.let { focused = it } },
                focusScope = "tabs",
            ) { item -> item.key.removePrefix("t:").toIntOrNull()?.let { app.openTab(it) } }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = Dim.SafeH), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(stringResource(R.string.tabs_close), { app.closeTab(shown) }, icon = Icons.Rounded.Close, container = Glass)
                if (tabs.count > 1) TvButton(stringResource(R.string.tabs_close_others), { app.closeOtherTabs(shown) }, icon = Icons.Rounded.TabIcon, container = Glass)
                TvButton(newLabel, { app.newTab() }, icon = Icons.Rounded.Add, primary = true)
                TvButton(stringResource(R.string.priv_new_tab), { app.newPrivateTab() }, icon = Icons.Rounded.VisibilityOff, container = Glass)
            }
        }
    }
}

private fun tabItem(app: AppState, index: Int, t: Tab): MediaItem {
    val store = app.store
    val host = if (t.isEmpty) "" else hostOf(t.url)
    return MediaItem(
        key = "t:$index",
        title = if (t.isEmpty) app.s(R.string.tabs_new) else t.title.ifBlank { host },
        subtitle = if (t.isPrivate) app.s(R.string.priv_badge) + (if (host.isEmpty()) "" else " · $host") else host,
        url = t.url,
        art = if (t.isEmpty || t.isPrivate) null else store.tabThumb(t.url) ?: store.artForUrl(t.url) ?: store.artForHost(host),
        icon = if (host.isEmpty()) null else store.iconFor(host),
        brand = brandOf(host),
    )
}
