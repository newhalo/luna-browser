package com.luna.browser.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tab as TabIcon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luna.browser.AppState
import com.luna.browser.R
import com.luna.browser.data.Suggest
import com.luna.browser.data.hostOf
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.SiteIcon
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Scrim
import com.luna.browser.ui.theme.Surface
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextSecondary

/** The text box shared by the address and find dialogs: a wide pill with a bright edge and a hint while empty. */
@Composable
private fun InputBox(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    hint: String,
    fr: FocusRequester,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    onSubmit: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(68.dp)
            .border(3.dp, Accent, RoundedCornerShape(34.dp))
            .background(Surface, RoundedCornerShape(34.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = T.body.copy(fontSize = 24.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }, onSearch = { onSubmit() }, onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).focusRequester(fr),
        )
        if (value.text.isEmpty()) Txt(hint, T.body.copy(fontSize = 24.sp), Modifier.padding(horizontal = 28.dp), TextSecondary)
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
    // Ranked on the TV from open tabs, favourites and history (a private tab skips the history); nothing is sent anywhere.
    val q = text.text.trim()
    val candidates = remember(app.tabs.count, app.store.favorites.size, app.store.history.size) { suggestionCandidates(app) }
    val suggestions = remember(q, candidates) { Suggest.rank(q, candidates, limit = 4) }

    Dialog(onDismissRequest = { app.addressOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.padding(top = 56.dp).width(760.dp)) {
                Txt(stringResource(R.string.addr_title), T.title)
                Spacer(Modifier.height(14.dp))
                InputBox(text, { text = it }, stringResource(R.string.addr_example), fr, ImeAction.Go, KeyboardType.Uri) { submit() }
                Spacer(Modifier.height(10.dp))
                suggestions.forEach { s ->
                    FocusSurface(
                        {
                            app.addressOpen = false
                            if (s.kind == Suggest.Kind.Tab) app.resume(s.url) else app.openUrl(s.url)
                        },
                        Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 6.dp).height(56.dp), focusScale = 1.02f,
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                            val host = hostOf(s.url)
                            SiteIcon(host, app.store.iconFor(host), 34.dp)
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Txt(s.title.ifBlank { host }, T.label)
                                Txt(host, T.caption)
                            }
                            // Where the suggestion comes from, as an icon with a word (never colour or icon alone).
                            val (icon, label) = when (s.kind) {
                                Suggest.Kind.Tab -> Icons.Rounded.TabIcon to stringResource(R.string.sug_tab)
                                Suggest.Kind.Favorite -> Icons.Rounded.Star to stringResource(R.string.sug_fav)
                                Suggest.Kind.History -> Icons.Rounded.History to stringResource(R.string.sug_history)
                            }
                            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Txt(" $label", T.caption, color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(stringResource(R.string.addr_open), { submit() }, primary = true, icon = Icons.Rounded.Search)
                    if (app.voiceAvailable) TvButton(stringResource(R.string.addr_voice), { app.addressOpen = false; app.startVoice() }, icon = Icons.Rounded.Mic)
                    TvButton(stringResource(R.string.addr_cancel), { app.addressOpen = false })
                }
            }
        }
    }
}

private fun suggestionCandidates(app: AppState): List<Suggest.Entry> {
    val st = app.store
    val inPrivate = app.tabs.current.isPrivate
    val tabs = app.tabs.tabs.filter { !it.isPrivate && it.url.startsWith("http") }
        .map { Suggest.Entry(it.title, it.url, Suggest.Kind.Tab, st.visitsOf(hostOf(it.url))) }
    val favs = st.favorites.map { Suggest.Entry(it.title, it.url, Suggest.Kind.Favorite, st.visitsOf(it.host)) }
    val hist = if (inPrivate) emptyList() else st.history.take(400).mapIndexed { i, h ->
        Suggest.Entry(h.title, h.url, Suggest.Kind.History, st.visitsOf(h.host), i)
    }
    return tabs + favs + hist
}

/** "Find on page": the word to look for; the matches are then stepped through with Up / Down on the page. */
@Composable
fun FindDialog(app: AppState) {
    val initial = app.browser.findQuery ?: ""
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val fr = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        val q = text.text.trim()
        app.findOpen = false
        if (q.isNotEmpty()) app.browser.find(q)
    }
    LaunchedEffect(Unit) {
        runCatching { fr.requestFocus() }
        keyboard?.show()
    }
    Dialog(onDismissRequest = { app.findOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.padding(top = 56.dp).width(760.dp)) {
                Txt(stringResource(R.string.find_title), T.title)
                Spacer(Modifier.height(14.dp))
                InputBox(text, { text = it }, stringResource(R.string.find_hint), fr, ImeAction.Search, KeyboardType.Text) { submit() }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(stringResource(R.string.find_go), { submit() }, primary = true, icon = Icons.Rounded.FindInPage)
                    TvButton(stringResource(R.string.addr_cancel), { app.findOpen = false })
                }
            }
        }
    }
}
