package com.luna.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luna.browser.AppState
import com.luna.browser.data.MediaItem
import com.luna.browser.ui.theme.Dim
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.ms
import kotlinx.coroutines.launch

/**
 * A titled, horizontally sliding row of 16:9 [MediaCard]s (Home, Tabs).
 *
 * It is not a scroll container: Compose's "scroll the focused child into view" kept sliding the first card to the
 * screen edge, so the content is translated instead and a focused card is revealed keeping a [Dim.SafeH] margin at
 * both ends. Cards all have the same width, so their positions are known without measuring.
 *
 * [mine] are the cards' focus requesters; [above] (optional) are those of the row above, so that Up goes to the card
 * above (the default focus search skips rows that were scrolled out of view).
 */
@Composable
fun CardRow(
    app: AppState,
    title: String?,
    items: List<MediaItem>,
    width: Dp,
    mine: List<FocusRequester>,
    above: List<FocusRequester>?,
    onFocus: (MediaItem) -> Unit,
    focusScope: String = "home",
    onClick: (MediaItem) -> Unit,
) {
    val density = LocalDensity.current
    val margin = with(density) { Dim.SafeH.toPx() }
    val cardW = with(density) { width.toPx() }
    val gap = with(density) { 20.dp.toPx() }
    var viewW by remember { mutableStateOf(0) }
    val offset = remember { Animatable(0f) }
    val slideMs = ms(220)
    val scope = rememberCoroutineScope()

    fun reveal(i: Int) {
        val total = 2 * margin + items.size * cardW + (items.size - 1).coerceAtLeast(0) * gap
        val maxOffset = (total - viewW).coerceAtLeast(0f)
        val left = margin + i * (cardW + gap) - offset.value
        val right = left + cardW
        val delta = when {
            left < margin -> left - margin                      // keep the left margin (also for the first card)
            right > viewW - margin -> right - (viewW - margin)  // and a margin before the right edge
            else -> 0f
        }
        if (delta != 0f && viewW > 0) {
            scope.launch { offset.animateTo((offset.value + delta).coerceIn(0f, maxOffset), tween(slideMs)) }
        }
    }

    // When cards disappear (a tab is closed) the row may be shorter than the current scroll: bring it back.
    LaunchedEffect(items.size, viewW) {
        val total = 2 * margin + items.size * cardW + (items.size - 1).coerceAtLeast(0) * gap
        val maxOffset = (total - viewW).coerceAtLeast(0f)
        if (offset.value > maxOffset) offset.snapTo(maxOffset)
    }

    Column {
        if (title != null) Txt(title, T.title.copy(fontSize = 22.sp), Modifier.padding(start = Dim.SafeH, top = 10.dp))
        Box(Modifier.fillMaxWidth().clipToBounds().onSizeChanged { viewW = it.width }) {
            Row(
                Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = -offset.value }
                    .padding(horizontal = Dim.SafeH, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items.forEachIndexed { i, item ->
                    MediaCard(
                        item, width, onFocus, { onClick(item) },
                        Modifier.rememberFocus(app, focusScope, item.key)
                            .onFocusChanged { if (it.isFocused) reveal(i) }
                            .focusRequester(mine[i])
                            .focusProperties {
                                // Sideways stays inside the row: at either end nothing happens (the default search used to
                                // jump to a card of the row below, mid-row).
                                left = if (i > 0) mine[i - 1] else FocusRequester.Cancel
                                right = if (i < items.size - 1) mine[i + 1] else FocusRequester.Cancel
                                if (above != null && above.isNotEmpty()) up = above[minOf(i, above.size - 1)]
                            },
                    )
                }
            }
        }
    }
}
