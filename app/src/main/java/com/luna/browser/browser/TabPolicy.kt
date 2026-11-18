package com.luna.browser.browser

import org.json.JSONArray
import org.json.JSONObject

/**
 * The rules behind tabs, kept apart from the Android classes so they are unit-tested.
 *
 * A TV has little memory, so only a few tabs hold a live WebView; the others are "frozen" (address and title only)
 * and are loaded again when selected.
 */
object TabPolicy {
    const val MAX_TABS = 8

    /** Live WebViews allowed at once. */
    fun maxLive(lowEnd: Boolean) = if (lowEnd) 2 else 3

    class LiveTab(val index: Int, val lastUsed: Long)

    /**
     * The live tab to freeze so that [target] can become live: the least recently used one that is neither the
     * target nor the tab being shown ([current]). Null when there is already room.
     */
    fun victim(live: List<LiveTab>, target: Int, current: Int, maxLive: Int): Int? = victim(live, target, setOf(current), maxLive)

    /** As above, but none of the tabs in [keep] (the ones on screen: both halves of a split screen) is frozen while another can be. */
    fun victim(live: List<LiveTab>, target: Int, keep: Set<Int>, maxLive: Int): Int? {
        val others = live.filter { it.index != target }
        if (others.size < maxLive) return null
        return others.filter { it.index !in keep }.minByOrNull { it.lastUsed }?.index
            ?: others.minByOrNull { it.lastUsed }?.index
    }

    /** The tab to show next to [current] in a split screen: the most recently used other tab that has a page, or null. */
    fun splitPartner(lastUsed: List<Long>, hasPage: List<Boolean>, current: Int): Int? =
        lastUsed.indices.filter { it != current && hasPage[it] }.maxByOrNull { lastUsed[it] }

    /** After removing the tab at [removed], which index should be current. */
    fun indexAfterClose(current: Int, removed: Int, sizeAfter: Int): Int = when {
        sizeAfter <= 0 -> 0
        removed < current -> current - 1
        removed == current -> minOf(current, sizeAfter - 1)
        else -> current
    }

    /**
     * Private tabs are never saved. Given which open tabs are private and which one is current, the indices to save
     * and the current one among them (the nearest saved tab before it when a private tab is current, else the first).
     */
    fun persistable(isPrivate: List<Boolean>, current: Int): Pair<List<Int>, Int> {
        val keep = isPrivate.indices.filter { !isPrivate[it] }
        val at = keep.indexOf(current)
        val cur = if (at >= 0) at else keep.lastOrNull { it < current }?.let { keep.indexOf(it) } ?: 0
        return keep to cur
    }

    fun toJson(tabs: List<Pair<String, String>>, current: Int): String = JSONObject()
        .put("current", current)
        .put("tabs", JSONArray().apply { tabs.forEach { (u, t) -> put(JSONObject().put("u", u).put("t", t)) } })
        .toString()

    /** The saved tabs and the current index; an empty list when nothing usable was saved. */
    fun fromJson(json: String?): Pair<List<Pair<String, String>>, Int> = try {
        val o = JSONObject(json ?: "")
        val arr = o.getJSONArray("tabs")
        val tabs = (0 until minOf(arr.length(), MAX_TABS)).map { arr.getJSONObject(it).let { j -> j.optString("u") to j.optString("t") } }
            .filter { it.first.startsWith("http") || it.first.isEmpty() }
        tabs to o.optInt("current", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    } catch (e: Exception) {
        emptyList<Pair<String, String>>() to 0
    }
}
