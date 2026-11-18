package com.luna.browser.data

import com.luna.browser.data.filter.siteOf
import org.json.JSONObject

/**
 * Settings that differ from the global ones for ONE site. Every field is nullable: null means "use the
 * global setting", so changing a global setting later still reaches every site that has no opinion.
 */
data class SiteOverride(
    val desktop: Boolean? = null,
    /** Text zoom in percent. */
    val zoom: Int? = null,
    val adblock: Boolean? = null,
    /** true = pointer mode, false = link-to-link mode. */
    val pointer: Boolean? = null,
    /** true = show this site dark, false = never darken it. */
    val dark: Boolean? = null,
) {
    val isEmpty: Boolean get() = desktop == null && zoom == null && adblock == null && pointer == null && dark == null
}

object SiteSettings {
    /** The key sites are stored under: the registrable domain, so m.example.com and www.example.com share settings. */
    fun keyOf(url: String): String {
        if (!url.startsWith("http")) return ""
        val host = try { java.net.URI(url.trim()).host } catch (e: Exception) { null } ?: return ""
        return siteOf(host.lowercase())
    }

    fun toJson(o: SiteOverride): JSONObject = JSONObject().apply {
        o.desktop?.let { put("d", it) }
        o.zoom?.let { put("z", it) }
        o.adblock?.let { put("a", it) }
        o.pointer?.let { put("p", it) }
        o.dark?.let { put("k", it) }
    }

    fun fromJson(j: JSONObject): SiteOverride = SiteOverride(
        desktop = if (j.has("d")) j.optBoolean("d") else null,
        zoom = if (j.has("z")) j.optInt("z").takeIf { it in 50..300 } else null,
        adblock = if (j.has("a")) j.optBoolean("a") else null,
        pointer = if (j.has("p")) j.optBoolean("p") else null,
        dark = if (j.has("k")) j.optBoolean("k") else null,
    )

    fun parseAll(json: String): Map<String, SiteOverride> = try {
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { fromJson(o.getJSONObject(it)) }.filterValues { !it.isEmpty }
    } catch (e: Exception) {
        emptyMap()
    }

    fun serializeAll(map: Map<String, SiteOverride>): String {
        val o = JSONObject()
        map.forEach { (k, v) -> if (!v.isEmpty) o.put(k, toJson(v)) }
        return o.toString()
    }

    /** Steps to the next option of a "default or one of these" choice (null = default), wrapping around. */
    fun <T> next(current: T?, options: List<T?>): T? = options[(options.indexOf(current) + 1).mod(options.size)]
}
