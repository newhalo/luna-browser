package com.luna.browser.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.data.Site
import com.luna.browser.ui.components.EmptyState
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.LetterTile
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.components.rememberFocus
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.T
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(app: AppState) {
    val items = app.store.history
    val homeFr = remember { FocusRequester() }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) runCatching { homeFr.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = Dim.SafeH, vertical = Dim.SafeV)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt("History", T.display.copy(fontSize = 36.sp), Modifier.weight(1f))
            if (items.isNotEmpty()) {
                TvButton(if (confirm) "Press OK again to clear" else "Clear history", {
                    if (confirm) { app.store.clearHistory(); confirm = false; app.toast("History cleared") } else confirm = true
                }, icon = Icons.Rounded.Delete)
            }
        }
        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.History, "No history yet",
                "Websites you visit will appear here so you can get back to them quickly.",
                "Go to Home", { app.goHome() }, Modifier.weight(1f), homeFr,
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { i, h ->
                    FocusSurface(
                        { app.openUrl(h.url) },
                        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp).rememberFocus(app, "history", "h$i"),
                        focusScale = 1.02f,
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                            LetterTile(h.title, 44.dp)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Txt(h.title, T.body)
                                Txt(Site(h.title, h.url).host, T.caption)
                            }
                            Txt(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.time)), T.caption)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(app: AppState) {
    val items = app.store.favorites
    val homeFr = remember { FocusRequester() }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) runCatching { homeFr.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = Dim.SafeH, vertical = Dim.SafeV)) {
        Txt("Favorites", T.display.copy(fontSize = 36.sp))
        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.Star, "No favorites yet",
                "While browsing, press Menu and choose Save to keep a website here.",
                "Go to Home", { app.goHome() }, Modifier.weight(1f), homeFr,
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.toList().forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusSurface({ app.openUrl(s.url) }, Modifier.weight(1f).height(72.dp).rememberFocus(app, "fav", "o$i"), focusScale = 1.02f) {
                            Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                                LetterTile(s.title, 44.dp)
                                Column(Modifier.padding(start = 16.dp)) {
                                    Txt(s.title, T.body)
                                    Txt(s.host, T.caption)
                                }
                            }
                        }
                        TvButton("Remove", { app.store.removeFavorite(s); app.toast("Removed from Favorites") }, Modifier.rememberFocus(app, "fav", "x$i"), Icons.Rounded.Close)
                    }
                }
            }
        }
    }
}
