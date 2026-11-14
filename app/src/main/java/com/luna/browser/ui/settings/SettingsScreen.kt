package com.luna.browser.ui.settings

import android.os.Build
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.data.AdBlocker
import com.luna.browser.data.K
import com.luna.browser.data.SearchEngines
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Danger
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Success
import com.luna.browser.ui.theme.Surface
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private sealed interface SRow
private data class Toggle(val key: String, val title: String, val desc: String) : SRow
private data class Choice(val key: String, val title: String, val desc: String, val options: List<Pair<String, String>>) : SRow
private data class Action(val title: String, val desc: String, val value: String = "", val danger: Boolean = false, val confirm: Boolean = false, val run: () -> Unit) : SRow
private data class Info(val title: String, val value: String) : SRow

private class Cat(val name: String, val icon: ImageVector)

private val categories = listOf(
    Cat("General", Icons.Rounded.Tune),
    Cat("Homepage", Icons.Rounded.Home),
    Cat("Browsing", Icons.Rounded.Mouse),
    Cat("Ad Blocking", Icons.Rounded.Shield),
    Cat("Video", Icons.Rounded.Videocam),
    Cat("Privacy", Icons.Rounded.Lock),
    Cat("About", Icons.Rounded.Info),
)

@Composable
fun SettingsScreen(app: AppState) {
    var selected by remember { mutableStateOf(0) }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFr.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = Dim.SafeH, vertical = Dim.SafeV)) {
        Column(Modifier.width(260.dp).fillMaxHeight().padding(end = 8.dp)) {
            Txt("Settings", T.display.copy(fontSize = 36.sp), Modifier.padding(bottom = 16.dp))
            categories.forEachIndexed { i, c ->
                CategoryItem(c, i == selected, Modifier.then(if (i == 0) Modifier.focusRequester(firstFr) else Modifier)) { selected = i }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 52.dp, bottom = 16.dp, end = 8.dp)) {
            val rows = rows(categories[selected].name, app)
            var confirming by remember(selected) { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { r ->
                    when (r) {
                        is Toggle -> SettingRow(r.title, r.desc, if (app.store.bool(r.key)) "✓  On" else "Off", app.store.bool(r.key)) {
                            app.store.put(r.key, !app.store.bool(r.key))
                        }
                        is Choice -> {
                            val cur = r.options.indexOfFirst { it.first == app.store.str(r.key) }.coerceAtLeast(0)
                            SettingRow(r.title, r.desc, "‹  ${r.options[cur].second}  ›", false) {
                                app.store.put(r.key, r.options[(cur + 1) % r.options.size].first)
                            }
                        }
                        is Action -> {
                            val armed = confirming == r.title
                            SettingRow(r.title, r.desc, if (armed) "Press OK again" else r.value, armed, danger = r.danger) {
                                if (r.confirm && !armed) {
                                    confirming = r.title
                                    app.toast("Press OK again to confirm")
                                } else {
                                    confirming = null
                                    r.run()
                                }
                            }
                        }
                        is Info -> InfoRow(r.title, r.value)
                    }
                }
            }
            if (categories[selected].name == "Ad Blocking") {
                // Update action needs a coroutine scope, so it lives here rather than in the pure row list.
                Box(Modifier.height(12.dp))
                val ads = app.ads
                val status = when {
                    ads.updating -> "Updating…"
                    ads.updateFailed -> "Update failed"
                    ads.lastUpdated == 0L -> "Built-in list"
                    else -> "Updated " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ads.lastUpdated))
                }
                SettingRow(
                    "Update filter lists",
                    if (ads.updateFailed) "Couldn't download the list. Check your internet connection, then try again." else "Download the latest list of ad and tracker websites to block.",
                    status, false, danger = ads.updateFailed,
                ) {
                    scope.launch {
                        val ok = ads.update()
                        app.toast(if (ok) "Filter lists updated" else "Couldn't update filter lists. Try again later.")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(c: Cat, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    // Moving focus onto a category immediately shows its settings (no extra click needed).
    FocusSurface(
        onClick = onFocus,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).height(56.dp).onFocusChanged { if (it.isFocused) onFocus() },
        container = if (selected) Accent.copy(alpha = 0.18f) else Surface,
        focusScale = 1.03f,
    ) {
        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon, null, tint = if (selected) Accent else com.luna.browser.ui.theme.TextPrimary, modifier = Modifier.size(24.dp))
            Txt("  " + c.name, T.label)
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, value: String, on: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick, Modifier.fillMaxWidth().heightIn(min = Dim.RowHeight), focusScale = 1.02f) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, T.body)
                Txt(desc, T.caption, maxLines = 2)
            }
            if (value.isNotEmpty()) {
                Box(
                    Modifier.padding(start = 16.dp).background(if (on) Success.copy(alpha = .16f) else Color0, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Txt(value, T.label, color = if (danger) Danger else if (on) Success else TextSecondary) }
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0x14FFFFFF)

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, T.body, Modifier.weight(1f))
        Txt(value, T.body, color = TextSecondary, maxLines = 2)
    }
}

private fun rows(cat: String, app: AppState): List<SRow> {
    val s = app.store
    return when (cat) {
        "General" -> listOf(
            Choice(K.ENGINE, "Search engine", "Used when you type words instead of a website address.", SearchEngines.keys.map { it to it }),
            Toggle(K.REDUCE_MOTION, "Reduce motion", "Use simpler, faster animations. Helps on slower TVs."),
        )
        "Homepage" -> listOf(
            Choice(K.LAUNCH, "When Luna opens", "Choose what you see first.", listOf("home" to "Home screen", "resume" to "Last page")),
            Toggle(K.SHOW_CONTINUE, "Show “Continue browsing”", "A shortcut back to the page you were on."),
            Toggle(K.SHOW_RECENT, "Show recently visited", "Quick links to sites you opened lately."),
        )
        "Browsing" -> listOf(
            Toggle(K.CURSOR_START, "Start in cursor mode", "Pages open with an on-screen pointer. Turn off to jump between links with the arrows."),
            Choice(K.CURSOR_SPEED, "Pointer speed", "How fast the pointer moves when you hold an arrow.", (1..5).map { it.toString() to it.toString() }),
            Toggle(K.DESKTOP, "Request desktop websites", "Shows full websites, which fit a big screen better."),
            Choice(K.ZOOM, "Page size", "Make text on websites larger so it reads from the sofa.", listOf("100", "125", "150", "175").map { it to "$it%" }),
        )
        "Ad Blocking" -> listOf(
            Toggle(K.ADBLOCK, "Block ads and trackers", "Stops known advertising websites from loading."),
            Toggle(K.BLOCK_POPUPS, "Block pop-ups", "Stops pages from opening new windows by themselves."),
            Info("Ads and trackers blocked", app.ads.blockedCount.toString()),
            Info("Websites on the list", app.ads.listSize.toString()),
        )
        "Video" -> listOf(
            Choice(K.SEEK_STEP, "Skip length", "How far Left and Right jump while watching a video.", listOf("5", "10", "15", "30").map { it to "$it seconds" }),
            Info("OK", "Show controls, then Play / Pause"),
            Info("Left / Right", "Skip backward / forward"),
            Info("Back", "Hide controls, then leave fullscreen"),
            Info("Play / Pause key", "Play or pause any video"),
        )
        "Privacy" -> listOf(
            Toggle(K.THIRD_PARTY, "Allow third-party cookies", "Some sites need these to sign in or play video."),
            Action("Clear browsing history", "Removes the list of sites you visited.", danger = true, confirm = true) {
                s.clearHistory(); app.toast("History cleared")
            },
            Action("Clear cookies and site data", "Signs you out of websites.", danger = true, confirm = true) {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                app.toast("Cookies and site data cleared")
            },
            Action("Clear cache", "Frees storage. Pages may load more slowly once.", confirm = false) {
                app.browser.webView.clearCache(true); app.toast("Cache cleared")
            },
        )
        else -> listOf(
            Info("Luna Browser", "Version 0.1.0"),
            Info("Web engine", runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "Unknown"),
            Info("Device", "${Build.MANUFACTURER} ${Build.MODEL}"),
            Info("Menu", "Show or hide the toolbar"),
            Info("Hold OK", "Switch cursor mode / focus mode"),
            Info("Hold Back", "Open the toolbar"),
        )
    }
}
