package com.luna.browser.data.filter

/** Builds a [FilterEngine] from any number of filter-list texts. Not thread-safe; use one per build. */
class FilterBuilder {
    private val hosts = HashSet<String>()
    private val block = RuleIndex()
    private val allow = RuleIndex()
    private val docAllow = RuleIndex()
    private val cosmeticOff = RuleIndex()
    private val genericCosmetic = ArrayList<CosmeticRule>()
    private val specificCosmetic = HashMap<String, MutableList<CosmeticRule>>()
    private val cosmeticExceptions = HashMap<String, MutableList<String>>()

    /** Parses one list. Returns the number of rules accepted. */
    fun add(text: String): Int {
        var n = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
            if (runCatching { parseLine(line) }.getOrDefault(false)) n++
        }
        return n
    }

    fun build() = FilterEngine(hosts, block, allow, docAllow, cosmeticOff, genericCosmetic, specificCosmetic, cosmeticExceptions)

    private val hostsLine = Regex("^(?:0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?)\\s+([a-z0-9.-]+)")
    private val bareHost = Regex("^[a-z0-9-]+(?:\\.[a-z0-9-]+)+$")
    private val pureHostRule = Regex("^\\|\\|([a-z0-9.-]+)\\^?\\|?$")
    private val proceduralMarkers = listOf(":-abp-", ":has-text(", ":style(", ":remove(", ":upward(", ":xpath(", ":matches-css", ":min-text-length", ":watch-attr", ":others(", ":matches-path", ":matches-attr")

    private fun parseLine(line: String): Boolean {
        // hosts-file formats
        // "# comment" in hosts files — but "##sel" / "#@#sel" are cosmetic rules.
        if (line.startsWith("#") && !line.startsWith("##") && !line.startsWith("#@#")) return false
        val lower = line.lowercase()
        hostsLine.find(lower)?.let { m ->
            val h = m.groupValues[1]
            if (h != "localhost" && h.contains('.')) { hosts.add(h); return true }
            return false
        }
        if (bareHost.matches(lower)) { hosts.add(lower); return true }

        // cosmetic
        if (line.contains("#?#") || line.contains("#$#") || line.contains("#%#") || line.contains("##+js") || line.contains("#@?#") || line.contains("#@$#")) return false
        val exc = line.indexOf("#@#")
        val cos = line.indexOf("##")
        if (exc >= 0 || cos >= 0) {
            val isException = exc >= 0
            val at = if (isException) exc else cos
            val selector = line.substring(at + if (isException) 3 else 2).trim()
            if (selector.isEmpty() || proceduralMarkers.any { selector.contains(it) }) return false
            val domains = line.substring(0, at).lowercase().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val inc = domains.filter { !it.startsWith("~") }
            val exl = domains.filter { it.startsWith("~") }.map { it.substring(1) }
            if (isException) {
                val list = cosmeticExceptions.getOrPut(selector) { ArrayList() }
                if (inc.isEmpty()) list.add("") else list.addAll(inc)
                return true
            }
            val rule = CosmeticRule(selector, null, exl.takeIf { it.isNotEmpty() }?.toTypedArray())
            if (inc.isEmpty()) genericCosmetic.add(rule) else inc.forEach { specificCosmetic.getOrPut(it) { ArrayList(2) }.add(rule) }
            return true
        }
        return parseNetwork(line)
    }

    private fun parseNetwork(line: String): Boolean {
        var s = line
        val exception = s.startsWith("@@")
        if (exception) s = s.substring(2)

        var opts = ""
        // A regex rule is /…/ optionally followed by $options. "/vast.js$script" is a path, not a regex.
        val lastSlash = s.lastIndexOf('/')
        val isRegex = s.startsWith("/") && lastSlash > 0 && (lastSlash == s.length - 1 || s[lastSlash + 1] == '$')
        if (isRegex) {
            val after = s.substring(lastSlash + 1)
            if (after.startsWith("$")) { opts = after.substring(1); s = s.substring(0, lastSlash + 1) }
        } else {
            val d = s.lastIndexOf('$')
            if (d >= 0 && !s.substring(d + 1).contains('/')) { opts = s.substring(d + 1); s = s.substring(0, d) }
        }

        var types = 0
        var negTypes = 0
        var party = 0
        var include: Array<String>? = null
        var exclude: Array<String>? = null
        var docException = false
        var cosmeticException = false
        for (o in opts.split(',')) {
            val opt = o.trim()
            if (opt.isEmpty()) continue
            val neg = opt.startsWith("~")
            val name = opt.removePrefix("~").substringBefore('=').lowercase()
            val bit = when (name) {
                "script" -> Type.SCRIPT
                "image" -> Type.IMAGE
                "stylesheet", "css" -> Type.STYLESHEET
                "subdocument", "frame" -> Type.SUBDOCUMENT
                "xmlhttprequest", "xhr" -> Type.XHR
                "media" -> Type.MEDIA
                "font" -> Type.FONT
                "object" -> Type.OBJECT
                "other" -> Type.OTHER
                "ping", "beacon" -> Type.PING
                "websocket" -> Type.WEBSOCKET
                "popup" -> Type.POPUP
                "document", "doc" -> Type.DOCUMENT
                else -> 0
            }
            if (bit != 0) {
                if (neg) negTypes = negTypes or bit else types = types or bit
                if (bit == Type.DOCUMENT && exception) docException = true
                continue
            }
            when (name) {
                "domain", "from" -> {
                    val parts = opt.substringAfter('=').lowercase().split('|').filter { it.isNotEmpty() }
                    include = parts.filter { !it.startsWith("~") }.toTypedArray().takeIf { it.isNotEmpty() }
                    exclude = parts.filter { it.startsWith("~") }.map { it.substring(1) }.toTypedArray().takeIf { it.isNotEmpty() }
                }
                "third-party", "3p" -> party = if (neg) -1 else 1
                "first-party", "1p" -> party = if (neg) 1 else -1
                "all" -> types = types or Type.DEFAULT or Type.DOCUMENT or Type.POPUP
                "elemhide", "ehide", "generichide", "ghide", "specifichide", "shide" -> if (exception) cosmeticException = true else return false
                // Harmless modifiers: the rule still blocks (or allows) as usual.
                "match-case", "important", "rewrite", "redirect", "redirect-rule", "empty", "mp4", "strict1p", "strict3p" -> {}
                // Anything else (csp=, removeparam=, header=, badfilter, …) changes behaviour we don't model.
                else -> return false
            }
        }
        val mask = when {
            types != 0 -> types
            negTypes != 0 -> Type.DEFAULT and negTypes.inv()
            else -> Type.DEFAULT
        }

        val lower = s.lowercase()
        val target = when {
            cosmeticException -> cosmeticOff
            docException -> docAllow
            exception -> allow
            else -> block
        }
        val finalMask = if (cosmeticException || docException) Type.DOCUMENT else mask

        // Fast path: `||host^` with no options worth checking per request.
        val pure = pureHostRule.find(lower)
        if (pure != null) {
            val host = pure.groupValues[1]
            if (target === block && opts.isEmpty()) { hosts.add(host); return true }
            target.addHostRule(host, NetRule(line, null, finalMask, party, include, exclude))
            return true
        }

        val regex = toRegex(lower) ?: return false
        target.addPatternRule(bestToken(lower), NetRule(line, regex, finalMask, party, include, exclude))
        return true
    }

    private fun toRegex(p: String): Regex? {
        if (p.isEmpty()) return Regex(".*") // e.g. `$popup,domain=site.com`: every URL
        if (p.length > 2 && p.startsWith("/") && p.endsWith("/")) return runCatching { Regex(p.substring(1, p.length - 1), RegexOption.IGNORE_CASE) }.getOrNull()
        val sb = StringBuilder()
        var s = p
        when {
            s.startsWith("||") -> { sb.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?"); s = s.substring(2) }
            s.startsWith("|") -> { sb.append('^'); s = s.substring(1) }
        }
        val anchoredEnd = s.endsWith("|")
        if (anchoredEnd) s = s.dropLast(1)
        for (c in s) {
            when (c) {
                '*' -> sb.append(".*")
                '^' -> sb.append("(?:[^a-z0-9_.%-]|$)")
                '.', '+', '?', '{', '}', '(', ')', '[', ']', '\\', '|', '$', '/' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        if (anchoredEnd) sb.append('$')
        return runCatching { Regex(sb.toString(), RegexOption.IGNORE_CASE) }.getOrNull()
    }

    /** Longest literal token of the pattern, used to index the rule (null = check on every request). */
    private fun bestToken(p: String): String? {
        if (p.startsWith("/") && p.endsWith("/")) return null
        val body = p.removePrefix("||").removePrefix("|")
        var best: String? = null
        Regex("[a-z0-9%]{3,}").findAll(body).forEach { m ->
            val before = if (m.range.first > 0) body[m.range.first - 1] else ' '
            val after = if (m.range.last + 1 < body.length) body[m.range.last + 1] else ' '
            if (before == '*' || after == '*') return@forEach // a wildcard can extend it
            if (best == null || m.value.length > best!!.length) best = m.value
        }
        return best
    }
}
