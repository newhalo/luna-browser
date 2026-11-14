package com.luna.browser.ui.home

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luna.browser.AppState
import com.luna.browser.Screen
import com.luna.browser.data.K
import com.luna.browser.data.QuickSites
import com.luna.browser.data.Site
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.LetterTile
import com.luna.browser.ui.components.MoonLogo
import com.luna.browser.ui.components.SectionTitle
import com.luna.browser.ui.components.SiteCard
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.components.rememberFocus
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Danger
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Scrim
import com.luna.browser.ui.theme.Surface
import com.luna.browser.ui.theme.SurfaceHigh
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(app: AppState) {
    val store = app.store
    val searchFr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (app.focusMemory["home"] == null) runCatching { searchFr.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dim.SafeH, vertical = Dim.SafeV),
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MoonLogo(Modifier.size(44.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Txt("Luna", T.title)
                if (!app.online) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WifiOff, null, tint = Danger, modifier = Modifier.size(18.dp))
                        Txt("  No internet connection", T.caption, color = Danger)
                    }
                } else Txt("Browse the web from your sofa", T.caption)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                TvButton("History", { app.navigate(Screen.History) }, Modifier.rememberFocus(app, "home", "nav:history"), Icons.Rounded.History)
                TvButton("Favorites", { app.navigate(Screen.Favorites) }, Modifier.rememberFocus(app, "home", "nav:fav"), Icons.Rounded.Star)
                TvButton("Settings", { app.navigate(Screen.Settings) }, Modifier.rememberFocus(app, "home", "nav:settings"), Icons.Rounded.Settings)
            }
        }

        // Search
        Spacer(Modifier.height(12.dp))
        FocusSurface(
            onClick = { app.openAddress() },
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 4.dp)
                .rememberFocus(app, "home", "search").focusRequester(searchFr),
            shape = RoundedCornerShape(34.dp),
            container = SurfaceHigh,
            focusScale = 1.02f,
        ) {
            Row(Modifier.padding(horizontal = 28.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Accent, modifier = Modifier.size(30.dp))
                Txt("  Search or type a website address", T.body.copy(fontSize = 22.sp), color = TextSecondary)
            }
        }

        // Quick access
        SectionTitle("Quick access")
        SiteRow {
            QuickSites.forEach { s -> SiteCard(s.title, s.host, { app.openUrl(s.url) }, Modifier.rememberFocus(app, "home", "q:${s.url}")) }
        }

        // Continue browsing
        val last = store.lastSession
        if (store.bool(K.SHOW_CONTINUE) && last != null) {
            SectionTitle("Continue browsing")
            Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                ContinueCard(last, Modifier.rememberFocus(app, "home", "continue")) { app.resume(last.url) }
            }
        }

        // Favorites
        if (store.favorites.isNotEmpty()) {
            SectionTitle("Favorites")
            SiteRow {
                store.favorites.take(8).forEach { s -> SiteCard(s.title, s.host, { app.openUrl(s.url) }, Modifier.rememberFocus(app, "home", "f:${s.url}")) }
            }
        }

        // Recent
        val recent = store.history.filter { h -> h.url != last?.url }.take(8)
        if (store.bool(K.SHOW_RECENT) && recent.isNotEmpty()) {
            SectionTitle("Recently visited")
            SiteRow {
                recent.forEach { h -> SiteCard(h.title, Site(h.title, h.url).host, { app.openUrl(h.url) }, Modifier.rememberFocus(app, "home", "r:${h.url}")) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SiteRow(content: @Composable () -> Unit) {
    // Inner padding leaves room for the focus scale so the focused card is never clipped.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Dim.Gap),
    ) { content() }
}

@Composable
private fun ContinueCard(site: Site, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick, modifier.width(520.dp).height(96.dp)) {
        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            LetterTile(site.title, 56.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Txt(site.title, T.body)
                Txt(site.host, T.caption)
            }
        }
    }
}

/** Full-screen address entry. Uses a real Dialog window so focus can never leak to the screen behind. */
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
        (app.store.favorites.map { Site(it.title, it.url) } + app.store.history.map { Site(it.title, it.url) })
            .distinctBy { it.url }
            .filter { it.url.contains(q, true) || it.title.contains(q, true) }
            .take(4)

    Dialog(onDismissRequest = { app.addressOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.padding(top = 72.dp).width(720.dp)) {
                Txt("Search or enter a website address", T.title)
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
                    if (text.text.isEmpty()) Txt("  youtube.com, or what you want to find", T.body.copy(fontSize = 24.sp), Modifier.padding(horizontal = 28.dp), TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                suggestions.forEach { s ->
                    FocusSurface({ app.addressOpen = false; app.openUrl(s.url) }, Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 6.dp).height(60.dp)) {
                        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                            LetterTile(s.title, 36.dp)
                            Column(Modifier.padding(start = 14.dp)) {
                                Txt(s.title, T.label)
                                Txt(s.host, T.caption.copy(fontSize = 14.sp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Go", { submit() }, primary = true, icon = Icons.Rounded.Search)
                    TvButton("Cancel", { app.addressOpen = false })
                }
            }
        }
    }
}
