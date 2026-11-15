package com.luna.browser.data

import org.json.JSONArray
import org.json.JSONObject

/** A family member's space: its own history, favourites, "continue watching", open tabs, and (where the web engine allows) its own sign-ins. */
data class Profile(val id: String, val name: String, val color: Int)

/**
 * The rules behind profiles, kept apart from Android classes so they are unit-tested.
 *
 * Storage keys: the default profile (id "0") keeps the historic key names, so everything people already have stays
 * theirs; other profiles prefix their keys. The guest is a profile that is erased when it ends.
 */
object ProfileRules {
    const val DEFAULT = "0"
    const val GUEST = "guest"
    const val MAX = 5
    const val MAX_NAME = 20

    /** Avatar colours (ARGB), in the order new profiles take them. */
    val colors: List<Long> = listOf(0xFF8AA4FF, 0xFFB48CFF, 0xFF4FE3A0, 0xFFFFB454, 0xFFFF7A8A, 0xFF4FC3F7, 0xFFE0E36C, 0xFFFF8FD8)

    /** The SharedPreferences key for [name] of profile [id]. */
    fun key(id: String, name: String) = if (id == DEFAULT) name else "p$id.$name"

    /** The per-profile data keys (wiped when a profile is deleted or the guest ends). */
    val dataKeys = listOf("favorites", "history", "visits", "positions", "tabs", "last")

    fun cleanName(raw: String): String =
        raw.filter { !it.isISOControl() }.replace(Regex("\\s+"), " ").trim().take(MAX_NAME)

    fun isValidId(id: String) = id == GUEST || id.matches(Regex("[0-9]"))

    /** A free id for a new profile ("1".."9"), or null when the limit is reached. */
    fun nextId(profiles: List<Profile>): String? {
        if (profiles.size >= MAX) return null
        return (1..9).map { it.toString() }.firstOrNull { id -> profiles.none { it.id == id } }
    }

    /** The first colour no profile uses yet, else the next in turn. */
    fun nextColor(profiles: List<Profile>): Int =
        colors.indices.firstOrNull { c -> profiles.none { it.color == c } } ?: (profiles.size % colors.size)

    fun initial(name: String): String = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    fun serialize(list: List<Profile>): String =
        JSONArray().apply { list.forEach { put(JSONObject().put("i", it.id).put("n", it.name).put("c", it.color)) } }.toString()

    /** Reads the saved list; always returns at least the default profile, first, with no duplicate or invalid ids. */
    fun parse(json: String?): List<Profile> {
        val out = ArrayList<Profile>()
        try {
            val arr = JSONArray(json ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("i")
                if (id == GUEST || !isValidId(id) || out.any { it.id == id }) continue
                out.add(Profile(id, cleanName(o.optString("n")), o.optInt("c", 0).coerceIn(0, colors.size - 1)))
            }
        } catch (e: Exception) {
            out.clear()
        }
        if (out.none { it.id == DEFAULT }) out.add(0, Profile(DEFAULT, "", 0))
        else out.sortBy { if (it.id == DEFAULT) 0 else 1 }
        return out.take(MAX)
    }
}
