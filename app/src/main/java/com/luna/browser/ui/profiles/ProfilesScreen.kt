package com.luna.browser.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luna.browser.AppState
import com.luna.browser.R
import com.luna.browser.Screen
import com.luna.browser.browser.WebProfiles
import com.luna.browser.data.K
import com.luna.browser.data.Profile
import com.luna.browser.data.ProfileRules
import com.luna.browser.ui.components.Aurora
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.SettingRow
import com.luna.browser.ui.components.TvButton
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.Glass
import com.luna.browser.ui.theme.Outline
import com.luna.browser.ui.theme.Scrim
import com.luna.browser.ui.theme.Surface
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary

private val Tile = 104.dp

/** A PIN entry the screen is waiting for: [verifyId] set = check that profile's PIN, null = choose a new one. */
private class PinTask(val title: String, val verifyId: String?, val onOk: (String) -> Unit)

private fun gradientOf(p: Profile?): Pair<Color, Color> {
    if (p == null) return Color(0xFF5B617D) to Color(0xFF2B3046)
    val base = Color(ProfileRules.colors[p.color.coerceIn(0, ProfileRules.colors.size - 1)])
    return base to Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.75f, 1f)
}

/** A round avatar: the profile's colour with its initial; a person icon for the guest ([profile] null). */
@Composable
fun Avatar(profile: Profile?, label: String, size: Dp, modifier: Modifier = Modifier) {
    val (a, b) = gradientOf(profile)
    Box(modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        if (profile == null) {
            Icon(Icons.Rounded.Person, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(size * 0.54f))
        } else {
            Txt(ProfileRules.initial(label), T.display.copy(fontSize = (size.value * 0.44f).sp, color = Color(0xFF0B0D12)))
        }
    }
}

/** The round profile button at the top of Home: the current profile's avatar; OK opens "Who's watching?". */
@Composable
fun ProfileChip(app: AppState, modifier: Modifier = Modifier) {
    val store = app.store
    val p = store.currentProfile
    val guest = store.isGuest
    val label = if (guest) stringResource(R.string.prof_guest) else app.profileLabel(p)
    FocusSurface(
        onClick = { app.navigate(Screen.Profiles) },
        modifier = modifier.size(52.dp).semantics { contentDescription = label },
        shape = CircleShape, container = Glass, focusScale = 1.12f,
    ) {
        Avatar(if (guest) null else p, label, 52.dp)
    }
}

@Composable
fun ProfilesScreen(app: AppState) {
    val store = app.store
    val profiles = store.profiles
    var manage by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<String?>(null) }     // "" = a new profile; otherwise the id being renamed
    var pinTask by remember { mutableStateOf<PinTask?>(null) }
    val locked = app.locked

    /** Runs [then] after the PIN of [p] was entered; at once when it has none. */
    fun withPin(p: Profile, then: () -> Unit) {
        if (!store.hasPin(p.id)) { then(); return }
        pinTask = PinTask(app.s(R.string.prof_pin_for, app.profileLabel(p)), p.id) { pinTask = null; then() }
    }

    val requesters = remember(profiles.size) { List(profiles.size) { FocusRequester() } }
    LaunchedEffect(Unit) {
        val i = profiles.indexOfFirst { it.id == store.profileId }.coerceAtLeast(0)
        runCatching { requesters.getOrNull(i)?.requestFocus() }
    }
    // Back closes the edit panel before it leaves the screen (the name dialog is a window of its own and handles Back itself).
    DisposableEffect(editId) {
        app.closeOverlay = if (editId != null) ({ editId = null; true }) else null
        onDispose { app.closeOverlay = null }
    }

    Box(Modifier.fillMaxSize()) {
        Aurora(animated = store.bool(K.ANIMATED_BG), dim = 0.55f)
        Column(Modifier.fillMaxSize().padding(horizontal = Dim.SafeH, vertical = Dim.SafeV), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Txt(stringResource(if (manage) R.string.prof_manage_title else R.string.prof_title), T.display, Modifier.fillMaxWidth(), align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Txt(
                stringResource(if (locked) R.string.prof_locked_hint else if (manage) R.string.prof_sub_manage else R.string.prof_sub), T.body, Modifier.fillMaxWidth(),
                color = TextSecondary, align = TextAlign.Center,
            )
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                profiles.forEachIndexed { i, p ->
                    val label = app.profileLabel(p)
                    ProfileTile(
                        label = label, current = p.id == store.profileId, edit = manage, requester = requesters[i],
                        locked = store.hasPin(p.id),
                        onClick = {
                            when {
                                manage -> withPin(p) { editId = p.id }
                                p.id == store.profileId && !locked -> app.switchProfile(p.id)
                                else -> withPin(p) { app.switchProfile(p.id) }
                            }
                        },
                    ) { Avatar(p, label, Tile) }
                }
                if (!manage) {
                    ProfileTile(
                        label = stringResource(R.string.prof_guest), current = store.isGuest, edit = false, locked = false, requester = null,
                        onClick = { app.switchProfile(ProfileRules.GUEST) },
                    ) { Avatar(null, "", Tile) }
                }
                if (profiles.size < ProfileRules.MAX && !locked) {
                    ProfileTile(label = stringResource(R.string.prof_add), current = false, edit = false, locked = false, requester = null, onClick = { naming = "" }) {
                        Box(Modifier.size(Tile).background(Glass), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = TextPrimary, modifier = Modifier.size(46.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!locked) TvButton(
                stringResource(if (manage) R.string.prof_done else R.string.prof_manage), { manage = !manage },
                icon = if (manage) Icons.Rounded.Check else Icons.Rounded.ManageAccounts, container = Glass,
            )
            Spacer(Modifier.height(10.dp))
            if (!WebProfiles.supported()) {
                Txt(stringResource(R.string.prof_shared), T.caption, Modifier.fillMaxWidth(), maxLines = 2, color = TextSecondary, align = TextAlign.Center)
            }
        }

        editId?.let { id ->
            EditPanel(app, id, onClose = { editId = null }, onSetPin = {
                pinTask = PinTask(app.s(R.string.prof_pin_new), null) { pin -> store.setPin(id, pin); pinTask = null; app.toast(R.string.prof_pin_saved) }
            })
        }
        pinTask?.let { t ->
            PinDialog(
                title = t.title,
                verify = t.verifyId?.let { id -> { pin: String -> app.tryPin(id, pin) } },
                onDone = t.onOk,
                onCancel = { pinTask = null },
            )
        }
        // Renaming and creating share one dialog.
        naming?.let { target ->
            NameDialog(
                initial = if (target.isEmpty()) "" else profiles.firstOrNull { it.id == target }?.name ?: "",
                onSave = { raw ->
                    val name = ProfileRules.cleanName(raw)
                    if (target.isEmpty()) {
                        val created = store.addProfile(name.ifEmpty { app.s(R.string.prof_new_name, profiles.size + 1) })
                        if (created != null) app.toast(R.string.prof_added) else app.toast(R.string.prof_limit, ProfileRules.MAX)
                    } else {
                        store.renameProfile(target, name)
                    }
                    naming = null
                },
                onCancel = { naming = null },
            )
        }
    }
}

@Composable
private fun ProfileTile(
    label: String,
    current: Boolean,
    edit: Boolean,
    locked: Boolean,
    requester: FocusRequester?,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(Tile + 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier.size(Tile).let { if (requester != null) it.focusRequester(requester) else it }.onFocusChanged { focused = it.isFocused },
            shape = CircleShape, container = Color.Transparent, focusScale = 1.12f,
        ) {
            content()
            if (edit || locked) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(2.dp).size(32.dp).clip(CircleShape).background(Color(0xF00B0D12)),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (edit) Icons.Rounded.Edit else Icons.Rounded.Lock, null, tint = TextPrimary, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Txt(
            label, T.label.copy(fontSize = 18.sp, fontWeight = if (focused || current) FontWeight.SemiBold else FontWeight.Normal),
            Modifier.fillMaxWidth(), color = if (focused) TextPrimary else TextSecondary, align = TextAlign.Center,
        )
        // A small dot marks who is watching now (not colour alone: it is also the bold name).
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(if (current) Accent else Color.Transparent))
    }
}

@Composable
private fun EditPanel(app: AppState, id: String, onClose: () -> Unit, onSetPin: () -> Unit) {
    val store = app.store
    val p = store.profiles.firstOrNull { it.id == id }
    if (p == null) { LaunchedEffect(Unit) { onClose() }; return }
    val label = app.profileLabel(p)
    val first = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(700.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xF2121622)).border(1.dp, Outline, RoundedCornerShape(28.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, label, 76.dp)
                Column(Modifier.padding(start = 20.dp)) {
                    Txt(label, T.title)
                    Txt(stringResource(R.string.prof_edit_sub), T.caption)
                }
            }
            Spacer(Modifier.height(2.dp))
            SettingRow(
                stringResource(R.string.prof_name), stringResource(R.string.prof_name_d), "‹  $label  ›", false,
                modifier = Modifier.focusRequester(first),
            ) { rename = true }
            // Colour: a row of swatches; Left / Right to move, OK to choose.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dim.Radius)).background(Glass).padding(horizontal = 22.dp, vertical = 14.dp)) {
                Txt(stringResource(R.string.prof_color), T.body)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileRules.colors.forEachIndexed { i, c ->
                        FocusSurface(
                            onClick = { store.recolorProfile(id, i) },
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape, container = Color(c), focusScale = 1.18f,
                        ) {
                            if (p.color == i) Icon(Icons.Rounded.Check, null, tint = Color(0xFF0B0D12), modifier = Modifier.align(Alignment.Center).size(26.dp))
                        }
                    }
                }
            }
            // PIN: set, change or remove. (Reaching this panel already needed the current PIN.)
            val hasPin = store.hasPin(id)
            SettingRow(
                stringResource(R.string.prof_pin_row), stringResource(R.string.prof_pin_row_d),
                if (hasPin) stringResource(R.string.set_on) else stringResource(R.string.set_off), hasPin,
            ) { onSetPin() }
            if (hasPin) {
                SettingRow(stringResource(R.string.prof_pin_remove), stringResource(R.string.prof_pin_remove_d), "", false, danger = true) {
                    store.setPin(id, null); app.toast(R.string.prof_pin_removed)
                }
            }
            if (id != ProfileRules.DEFAULT && id != store.profileId) {
                SettingRow(
                    stringResource(R.string.prof_delete), stringResource(R.string.prof_delete_d),
                    if (armed) stringResource(R.string.set_press_again) else "", armed, danger = true,
                ) {
                    if (!armed) { armed = true; app.toast(R.string.toast_confirm) } else { app.deleteProfile(id); onClose() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvButton(stringResource(R.string.prof_done), onClose, icon = Icons.Rounded.Check, primary = true)
            }
        }
        if (rename) {
            NameDialog(
                initial = p.name,
                onSave = { store.renameProfile(id, it); rename = false },
                onCancel = { rename = false },
            )
        }
    }
}

/** Full-screen name entry in a real Dialog window (like the address bar), so the on-screen keyboard behaves. */
@Composable
private fun NameDialog(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val fr = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { fr.requestFocus() }
        keyboard?.show()
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.padding(top = 72.dp).width(620.dp)) {
                Txt(stringResource(R.string.prof_name_title), T.title)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().height(68.dp).border(3.dp, Accent, RoundedCornerShape(34.dp)).background(Surface, RoundedCornerShape(34.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.text.length <= ProfileRules.MAX_NAME) text = it },
                        singleLine = true,
                        textStyle = T.body.copy(fontSize = 24.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSave(text.text) }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).focusRequester(fr),
                    )
                    if (text.text.isEmpty()) Txt(stringResource(R.string.prof_name_hint), T.body.copy(fontSize = 24.sp), Modifier.padding(horizontal = 28.dp), TextSecondary)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(stringResource(R.string.prof_save), { onSave(text.text) }, primary = true, icon = Icons.Rounded.Check)
                    TvButton(stringResource(R.string.addr_cancel), onCancel)
                }
            }
        }
    }
}
