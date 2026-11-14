package com.luna.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.FocusRing
import com.luna.browser.ui.theme.Outline
import com.luna.browser.ui.theme.Surface
import com.luna.browser.ui.theme.SurfaceFocus
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary
import com.luna.browser.ui.theme.ms

@Composable
fun Txt(
    text: String,
    style: TextStyle = T.body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text = text, style = style, modifier = modifier, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, textAlign = align,
        color = if (color == Color.Unspecified) style.color else color,
    )
}

/**
 * The base of every focusable element. Focus is shown by scale + elevation + a bright ring
 * + a lighter fill, so it never relies on colour alone.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dim.Radius),
    container: Color = Surface,
    focusScale: Float = 1.06f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(ms(160)), label = "focusScale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 18.dp else 0.dp, shape)
            .clip(shape)
            .background(if (focused) SurfaceFocus else container)
            .border(if (focused) BorderStroke(3.dp, FocusRing) else BorderStroke(1.dp, Outline), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) { content(focused) }
}

/** Remembers the last focused item of a screen so focus returns to it after menus. */
@Composable
fun Modifier.rememberFocus(app: AppState, scope: String, id: String): Modifier {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (app.focusMemory[scope] == id) runCatching { fr.requestFocus() }
    }
    return this
        .focusRequester(fr)
        .onFocusChanged { if (it.isFocused) app.focusMemory[scope] = id }
}

@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        container = if (primary) Accent.copy(alpha = 0.22f) else Surface,
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, null, tint = if (primary) Accent else TextPrimary, modifier = Modifier.size(24.dp))
            Txt(label, T.label)
        }
    }
}

/** Icon over label — used in the browser toolbar. Always labelled, never an ambiguous bare icon. */
@Composable
fun IconLabelButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    FocusSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.width(80.dp).height(66.dp),
        shape = RoundedCornerShape(14.dp),
        container = if (highlighted) Accent.copy(alpha = 0.2f) else Surface,
        focusScale = 1.08f,
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (!enabled) TextSecondary.copy(alpha = .5f) else if (highlighted) Accent else TextPrimary, modifier = Modifier.size(26.dp))
            Txt(label, T.caption.copy(fontSize = 14.sp), color = if (enabled) TextPrimary else TextSecondary.copy(alpha = .5f))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Txt(text, T.title, modifier.padding(top = 20.dp, bottom = 4.dp))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actionRequester: FocusRequester? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Txt(title, T.title, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(message, T.caption.copy(fontSize = 18.sp), maxLines = 3, align = TextAlign.Center, modifier = Modifier.width(520.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            TvButton(actionLabel, onAction, primary = true, modifier = if (actionRequester != null) Modifier.focusRequester(actionRequester) else Modifier)
        }
    }
}

/** Gradient + initial tile that stands in for a favicon. */
@Composable
fun LetterTile(text: String, size: Dp, modifier: Modifier = Modifier) {
    val palette = listOf(
        0xFF6C8CFF to 0xFF8E5BFF, 0xFFFF7A8A to 0xFFFFB36B, 0xFF4DD4AC to 0xFF3B9EFF,
        0xFFFFC857 to 0xFFFF8A5B, 0xFFB57BFF to 0xFFFF6BB5, 0xFF52D1DC to 0xFF4D7CFF,
    )
    val (a, b) = palette[(text.hashCode() and 0x7fffffff) % palette.size]
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4))
            .background(Brush.linearGradient(listOf(Color(a), Color(b)))),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text.trim().take(1).uppercase().ifEmpty { "?" }, T.title.copy(color = Bg), color = Bg)
    }
}

@Composable
fun SiteCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick, modifier.width(196.dp).height(118.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            LetterTile(title, 44.dp)
            Column {
                Txt(title, T.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                Txt(subtitle, T.caption.copy(fontSize = 14.sp), color = TextSecondary)
            }
        }
    }
}

@Composable
fun ToastHost(app: AppState, modifier: Modifier = Modifier) {
    val msg = app.toastMsg
    LaunchedEffect(msg) {
        if (msg != null) {
            kotlinx.coroutines.delay(2600)
            if (app.toastMsg == msg) app.toastMsg = null
        }
    }
    Box(modifier.fillMaxWidth().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
        androidx.compose.animation.AnimatedVisibility(
            visible = msg != null,
            enter = androidx.compose.animation.fadeIn(tween(ms(180))),
            exit = androidx.compose.animation.fadeOut(tween(ms(180))),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xF0252C3A))
                    .border(1.dp, Outline, RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) { Txt(msg ?: "", T.label, maxLines = 2) }
        }
    }
}

/** The Luna crescent mark. */
@Composable
fun MoonLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val path = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c, r))
        }
        val cut = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(c.x + r * 0.45f, c.y - r * 0.25f), r * 0.85f))
        }
        val moon = androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, path, cut)
        drawPath(moon, Brush.linearGradient(listOf(Accent, Color(0xFFB57BFF))))
    }
}
