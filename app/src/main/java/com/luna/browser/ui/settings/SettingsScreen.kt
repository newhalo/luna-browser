package com.luna.browser.ui.settings

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.R
import com.luna.browser.data.FilterLists
import com.luna.browser.data.K
import com.luna.browser.data.ListStatus
import com.luna.browser.data.Languages
import com.luna.browser.data.searchEnginesFor
import com.luna.browser.ui.components.Aurora
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.Danger
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Glass
import com.luna.browser.ui.theme.Success
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private sealed interface SRow
private data class Toggle(val key: String, val title: String, val desc: String) : SRow
private data class Choice(val key: String, val title: String, val desc: String, val options: List<Pair<String, String>>) : SRow
private data class Action(val title: String, val desc: String, val value: String = "", val danger: Boolean = false, val confirm: Boolean = false, val run: () -> Unit) : SRow
private data class Info(val title: String, val value: String) : SRow

private class Cat(@StringRes val name: Int, val icon: ImageVector)

private val categories = listOf(
    Cat(R.string.set_cat_general, Icons.Rounded.Tune),
    Cat(R.string.set_cat_home, Icons.Rounded.Home),
    Cat(R.string.set_cat_browsing, Icons.Rounded.Mouse),
    Cat(R.string.set_cat_adblock, Icons.Rounded.Shield),
    Cat(R.string.set_cat_video, Icons.Rounded.Videocam),
    Cat(R.string.set_cat_phone, Icons.Rounded.PhoneAndroid),
    Cat(R.string.set_cat_privacy, Icons.Rounded.Lock),
    Cat(R.string.set_cat_about, Icons.Rounded.Info),
)

private const val AD_BLOCKING = 3
private const val PHONE = 5

@Composable
fun SettingsScreen(app: AppState) {
    var selected by remember { mutableIntStateOf(0) }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFr.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        Aurora(animated = app.store.bool(K.ANIMATED_BG), dim = 0.55f)
        Row(Modifier.fillMaxSize().padding(horizontal = Dim.SafeH, vertical = Dim.SafeV)) {
            Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 12.dp, bottom = 12.dp)) {
                Txt(stringResource(R.string.set_title), T.display.copy(fontSize = 36.sp), Modifier.padding(bottom = 10.dp))
                categories.forEachIndexed { i, c ->
                    CategoryItem(c, i == selected, if (i == 0) Modifier.focusRequester(firstFr) else Modifier) { selected = i }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0x99121622))
                    .verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                Txt(stringResource(categories[selected].name), T.title, Modifier.padding(start = 6.dp, bottom = 14.dp))
                val rows = rows(selected, app)
                var confirming by remember(selected) { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val onText = stringResource(R.string.set_on)
                val offText = stringResource(R.string.set_off)
                val pressAgain = stringResource(R.string.set_press_again)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { r ->
                        when (r) {
                            is Toggle -> {
                                val on = app.store.bool(r.key)
                                SettingRow(r.title, r.desc, if (on) onText else offText, on) { app.store.put(r.key, !on) }
                            }
                            is Choice -> {
                                val cur = r.options.indexOfFirst { it.first == app.store.str(r.key) }.coerceAtLeast(0)
                                SettingRow(r.title, r.desc, "‹  ${r.options[cur].second}  ›", false) {
                                    app.store.put(r.key, r.options[(cur + 1) % r.options.size].first)
                                }
                            }
                            is Action -> {
                                val armed = confirming == r.title
                                SettingRow(r.title, r.desc, if (armed) pressAgain else r.value, armed, danger = r.danger || armed) {
                                    if (r.confirm && !armed) {
                                        confirming = r.title
                                        app.toast(R.string.toast_confirm)
                                    } else {
                                        confirming = null
                                        r.run()
                                    }
                                }
                            }
                            is Info -> InfoRow(r.title, r.value)
                        }
                    }
                    if (selected == AD_BLOCKING) {
                        val ads = app.ads
                        Txt(stringResource(R.string.set_lists), T.label, Modifier.padding(start = 6.dp, top = 8.dp), color = TextSecondary)
                        FilterLists.forEach { l ->
                            val on = ads.isEnabled(l.id)
                            val st = ads.status[l.id] ?: ListStatus()
                            val meta = buildString {
                                append(app.s(l.description))
                                if (on && st.rules > 0) append(app.s(R.string.list_rules_n, "%,d".format(app.locale, st.rules)))
                                if (l.url != null && st.updatedAt > 0) {
                                    append(app.s(R.string.list_updated_n, DateFormat.getDateInstance(DateFormat.SHORT, app.locale).format(Date(st.updatedAt))))
                                }
                                if (l.url != null && on && st.updatedAt == 0L) append(app.s(R.string.list_not_downloaded))
                                if (st.failed) append(app.s(R.string.list_failed))
                            }
                            SettingRow(app.s(l.name), meta, if (on) onText else offText, on, danger = st.failed) { ads.setEnabled(l.id, !on) }
                        }
                        val status = when {
                            ads.updating -> app.s(R.string.st_updating)
                            ads.updateFailed -> app.s(R.string.st_failed)
                            ads.lastUpdated == 0L -> app.s(R.string.st_never)
                            else -> app.s(R.string.st_updated, DateFormat.getDateInstance(DateFormat.MEDIUM, app.locale).format(Date(ads.lastUpdated)))
                        }
                        SettingRow(
                            app.s(R.string.set_update),
                            app.s(if (ads.updateFailed) R.string.set_update_fail_d else R.string.set_update_d),
                            status, false, danger = ads.updateFailed,
                        ) {
                            scope.launch {
                                val ok = ads.update()
                                app.toast(if (ok) R.string.toast_filters_ok else R.string.toast_filters_fail)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(c: Cat, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    // Moving focus onto a category shows its settings immediately — no extra click.
    FocusSurface(
        onClick = onFocus,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp).height(52.dp).onFocusChanged { if (it.isFocused) onFocus() },
        container = if (selected) Accent.copy(alpha = 0.22f) else Glass,
        focusScale = 1.03f,
    ) {
        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon, null, tint = if (selected) Accent else TextPrimary, modifier = Modifier.size(24.dp))
            Txt("  " + stringResource(c.name), T.label)
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, value: String, on: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick, Modifier.fillMaxWidth().heightIn(min = Dim.RowHeight), container = Glass, focusScale = 1.02f) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, T.body)
                Txt(desc, T.caption, maxLines = 2)
            }
            if (value.isNotEmpty()) {
                Box(
                    Modifier.padding(start = 16.dp).background(if (on) Success.copy(alpha = .16f) else Color(0x14FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Txt(value, T.label, color = if (danger) Danger else if (on) Success else TextSecondary) }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, T.body, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Txt(value, T.body, color = TextSecondary, maxLines = 2)
    }
}

private fun rows(category: Int, app: AppState): List<SRow> {
    val s = app.store
    return when (category) {
        0 -> {
            // The stored engine is always offered, even if the current language doesn't list it.
            val engines = (searchEnginesFor(app.lang) + s.str(K.ENGINE)).distinct()
            listOf(
                Choice(K.LANGUAGE, app.s(R.string.set_language), app.s(R.string.set_language_d), listOf("auto" to app.s(R.string.lang_auto)) + Languages),
                Choice(K.ENGINE, app.s(R.string.set_engine), app.s(R.string.set_engine_d), engines.map { it to it }),
                Toggle(K.ANIMATED_BG, app.s(R.string.set_anim_bg), app.s(R.string.set_anim_bg_d)),
                Toggle(K.REDUCE_MOTION, app.s(R.string.set_reduce), app.s(R.string.set_reduce_d)),
            )
        }
        1 -> listOf(
            Choice(K.LAUNCH, app.s(R.string.set_launch), app.s(R.string.set_launch_d), listOf("home" to app.s(R.string.launch_home), "resume" to app.s(R.string.launch_resume))),
            Toggle(K.SHOW_CONTINUE, app.s(R.string.set_continue), app.s(R.string.set_continue_d)),
        )
        2 -> listOf(
            Toggle(K.CURSOR_START, app.s(R.string.set_cursor_start), app.s(R.string.set_cursor_start_d)),
            Choice(K.CURSOR_SPEED, app.s(R.string.set_cursor_speed), app.s(R.string.set_cursor_speed_d), (1..5).map { it.toString() to app.s(R.string.level_n, it) }),
            Toggle(K.DESKTOP, app.s(R.string.set_desktop), app.s(R.string.set_desktop_d)),
            Choice(K.ZOOM, app.s(R.string.set_zoom), app.s(R.string.set_zoom_d), listOf("100", "125", "150", "175").map { it to "$it%" }),
        )
        AD_BLOCKING -> listOf(
            Toggle(K.ADBLOCK, app.s(R.string.set_adblock), app.s(R.string.set_adblock_d)),
            Toggle(K.BLOCK_POPUPS, app.s(R.string.set_popups), app.s(R.string.set_popups_d)),
            Info(app.s(R.string.set_blocked), app.s(R.string.set_blocked_n, app.ads.blockedCount)),
            Info(app.s(R.string.set_rules), "%,d".format(app.locale, app.ads.listSize)),
        )
        4 -> listOf(
            Choice(K.SEEK_STEP, app.s(R.string.set_seek), app.s(R.string.set_seek_d), listOf("5", "10", "15", "30").map { it to app.s(R.string.sec_n, it.toInt()) }),
            Info(app.s(R.string.info_ok), app.s(R.string.info_ok_v)),
            Info(app.s(R.string.info_lr), app.s(R.string.info_lr_v)),
            Info(app.s(R.string.info_back), app.s(R.string.info_back_v)),
            Info(app.s(R.string.info_pp), app.s(R.string.info_pp_v)),
        )
        PHONE -> listOf(
            Toggle(K.PHONE, app.s(R.string.set_phone), app.s(R.string.set_phone_d)),
            Info(app.s(R.string.set_phone_addr), app.phoneAddress() ?: app.s(R.string.set_phone_none)),
            Info(app.s(R.string.set_phone_pin), s.str(K.PHONE_PIN)),
            Action(app.s(R.string.set_phone_newpin), app.s(R.string.set_phone_newpin_d)) {
                s.newPin(); app.toast(R.string.toast_pin_changed)
            },
        )
        6 -> listOf(
            Toggle(K.THIRD_PARTY, app.s(R.string.set_3p), app.s(R.string.set_3p_d)),
            Action(app.s(R.string.set_clear_hist), app.s(R.string.set_clear_hist_d), danger = true, confirm = true) {
                s.clearHistory(); app.toast(R.string.toast_history_cleared)
            },
            Action(app.s(R.string.set_clear_cookies), app.s(R.string.set_clear_cookies_d), danger = true, confirm = true) {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                app.toast(R.string.toast_cookies_cleared)
            },
            Action(app.s(R.string.set_clear_cache), app.s(R.string.set_clear_cache_d)) {
                app.browser.webView.clearCache(true); app.toast(R.string.toast_cache_cleared)
            },
        )
        else -> {
            val version = runCatching { app.activity.packageManager.getPackageInfo(app.activity.packageName, 0).versionName }.getOrNull() ?: "?"
            listOf(
                Info(app.s(R.string.about_luna), app.s(R.string.about_version, version)),
                Info(app.s(R.string.about_engine), runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: app.s(R.string.about_unknown)),
                Info(app.s(R.string.about_device), "${Build.MANUFACTURER} ${Build.MODEL}"),
                Info(app.s(R.string.about_menu), app.s(R.string.about_menu_v)),
                Info(app.s(R.string.about_hold_ok), app.s(R.string.about_hold_ok_v)),
                Info(app.s(R.string.about_hold_back), app.s(R.string.about_hold_back_v)),
            )
        }
    }
}
