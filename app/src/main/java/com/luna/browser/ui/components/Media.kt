package com.luna.browser.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luna.browser.data.MediaItem
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.LocalReduceMotion
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextPrimary
import com.luna.browser.ui.theme.TextSecondary
import com.luna.browser.ui.theme.ms
import java.util.Calendar

/** Image with crossfade; draws nothing when [model] is null or fails. */
@Composable
fun Art(model: Any?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    if (model == null) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(250).build(),
        contentDescription = null,
        contentScale = scale,
        modifier = modifier,
    )
}

/**
 * Slow drifting colour light behind every screen. Only layer transforms animate,
 * so it costs almost nothing on TV GPUs. Static when reduced motion is on.
 */
@Composable
fun Aurora(modifier: Modifier = Modifier, animated: Boolean = true) {
    val still = LocalReduceMotion.current || !animated
    val t: State<Float> = if (still) remember { mutableFloatStateOf(0.5f) } else
        rememberInfiniteTransition(label = "aurora").animateFloat(
            0f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse), label = "t",
        )
    Box(modifier.fillMaxSize().background(Bg)) {
        Blob(Color(0xFF3D4FB8), 760.dp, -160.dp, -260.dp, 220.dp, 120.dp, t)
        Blob(Color(0xFF7A2E8C), 620.dp, 560.dp, 120.dp, -180.dp, 90.dp, t)
        Blob(Color(0xFF136E7E), 560.dp, 160.dp, 300.dp, 260.dp, -160.dp, t)
    }
}

@Composable
private fun Blob(color: Color, size: Dp, x: Dp, y: Dp, dx: Dp, dy: Dp, t: State<Float>) {
    val d = LocalDensity.current.density
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                translationX = (x.value + dx.value * t.value) * d
                translationY = (y.value + dy.value * t.value) * d
                alpha = 0.55f
            }
            .background(Brush.radialGradient(listOf(color, Color.Transparent))),
    )
}

/** Full-screen backdrop: the focused item's artwork over the aurora, with cinematic scrims. */
@Composable
fun Backdrop(art: Any?, animated: Boolean = true) {
    Box(Modifier.fillMaxSize()) {
        Aurora(animated = animated)
        Crossfade(targetState = art, animationSpec = tween(ms(450)), label = "backdrop") { a ->
            if (a != null) Art(a, Modifier.fillMaxSize().alpha(0.62f))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Bg.copy(alpha = 0.96f), 0.5f to Bg.copy(alpha = 0.6f), 1f to Bg.copy(alpha = 0.15f)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Bg.copy(alpha = 0.5f), 0.3f to Color.Transparent, 0.62f to Bg.copy(alpha = 0.85f), 1f to Bg),
            ),
        )
    }
}

/** Site icon: favicon when we have one, otherwise a lettered tile. */
@Composable
fun SiteIcon(title: String, icon: Any?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        LetterTile(title, size)
        if (icon != null) {
            Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color.White), contentAlignment = Alignment.Center) {
                Art(icon, Modifier.size(size * 0.72f), ContentScale.Fit)
            }
        }
    }
}

/**
 * 16:9 media card in the style of streaming apps: artwork (or a brand gradient with the site
 * icon), title underneath that brightens on focus.
 */
@Composable
fun MediaCard(
    item: MediaItem,
    width: Dp,
    onFocus: (MediaItem) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(item) },
            shape = RoundedCornerShape(14.dp),
            container = Color(item.brand.second),
            focusScale = 1.08f,
        ) {
            if (item.art != null) {
                Art(item.art, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))))
                Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(item.title, item.icon, 24.dp)
                    Txt("  " + item.subtitle, T.caption.copy(fontSize = 14.sp, color = Color.White), color = Color.White)
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(item.brand.first), Color(item.brand.second)))))
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    SiteIcon(item.title, item.icon, width * 0.22f)
                    Spacer(Modifier.height(8.dp))
                    Txt(item.title, T.body.copy(fontWeight = FontWeight.Bold, color = Color.White), color = Color.White)
                }
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(10.dp))
            Txt(
                item.title, T.label.copy(fontSize = 16.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal),
                color = if (focused) TextPrimary else TextSecondary, modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** Vietnamese relative time: "Vừa xong", "5 phút trước", "Hôm qua"… */
fun timeAgo(ts: Long): String {
    if (ts <= 0) return ""
    val diff = (System.currentTimeMillis() - ts) / 1000
    return when {
        diff < 60 -> "Vừa xong"
        diff < 3600 -> "${diff / 60} phút trước"
        diff < 86_400 -> "${diff / 3600} giờ trước"
        diff < 172_800 -> "Hôm qua"
        diff < 604_800 -> "${diff / 86_400} ngày trước"
        else -> "${diff / 604_800} tuần trước"
    }
}

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..10 -> "Chào buổi sáng"
    in 11..13 -> "Chào buổi trưa"
    in 14..17 -> "Chào buổi chiều"
    else -> "Chào buổi tối"
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0x33FFFFFF)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Txt(text, T.caption.copy(fontSize = 14.sp, color = TextPrimary), color = TextPrimary)
    }
}
