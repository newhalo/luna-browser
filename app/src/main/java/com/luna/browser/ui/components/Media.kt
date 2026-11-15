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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.luna.browser.AppState
import com.luna.browser.R
import com.luna.browser.data.MediaItem
import com.luna.browser.ui.theme.Accent
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.LocalReduceMotion
import com.luna.browser.ui.theme.Motion
import com.luna.browser.ui.theme.Themes
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
    /** Used when [model] fails to load. */
    fallback: Any? = null,
    onLoaded: () -> Unit = {},
) {
    if (model == null) return
    var failed by remember(model) { mutableStateOf(false) }
    val shown = if (failed && fallback != null) fallback else model
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(shown).crossfade(250)
            // Some image hosts (Wikimedia, for one) reject clients that don't identify themselves.
            .addHeader("User-Agent", USER_AGENT).build(),
        contentDescription = null,
        contentScale = scale,
        alpha = alpha,
        modifier = modifier,
        onSuccess = { onLoaded() },
        onError = { failed = true },
    )
}

private const val USER_AGENT = "LunaBrowser/0.3 (Android TV; +https://github.com/newhalo/luna-browser)"

private const val BW = 160  // the backdrop is painted into a BW × BH bitmap and stretched
private const val BH = 90

/** Fixed pseudo-random particle seeds (per theme), so the picture is the same at every start. */
private class Dots(val n: Int, seed: Int) {
    private val r = kotlin.random.Random(seed)
    val x = FloatArray(n) { r.nextFloat() * BW }
    val y = FloatArray(n) { r.nextFloat() * BH }
    val v = FloatArray(n) { 0.6f + r.nextFloat() * 1.4f }      // speed / frequency factor
    val ph = FloatArray(n) { r.nextFloat() * 6.2832f }
    val size = FloatArray(n) { 0.5f + r.nextFloat() * 0.8f }   // radius in bitmap pixels
}

private fun dotsFor(m: Motion) = when (m) {
    Motion.Embers -> Dots(26, 11)
    Motion.Fireflies -> Dots(20, 23)
    Motion.Stars -> Dots(70, 5)
    else -> Dots(0, 1)
}

/** One wave layer: baseline (0..1 of the height), amplitude, spatial frequency, speed, alpha and which colour. */
private class Wave(val base: Float, val amp: Float, val freq: Float, val speed: Float, val alpha: Float, val deep: Boolean)
private val waves = listOf(
    Wave(0.60f, 3.2f, 0.050f, 0.45f, 0.50f, true),
    Wave(0.70f, 4.0f, 0.036f, -0.38f, 0.16f, false),
    Wave(0.80f, 3.0f, 0.062f, 0.62f, 0.70f, true),
)

/**
 * Slow animated backdrop behind every screen, in the active theme's colours and motion (`Themes`).
 *
 * Measured on the MiTV: drawing the lights as three full-screen radial-gradient layers cost the
 * GPU 33–50 ms per frame. Soft gradients survive heavy upscaling, so everything (lights, sparks, stars,
 * waves) is painted by the CPU into a 160×90 bitmap (~15 times a second) and the GPU only stretches one
 * small texture. Static when reduced motion or the setting is off, and for the Still theme.
 */
@Composable
fun Aurora(modifier: Modifier = Modifier, animated: Boolean = true, dim: Float = 0f) {
    val pal = Themes.current
    val still = LocalReduceMotion.current || !animated || pal.motion == Motion.Still
    // The motion is so slow that ~15 updates a second look identical to 60. Every update redraws the
    // whole frame on this GPU, so time also stops while the viewer is pressing keys: navigation gets
    // the GPU to itself, the motion resumes when idle.
    val time = remember { mutableFloatStateOf(11f) }   // seconds of idle time; 11 s = the lights' centre position
    if (!still) {
        LaunchedEffect(Unit) {
            var last = System.nanoTime()
            while (true) {
                val now = System.nanoTime()
                val idle = Interaction.idleFor() > 2_500
                if (idle) time.floatValue += (now - last) / 1_000_000_000f
                last = now
                delay(if (idle) 66 else 250)
            }
        }
    }
    val dots = remember(pal.id) { dotsFor(pal.motion) }
    val bitmap = remember { ImageBitmap(BW, BH) }
    val canvas = remember { androidx.compose.ui.graphics.Canvas(bitmap) }
    val paint = remember { Paint() }
    val path = remember { androidx.compose.ui.graphics.Path() }
    Spacer(
        modifier.fillMaxSize().drawBehind {
            val k = BW / 960f // dp on the 960-dp canvas -> bitmap pixels
            val t = time.floatValue // read in the draw phase: only this layer redraws
            val bw = BW.toFloat()
            val bh = BH.toFloat()
            paint.shader = null
            paint.color = pal.bg
            canvas.drawRect(0f, 0f, bw, bh, paint)
            val p = com.luna.browser.ui.theme.pingPong((t % 44f) / 22f)
            pal.lights.forEach { l ->
                val c = Offset((l.x + l.dx * (p - 0.5f)) * k, (l.y + l.dy * (p - 0.5f)) * k)
                paint.shader = RadialGradientShader(c, l.r * k, listOf(l.color, Color.Transparent))
                canvas.drawCircle(c, l.r * k, paint)
            }
            paint.shader = null
            when (pal.motion) {
                Motion.Embers -> for (i in 0 until dots.n) {
                    val yy = (((dots.y[i] - t * dots.v[i] * 2.0f) % bh) + bh) % bh          // rising
                    val xx = dots.x[i] + kotlin.math.sin(t * 0.35f * dots.v[i] + dots.ph[i]) * 3f
                    val edge = kotlin.math.sin(Math.PI.toFloat() * yy / bh)                  // fades in and out at the ends
                    val a = 0.6f * edge * (0.65f + 0.35f * kotlin.math.sin(t * dots.v[i] + dots.ph[i]))
                    paint.color = pal.particle.copy(alpha = a.coerceIn(0f, 1f))
                    canvas.drawCircle(Offset(xx, yy), dots.size[i] * 1.3f, paint)
                }
                Motion.Fireflies -> for (i in 0 until dots.n) {
                    val xx = dots.x[i] + kotlin.math.sin(t * 0.18f * dots.v[i] + dots.ph[i]) * 10f
                    val yy = dots.y[i] + kotlin.math.cos(t * 0.15f * dots.v[i] + dots.ph[i] * 1.7f) * 6f
                    val blink = kotlin.math.sin(t * 0.55f * dots.v[i] + dots.ph[i])
                    val a = if (blink > 0f) blink * blink * 0.9f else 0f
                    if (a > 0.02f) {
                        paint.color = pal.particle.copy(alpha = a)
                        canvas.drawCircle(Offset(xx, yy), dots.size[i] * 1.2f, paint)
                    }
                }
                Motion.Stars -> for (i in 0 until dots.n) {
                    val a = 0.2f + 0.6f * (0.5f + 0.5f * kotlin.math.sin(t * 0.8f * dots.v[i] + dots.ph[i]))
                    paint.color = pal.particle.copy(alpha = a)
                    canvas.drawCircle(Offset(dots.x[i], dots.y[i]), dots.size[i] * 0.8f, paint)
                }
                Motion.Waves -> waves.forEach { w ->
                    path.reset()
                    path.moveTo(0f, bh)
                    var x = 0f
                    while (x <= bw + 5f) {
                        path.lineTo(x, w.base * bh + w.amp * kotlin.math.sin(x * w.freq + t * w.speed))
                        x += 5f
                    }
                    path.lineTo(bw, bh)
                    path.close()
                    paint.color = (if (w.deep) pal.accentDeep else pal.accent).copy(alpha = w.alpha)
                    canvas.drawPath(path, paint)
                }
                else -> {}
            }
            if (dim > 0f) {
                // Darkening baked into the small bitmap instead of another full-screen layer.
                paint.color = pal.bg.copy(alpha = dim)
                canvas.drawRect(0f, 0f, bw, bh, paint)
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
private fun buildScrim(bg: Color): ImageBitmap {
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
    val rgb = bg.toArgb() and 0x00FFFFFF
    for (y in 0 until h) for (x in 0 until w) {
        val a1 = ramp(x / (w - 1f), hStops)
        val a2 = ramp(y / (h - 1f), vStops)
        val a = 1f - (1f - a1) * (1f - a2) // two layers stacked
        bmp.setPixel(x, y, ((a * 255).toInt().coerceIn(0, 255) shl 24) or rgb)
    }
    return bmp.asImageBitmap()
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
    val pal = Themes.current
    val scrim = remember(pal.id) { buildScrim(pal.bg) }
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
    // Dp.Unspecified: the card fills the cell it is placed in (History grid).
    val fixed = width != Dp.Unspecified
    val iconSize = if (fixed) width * 0.22f else 44.dp
    Column(if (fixed) Modifier.width(width) else Modifier.fillMaxWidth()) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(item) },
            shape = RoundedCornerShape(14.dp),
            container = Color(item.brand.second),
            focusScale = 1.08f,
        ) {
            if (item.art != null) {
                Art(item.art, Modifier.fillMaxSize(), fallback = item.alt)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))))
                Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(item.subtitle, item.icon, 24.dp)
                    Txt("  " + item.subtitle, T.caption.copy(fontSize = 14.sp, color = Color.White), color = Color.White)
                }
                if (item.progress > 0.02f) {
                    // How far the video was watched, like the red bar on YouTube / Netflix.
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color(0x88000000))) {
                        Box(Modifier.fillMaxWidth(item.progress.coerceAtMost(1f)).fillMaxHeight().background(Accent))
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(item.brand.first), Color(item.brand.second)))))
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    SiteIcon(item.title, item.icon, iconSize)
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

/** Localised relative time ("Just now", "5 minutes ago", "Yesterday", …) using plurals. */
fun timeAgo(app: AppState, ts: Long): String {
    if (ts <= 0) return ""
    val diff = (System.currentTimeMillis() - ts) / 1000
    return when {
        diff < 60 -> app.s(R.string.ago_now)
        diff < 3600 -> app.plural(R.plurals.ago_minutes, (diff / 60).toInt())
        diff < 86_400 -> app.plural(R.plurals.ago_hours, (diff / 3600).toInt())
        diff < 172_800 -> app.s(R.string.ago_yesterday)
        diff < 604_800 -> app.plural(R.plurals.ago_days, (diff / 86_400).toInt())
        else -> app.plural(R.plurals.ago_weeks, (diff / 604_800).toInt())
    }
}

fun greeting(app: AppState): String = app.s(
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 4..11 -> R.string.greet_morning
        in 12..17 -> R.string.greet_afternoon
        else -> R.string.greet_evening
    },
)

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0x33FFFFFF)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Txt(text, T.caption.copy(fontSize = 14.sp, color = TextPrimary), color = TextPrimary)
    }
}
