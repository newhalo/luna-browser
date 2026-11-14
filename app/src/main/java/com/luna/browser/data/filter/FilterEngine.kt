package com.luna.browser.data.filter

/**
 * A compact Adblock Plus / uBlock Origin compatible filter engine.
 *
 * Supported (enough for ABPVN, EasyList-style lists and plain hosts files):
 *  - network rules: `||host^`, `|https://`, `/path/`, `*` and `^` wildcards, `/regex/`
 *  - options: domain= (incl. ~domain and `name.*`), third-party / first-party, resource types,
 *    popup, document, all; unknown or non-blocking options (csp=, removeparam=…) skip the rule
 *  - exceptions: `@@…`, page allow-listing with `@@…$document`, `$elemhide` / `$generichide`
 *  - cosmetic: `##sel`, `a.com,b.com##sel`, `~a.com##sel`, `#@#sel`
 *  - hosts files: `0.0.0.0 host`, `127.0.0.1 host`, bare `host`
 * Procedural / scriptlet filters (`#?#`, `#$#`, `##+js`, `:-abp-…`) are skipped.
 */
object Type {
    const val SCRIPT = 1
    const val IMAGE = 2
    const val STYLESHEET = 4
    const val SUBDOCUMENT = 8
    const val XHR = 16
    const val MEDIA = 32
    const val FONT = 64
    const val OTHER = 128
    const val DOCUMENT = 256
    const val POPUP = 512
    const val PING = 1024
    const val WEBSOCKET = 2048
    const val OBJECT = 4096

    /** Types a rule without type options applies to (ABP semantics: not document, not popup). */
    const val DEFAULT = SCRIPT or IMAGE or STYLESHEET or SUBDOCUMENT or XHR or MEDIA or FONT or OTHER or PING or WEBSOCKET or OBJECT

    /** WebView can't always tell a script from an XHR; such requests match any of these. */
    const val AMBIGUOUS = SCRIPT or XHR or OTHER or OBJECT or PING
}

private val hostRe = Regex("^[a-z][a-z0-9+.-]*://(?:[^/?#@]*@)?([^/?#:]+)", RegexOption.IGNORE_CASE)

/** Host of a URL, lower-cased (pure Kotlin so the engine runs in JVM unit tests). */
fun urlHost(url: String): String? = hostRe.find(url)?.groupValues?.get(1)?.lowercase()

fun hostMatches(host: String, domain: String): Boolean {
    if (domain.endsWith(".*")) {
        val base = domain.dropLast(2)
        return host.startsWith("$base.") || host.contains(".$base.")
    }
    return host == domain || host.endsWith(".$domain")
}

/** Approximate registrable domain ("phim.abc.com.vn" -> "abc.com.vn"). */
fun siteOf(host: String): String {
    val p = host.lowercase().removePrefix("www.").split('.')
    if (p.size <= 2) return p.joinToString(".")
    val sld = setOf("com", "net", "org", "gov", "edu", "co", "ac", "info", "biz")
    val take = if (p[p.size - 2] in sld && p.last().length == 2) 3 else 2
    return p.takeLast(take).joinToString(".")
}

class NetRule(
    val text: String,
    val regex: Regex?,
    val types: Int,
    /** 1 = third-party only, -1 = first-party only, 0 = any. */
    val party: Int,
    val include: Array<String>?,
    val exclude: Array<String>?,
) {
    fun applies(type: Int, pageHost: String, thirdParty: Boolean): Boolean {
        if (types and type == 0) return false
        if (party == 1 && !thirdParty) return false
        if (party == -1 && thirdParty) return false
        if (exclude != null && exclude.any { hostMatches(pageHost, it) }) return false
        if (include != null && include.none { hostMatches(pageHost, it) }) return false
        return true
    }
}

/** Rules indexed for fast lookup: by host, by URL token, and a small generic bucket. */
class RuleIndex {
    private val byHost = HashMap<String, MutableList<NetRule>>()
    private val byToken = HashMap<String, MutableList<NetRule>>()
    private val generic = ArrayList<NetRule>()
    var size = 0
        private set

    fun addHostRule(host: String, rule: NetRule) {
        byHost.getOrPut(host) { ArrayList(1) }.add(rule); size++
    }

    fun addPatternRule(token: String?, rule: NetRule) {
        if (token == null) generic.add(rule) else byToken.getOrPut(token) { ArrayList(1) }.add(rule)
        size++
    }

    fun find(url: String, host: String, tokens: Collection<String>, pageHost: String, type: Int, third: Boolean): NetRule? {
        var h = host
        while (true) {
            byHost[h]?.firstOrNull { it.applies(type, pageHost, third) }?.let { return it }
            val i = h.indexOf('.')
            if (i < 0) break
            h = h.substring(i + 1)
        }
        for (t in tokens) {
            byToken[t]?.firstOrNull { it.applies(type, pageHost, third) && it.regex!!.containsMatchIn(url) }?.let { return it }
        }
        return generic.firstOrNull { it.applies(type, pageHost, third) && (it.regex == null || it.regex.containsMatchIn(url)) }
    }
}

class CosmeticRule(val selector: String, val include: Array<String>?, val exclude: Array<String>?)

class FilterEngine(
    private val hosts: HashSet<String>,
    private val block: RuleIndex,
    private val allow: RuleIndex,
    private val docAllow: RuleIndex,
    private val cosmeticOff: RuleIndex,
    private val genericCosmetic: List<CosmeticRule>,
    private val specificCosmetic: HashMap<String, MutableList<CosmeticRule>>,
    private val cosmeticExceptions: HashMap<String, MutableList<String>>,
) {
    val networkRuleCount get() = hosts.size + block.size
    val cosmeticRuleCount get() = genericCosmetic.size + specificCosmetic.values.sumOf { it.size }

    private val cssCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 24
    }

    /** True when the page itself is allow-listed (`@@||site^$document`). */
    fun pageAllowed(pageUrl: String): Boolean {
        val host = urlHost(pageUrl) ?: return false
        return docAllow.find(pageUrl.lowercase(), host, tokens(pageUrl), host, Type.DOCUMENT, false) != null
    }

    /** The blocking rule for this request, or null if it may load. */
    fun match(url: String, pageUrl: String, type: Int): String? {
        val u = url.lowercase()
        val host = urlHost(u) ?: return null
        val pageHost = urlHost(pageUrl) ?: host
        val third = siteOf(host) != siteOf(pageHost)
        val toks = tokens(u)
        // Plain host entries block every request and navigation to that host, but not pop-up
        // matching (pop-ups are decided by $popup rules and the same-site policy).
        var hit: String? = if (type != Type.POPUP && hostListed(host)) "||$host^" else null
        if (hit == null) hit = block.find(u, host, toks, pageHost, type, third)?.text
        if (hit == null) return null
        if (allow.find(u, host, toks, pageHost, type, third) != null) return null
        if (pageAllowed(pageUrl)) return null
        return hit
    }

    private fun hostListed(host: String): Boolean {
        var h = host
        while (true) {
            if (h in hosts) return true
            val i = h.indexOf('.')
            if (i < 0 || h.indexOf('.', i + 1) < 0) return false
            h = h.substring(i + 1)
        }
    }

    /** CSS that hides ad elements on this page. */
    fun css(pageUrl: String): String {
        val host = urlHost(pageUrl) ?: return ""
        synchronized(cssCache) { cssCache[host]?.let { return it } }
        val off = cosmeticOff.find(pageUrl.lowercase(), host, tokens(pageUrl), host, Type.DOCUMENT, false) != null
        val out = LinkedHashSet<String>()
        if (!off && !pageAllowed(pageUrl)) {
            fun ok(r: CosmeticRule) = (r.exclude == null || r.exclude.none { hostMatches(host, it) }) &&
                (r.include == null || r.include.any { hostMatches(host, it) })
            genericCosmetic.forEach { if (ok(it)) out.add(it.selector) }
            var h = host
            while (true) {
                specificCosmetic[h]?.forEach { if (ok(it)) out.add(it.selector) }
                val i = h.indexOf('.')
                if (i < 0) break
                h = h.substring(i + 1)
            }
            // `name.*` entity rules are stored under "name.*"
            specificCosmetic.keys.filter { it.endsWith(".*") && hostMatches(host, it) }
                .forEach { k -> specificCosmetic[k]?.forEach { if (ok(it)) out.add(it.selector) } }
            out.removeAll { sel -> cosmeticExceptions[sel]?.any { it.isEmpty() || hostMatches(host, it) } == true }
        }
        // One rule per selector: an invalid selector then only drops itself, not the whole sheet.
        val css = out.joinToString("\n") { "$it{display:none!important}" }
        synchronized(cssCache) { cssCache[host] = css }
        return css
    }

    companion object {
        private val tokenRe = Regex("[a-z0-9%]{3,}")
        fun tokens(url: String): Set<String> = tokenRe.findAll(url.lowercase()).map { it.value }.toHashSet()
    }
}
