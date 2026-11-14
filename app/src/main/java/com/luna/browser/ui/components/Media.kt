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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
fun Art(
    model: Any?,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
    onLoaded: () -> Unit = {},
) {
    if (model == null) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(250).build(),
        contentDescription = null,
        contentScale = scale,
        alpha = alpha,
        modifier = modifier,
        onSuccess = { onLoaded() },
    )
}

/** One drifting light: centre and radius in dp on the 960×540 dp canvas, drift (dx, dy) over a sweep. */
private class Light(val color: Color, val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float)

private val lights = listOf(
    Light(Color(0xCC4F63E8), 210f, 110f, 390f, 260f, 140f),
    Light(Color(0xCC9B3DB5), 880f, 380f, 320f, -200f, 120f),
    Light(Color(0xCC1A93A6), 410f, 590f, 290f, 300f, -180f),
)

/**
 * Slow drifting colour light behind every screen.
 *
 * Measured on the MiTV: drawing the lights as three full-screen radial-gradient layers cost the
 * GPU 33–50 ms per frame. Soft gradients survive heavy upscaling, so they are painted by the CPU
 * into a 160×90 bitmap (~15 times a second) and the GPU only stretches one small texture.
 * Static when reduced motion or the setting is off.
 */
@Composable
fun Aurora(modifier: Modifier = Modifier, animated: Boolean = true, dim: Float = 0f) {
    val still = LocalReduceMotion.current || !animated
    // The drift is so slow (22 s per sweep) that ~15 updates a second look identical to 60.
    // Every update redraws the whole frame on this GPU, so the drift also pauses while the viewer
    // is pressing keys: navigation gets the GPU to itself, the light resumes when idle.
    val t = remember { mutableFloatStateOf(0.5f) }
    if (!still) {
        LaunchedEffect(Unit) {
            var phaseMs = 11_000L
            var last = System.nanoTime()
            while (true) {
                val now = System.nanoTime()
                val idle = Interaction.idleFor() > 2_500
                if (idle) phaseMs += (now - last) / 1_000_000L
                last = now
                val phase = (phaseMs % 44_000L) / 22_000f
                if (idle) t.floatValue = if (phase <= 1f) phase else 2f - phase
                delay(if (idle) 66 else 250)
            }
        }
    }
    val bitmap = remember { ImageBitmap(160, 90) }
    val canvas = remember { androidx.compose.ui.graphics.Canvas(bitmap) }
    val paint = remember { Paint() }
    Spacer(
        modifier.fillMaxSize().drawBehind {
            val k = 160f / 960f // dp on the 960-dp canvas -> bitmap pixels
            val p = t.floatValue // read in the draw phase: only this layer redraws
            paint.shader = null
            paint.color = Bg
            canvas.drawRect(0f, 0f, 160f, 90f, paint)
            lights.forEach { l ->
                val c = Offset((l.x + l.dx * (p - 0.5f)) * k, (l.y + l.dy * (p - 0.5f)) * k)
                paint.shader = RadialGradientShader(c, l.r * k, listOf(l.color, Color.Transparent))
                canvas.drawCircle(c, l.r * k, paint)
            }
            if (dim > 0f) {
                // Darkening baked into the small bitmap instead of another full-screen layer.
                paint.shader = null
                paint.color = Bg.copy(alpha = dim)
                canvas.drawRect(0f, 0f, 160f, 90f, paint)
            }
            drawImage(
                bitmap,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low, // bilinear: smooth when stretched
            )
        },
    )
}

/**
 * The two cinematic scrims (dark on the left for the title, dark at the bottom for the rows),
 * pre-multiplied into one small alpha bitmap so the GPU blends a single full-screen layer.
 */
private val scrim: ImageBitmap by lazy {
    val w = 96
    val h = 54
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    fun ramp(x: Float, stops: List<Pair<Float, Float>>): Float {
        for (i in 1 until stops.size) {
            val (x0, a0) = stops[i - 1]
            val (x1, a1) = stops[i]
            if (x <= x1) return a0 + (a1 - a0) * ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
        }
        return stops.last().second
    }
    val hStops = listOf(0f to 0.96f, 0.5f to 0.6f, 1f to 0.15f)
    val vStops = listOf(0f to 0.5f, 0.3f to 0f, 0.62f to 0.85f, 1f to 1f)
    val rgb = Bg.toArgb() and 0x00FFFFFF
    for (y in 0 until h) for (x in 0 until w) {
        val a1 = ramp(x / (w - 1f), hStops)
        val a2 = ramp(y / (h - 1f), vStops)
        val a = 1f - (1f - a1) * (1f - a2) // two layers stacked
        bmp.setPixel(x, y, ((a * 255).toInt().coerceIn(0, 255) shl 24) or rgb)
    }
    bmp.asImageBitmap()
}

private class Slot<T>(val value: T)

/**
 * Crossfade without offscreen layers. Compose's Crossfade fades each child through an offscreen
 * buffer — for a full-screen backdrop that is two extra full-screen passes per frame, which this
 * TV's GPU can't afford. ModulateAlpha applies the alpha to each draw call directly.
 */
@Composable
fun <T> LightCrossfade(target: T, durationMs: Int, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    var current by remember { mutableStateOf(Slot(target)) }
    var previous by remember { mutableStateOf<Slot<T>?>(null) }
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(target) {
        if (target != current.value) {
            previous = current
            current = Slot(target)
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMs))
            previous = null
        }
    }
    Box(modifier) {
        previous?.let { p ->
            androidx.compose.runtime.key(p) {
                Box(Modifier.graphicsLayer { alpha = 1f - progress.value; compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(p.value) }
            }
        }
        androidx.compose.runtime.key(current) {
            Box(Modifier.graphicsLayer { alpha = progress.value; compositingStrategy = CompositingStrategy.ModulateAlpha }) { content(current.value) }
        }
    }
}

/** Full-screen backdrop: the focused item's artwork over the aurora, with cinematic scrims. */
@Composable
fun Backdrop(art: () -> Any?, animated: Boolean = true) {
    Box(Modifier.fillMaxSize()) {
        Aurora(animated = animated)
        LightCrossfade(art(), ms(450), Modifier.fillMaxSize()) { a ->
            // alpha applied while drawing the image — Modifier.alpha would render it offscreen first
            if (a != null) Art(a, Modifier.fillMaxSize(), alpha = 0.62f)
        }
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                drawImage(scrim, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
            },
        )
    }
}

/** Last remote-control input, so ambient animation can step aside while the viewer navigates. */
object Interaction {
    @Volatile
    var lastInputAt = 0L

    fun touch() { lastInputAt = android.os.SystemClock.uptimeMillis() }
    fun idleFor() = android.os.SystemClock.uptimeMillis() - lastInputAt
}

/** Site icon: favicon when we have one, otherwise a lettered tile. */
@Composable
fun SiteIcon(title: String, icon: Any?, size: Dp, modifier: Modifier = Modifier) {
    // The letter tile stays visible until the favicon has actually loaded — no blank white squares.
    var loaded by remember(icon) { mutableStateOf(false) }
    Box(modifier.size(size)) {
        LetterTile(title, size)
        if (icon != null) {
            Box(
                Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(if (loaded) Color.White else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Art(icon, Modifier.size(size * 0.72f), ContentScale.Fit) { loaded = true }
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
                    SiteIcon(item.subtitle, item.icon, 24.dp)
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
                // Brand cards already show the name inside, so the caption shows the address instead.
                if (item.art == null) item.subtitle else item.title, T.label.copy(fontSize = 16.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal),
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
