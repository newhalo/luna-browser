package com.luna.browser.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luna.browser.R
import com.luna.browser.data.PinRules
import com.luna.browser.ui.components.FocusSurface
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Danger
import com.luna.browser.ui.theme.Glass
import com.luna.browser.ui.theme.Scrim
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary

/**
 * A four-digit PIN pad for the remote: D-pad to a digit and OK, or press the number keys. Four digits are submitted at once.
 *
 * With [verify] it asks for an existing PIN: [verify] returns null when right, otherwise the message to show.
 * Without it, it asks for a new PIN twice (the second time to confirm) and hands it to [onDone].
 */
@Composable
fun PinDialog(
    title: String,
    verify: ((String) -> String?)?,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }     // new-PIN mode: the first entry, waiting for confirmation
    var message by remember { mutableStateOf<String?>(null) }
    val centre = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { centre.requestFocus() } }

    fun submit(pin: String) {
        if (verify != null) {
            val err = verify(pin)
            if (err == null) onDone(pin) else { message = err; entered = "" }
        } else if (first == null) {
            first = pin; entered = ""; message = null
        } else if (first == pin) {
            onDone(pin)
        } else {
            first = null; entered = ""
            message = MISMATCH
        }
    }

    fun press(d: Char) {
        if (entered.length >= PinRules.LENGTH) return
        message = null
        entered += d
        if (entered.length == PinRules.LENGTH) submit(entered)
    }

    val mismatch = stringResource(R.string.prof_pin_mismatch)
    val prompt = when {
        verify != null -> title
        first == null -> stringResource(R.string.prof_pin_new)
        else -> stringResource(R.string.prof_pin_again)
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Scrim).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    val digit = when (e.key) {
                        Key.Zero, Key.NumPad0 -> '0'; Key.One, Key.NumPad1 -> '1'; Key.Two, Key.NumPad2 -> '2'
                        Key.Three, Key.NumPad3 -> '3'; Key.Four, Key.NumPad4 -> '4'; Key.Five, Key.NumPad5 -> '5'
                        Key.Six, Key.NumPad6 -> '6'; Key.Seven, Key.NumPad7 -> '7'; Key.Eight, Key.NumPad8 -> '8'
                        Key.Nine, Key.NumPad9 -> '9'; else -> null
                    }
                    when {
                        digit != null -> { press(digit); true }
                        e.key == Key.Delete || e.key == Key.Backspace -> { entered = entered.dropLast(1); true }
                        else -> false
                    }
                } else false
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(prompt, T.title, Modifier.padding(bottom = 6.dp), align = TextAlign.Center)
                Txt(
                    if (message == MISMATCH) mismatch else message ?: " ",
                    T.label, color = Danger, align = TextAlign.Center, modifier = Modifier.height(30.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.semantics { contentDescription = "${entered.length}" }) {
                    for (i in 0 until PinRules.LENGTH) {
                        Box(Modifier.size(22.dp).background(if (i < entered.length) Accent else Color(0x33FFFFFF), CircleShape))
                    }
                }
                Spacer(Modifier.height(26.dp))
                val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            r.forEach { d -> PadKey(d, if (d == '5') Modifier.focusRequester(centre) else Modifier) { press(d) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusSurface(
                            onClick = { entered = entered.dropLast(1) },
                            modifier = Modifier.size(width = 84.dp, height = 62.dp).semantics { contentDescription = "⌫" },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), container = Glass,
                        ) { Icon(Icons.Rounded.Backspace, null, tint = TextPrimary, modifier = Modifier.align(Alignment.Center).size(26.dp)) }
                        PadKey('0', Modifier) { press('0') }
                        FocusSurface(
                            onClick = onCancel,
                            modifier = Modifier.size(width = 84.dp, height = 62.dp).semantics { contentDescription = "×" },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), container = Glass,
                        ) { Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.align(Alignment.Center).size(26.dp)) }
                    }
                }
            }
        }
    }
}

private const val MISMATCH = "mismatch-marker"

@Composable
private fun PadKey(d: Char, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(width = 84.dp, height = 62.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), container = Glass, focusScale = 1.1f,
    ) { Txt(d.toString(), T.title.copy(fontSize = 28.sp), Modifier.align(Alignment.Center)) }
}
