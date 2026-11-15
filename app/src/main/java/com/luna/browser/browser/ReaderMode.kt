package com.luna.browser.browser

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** One piece of an article: kind is h2, h3, p, li, quote, pre or img (then [url] is the image address). */
class Block(val kind: String, val text: String, val url: String? = null)

class Article(val title: String, val site: String, val blocks: List<Block>, val lang: String = "")

/**
 * Reading mode: a clean, large-type, dark view of the article on the current page, made for reading from the sofa.
 *
 * The page is only asked for its text ([EXTRACT_JS]); the reading page is then built here, from escaped text, and loaded
 * as a separate document. The original page's scripts and styles never run in it, and nothing from the page can inject
 * markup (every string is escaped, images must be http or https).
 */
object ReaderMode {
    const val MIN_CHARS = 500
    private val kinds = setOf("h2", "h3", "p", "li", "quote", "pre", "img")

    /** Parses what [EXTRACT_JS] returned (evaluateJavascript hands back a JSON string literal). Null when there is no article. */
    fun parse(raw: String?): Article? {
        if (raw == null || raw == "null" || raw.isBlank()) return null
        return try {
            val v = JSONTokener(raw).nextValue()
            val o = when (v) {
                is JSONObject -> v
                is String -> JSONObject(v)
                else -> return null
            }
            val arr = o.optJSONArray("blocks") ?: JSONArray()
            val blocks = (0 until minOf(arr.length(), 500)).mapNotNull { i ->
                val b = arr.optJSONObject(i) ?: return@mapNotNull null
                val k = b.optString("k")
                val t = b.optString("t").trim()
                val u = b.optString("u").ifEmpty { null }
                when {
                    k !in kinds -> null
                    k == "img" -> if (u != null && isWeb(u)) Block(k, t.take(300), u) else null
                    t.isEmpty() -> null
                    else -> Block(k, t.take(20_000))
                }
            }
            val chars = blocks.filter { it.kind != "img" }.sumOf { it.text.length }
            val lang = o.optString("lang").trim().take(20).takeIf { l -> l.matches(Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*")) } ?: ""
            val article = Article(o.optString("title").trim().take(300), o.optString("site").trim().take(120), blocks, lang)
            if (chars < MIN_CHARS || blocks.count { it.kind != "img" } < 3) null else article
        } catch (e: Exception) {
            null
        }
    }

    /** Text sizes of the reading page in px; the middle-low one is the original size. */
    val sizes = listOf(17, 19, 22, 26, 30)
    const val DEFAULT_SIZE = 1

    fun clampSize(level: Int) = level.coerceIn(0, sizes.lastIndex)

    fun sizePx(level: Int) = sizes[clampSize(level)]

    /** The size as a percentage of the default, for display ("116%"). */
    fun sizePercent(level: Int) = Math.round(sizePx(level) * 100f / sizes[DEFAULT_SIZE])

    /** The blocks that are read aloud: (index in [Article.blocks], text). Pictures are skipped. */
    fun speakable(a: Article): List<Pair<Int, String>> = a.blocks.mapIndexedNotNull { i, b -> if (b.kind == "img") null else i to b.text }

    /** Splits [text] into pieces of at most [max] characters, at sentence ends where possible (speech engines refuse long input). */
    fun chunks(text: String, max: Int = 3500): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        if (t.length <= max) return listOf(t)
        val out = ArrayList<String>()
        var rest = t
        while (rest.length > max) {
            val window = rest.substring(0, max)
            var cut = maxOf(window.lastIndexOf(". "), window.lastIndexOf("? "), window.lastIndexOf("! "), window.lastIndexOf("。"))
            if (cut < max / 2) cut = window.lastIndexOf(' ')
            if (cut < max / 4) cut = max - 1
            out.add(rest.substring(0, cut + 1).trim())
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out.filter { it.isNotEmpty() }
    }

    private fun isWeb(u: String) = u.startsWith("https://") || u.startsWith("http://")

    fun words(a: Article) = a.blocks.filter { it.kind != "img" }.sumOf { b -> b.text.split(Regex("\\s+")).count { it.isNotEmpty() } }

    /** Reading time in minutes at about 200 words a minute (never less than 1). CJK text has no spaces, so it counts characters / 3. */
    fun minutes(a: Article): Int {
        val chars = a.blocks.filter { it.kind != "img" }.sumOf { it.text.length }
        val w = words(a)
        val effective = if (w > 0 && chars / w.toDouble() > 12) chars / 3.0 else w.toDouble()
        return maxOf(1, Math.round(effective / 200.0).toInt())
    }

    fun esc(s: String): String {
        val out = StringBuilder(s.length + 16)
        for (c in s) when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(c)
        }
        return out.toString()
    }

    /** The reading page. [minutesLabel] is already translated ("5 min read"); [sizeLevel] indexes [sizes]. */
    fun html(a: Article, minutesLabel: String, sizeLevel: Int = DEFAULT_SIZE): String {
        val sb = StringBuilder(4096)
        sb.append("<!doctype html><html dir=\"auto\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta name=\"color-scheme\" content=\"dark\">")
        sb.append("<title>").append(esc(a.title)).append("</title><style>:root{--fs:").append(sizePx(sizeLevel)).append("px}").append(CSS).append("</style></head><body><main>")
        if (a.site.isNotEmpty()) sb.append("<div class=\"site\">").append(esc(a.site)).append("</div>")
        if (a.title.isNotEmpty()) sb.append("<h1>").append(esc(a.title)).append("</h1>")
        sb.append("<div class=\"meta\">").append(esc(minutesLabel)).append("</div>")
        var inList = false
        for ((i, b) in a.blocks.withIndex()) {
            if (b.kind != "li" && inList) { sb.append("</ul>"); inList = false }
            when (b.kind) {
                "h2" -> sb.append("<h2 data-i=\"").append(i).append("\">").append(esc(b.text)).append("</h2>")
                "h3" -> sb.append("<h3 data-i=\"").append(i).append("\">").append(esc(b.text)).append("</h3>")
                "p" -> sb.append("<p data-i=\"").append(i).append("\">").append(esc(b.text)).append("</p>")
                "quote" -> sb.append("<blockquote data-i=\"").append(i).append("\">").append(esc(b.text)).append("</blockquote>")
                "pre" -> sb.append("<pre data-i=\"").append(i).append("\">").append(esc(b.text)).append("</pre>")
                "li" -> { if (!inList) { sb.append("<ul>"); inList = true }; sb.append("<li data-i=\"").append(i).append("\">").append(esc(b.text)).append("</li>") }
                "img" -> {
                    sb.append("<figure><img src=\"").append(esc(b.url ?: "")).append("\" alt=\"\">")
                    if (b.text.isNotEmpty()) sb.append("<figcaption>").append(esc(b.text)).append("</figcaption>")
                    sb.append("</figure>")
                }
            }
        }
        if (inList) sb.append("</ul>")
        sb.append("</main></body></html>")
        return sb.toString()
    }

    private const val CSS =
        "html{background:#0b0d12}body{margin:0;color:#e9ecf7;font-size:var(--fs,19px);line-height:1.75;font-family:Roboto,'Segoe UI',system-ui,sans-serif;-webkit-font-smoothing:antialiased}" +
            "main{max-width:34em;margin:0 auto;padding:44px 28px 140px}" +
            ".site{color:#8aa4ff;font-size:.74em;letter-spacing:.08em;text-transform:uppercase;font-weight:600}" +
            "h1{font-size:1.68em;line-height:1.25;margin:.3em 0 .25em;color:#fff}" +
            ".meta{color:#9aa3c4;font-size:.8em;margin:0 0 1.4em;padding-bottom:1em;border-bottom:1px solid #292e4a}" +
            "h2{font-size:1.32em;line-height:1.3;margin:1.4em 0 .3em;color:#fff}h3{font-size:1.1em;line-height:1.3;margin:1.2em 0 .25em;color:#f1f3fb}" +
            "p{margin:0 0 .95em}ul{margin:0 0 .95em;padding-left:1.4em}li{margin:.3em 0}" +
            "blockquote{margin:1.1em 0;padding:.2em 0 .2em 1em;border-left:4px solid #8aa4ff;color:#c9d1ee}" +
            "pre{background:#151826;padding:.9em;border-radius:12px;font-size:.8em;line-height:1.5;white-space:pre-wrap;word-break:break-word}" +
            "figure{margin:1.4em 0}img{max-width:100%;height:auto;border-radius:12px;display:block}" +
            "figcaption{color:#9aa3c4;font-size:.74em;line-height:1.5;margin-top:.5em}" +
            "[data-i]{transition:background .25s,box-shadow .25s}.now{background:#1c2244;box-shadow:-14px 0 0 #1c2244,14px 0 0 #1c2244;border-radius:6px}"

    /** Script that sets the text size of the open reading page (no reload, the scroll position stays). */
    fun setSizeJs(level: Int) = "document.documentElement.style.setProperty('--fs','" + sizePx(level) + "px')"

    /** Script that marks block [i] as the one being read aloud and scrolls it to the middle of the screen. */
    fun highlightJs(i: Int) = "(function(i){var o=document.querySelector('.now');if(o)o.classList.remove('now');" +
        "var e=document.querySelector('[data-i=\"'+i+'\"]');if(e){e.classList.add('now');e.scrollIntoView({block:'center',behavior:'smooth'})}})(" + i.coerceAtLeast(0) + ")"

    const val CLEAR_HIGHLIGHT_JS = "(function(){var o=document.querySelector('.now');if(o)o.classList.remove('now')})()"

    /**
     * Runs in the page. Finds the block of the page that holds most of the running text (paragraph length, discounted by
     * link density, skipping navigation, sidebars, comments and the like) and returns its headings, paragraphs, lists,
     * quotes, code and large images as JSON text. Returns null when the page has no article.
     */
    const val EXTRACT_JS = """(function(){try{
var SKIP=/(^|[\s_-])(comments?|share|sharing|social|related|promo|adverts?|ads?|sidebar|newsletter|subscribe|footer|breadcrumbs?|cookie|popup|modal|widget|recommended|outbrain|taboola)([\s_-]|${'$'})/i;
function T(e){return (e.textContent||'').replace(/\s+/g,' ').trim()}
function bad(e){if(!e||e.nodeType!==1)return false;var g=e.tagName;
if(/^(NAV|ASIDE|FOOTER|FORM|SCRIPT|STYLE|NOSCRIPT|IFRAME|BUTTON|SVG|DIALOG)${'$'}/i.test(g))return true;
var c=(typeof e.className==='string'?e.className:'')+' '+(e.id||'');return SKIP.test(c)}
function ld(e){var t=T(e).length;if(!t)return 1;var l=0,a=e.getElementsByTagName('a');for(var i=0;i<a.length;i++)l+=T(a[i]).length;return l/t}
var score=new Map(),ps=document.querySelectorAll('p');
for(var i=0;i<ps.length&&i<3000;i++){var p=ps[i],t=T(p);if(t.length<50)continue;
var skip=false;for(var a=p;a&&a!==document.body;a=a.parentElement){if(bad(a)){skip=true;break}}
if(skip)continue;var par=p.parentElement;if(!par)continue;var s=Math.min(t.length,400);
score.set(par,(score.get(par)||0)+s);var gp=par.parentElement;if(gp)score.set(gp,(score.get(gp)||0)+s/2)}
var best=null,bs=0;score.forEach(function(v,k){var s2=v*(1-Math.min(ld(k),0.9));if(s2>bs){bs=s2;best=k}});
if(!best||bs<250)return null;
var art=best.closest('article');if(art&&T(art).length<T(best).length*3)best=art;
var blocks=[],chars=0;
function add(k,t,u){if(blocks.length>=400||chars>120000)return;blocks.push(u?{k:k,t:t,u:u}:{k:k,t:t});chars+=t.length}
function walk(n){if(blocks.length>=400)return;
for(var c=n.firstElementChild;c;c=c.nextElementSibling){if(bad(c))continue;var g=c.tagName.toLowerCase(),t;
if(/^h[1-6]${'$'}/.test(g)){t=T(c);if(t&&t.length<200)add(g==='h1'||g==='h2'?'h2':'h3',t)}
else if(g==='p'){t=T(c);if(t.length>2&&!(t.length<120&&ld(c)>0.6))add('p',t)}
else if(g==='ul'||g==='ol'){var li=c.children;for(var j=0;j<li.length;j++){if(li[j].tagName==='LI'){t=T(li[j]);if(t&&t.length<600)add('li',t)}}}
else if(g==='blockquote'){t=T(c);if(t)add('quote',t.slice(0,2000))}
else if(g==='pre'){t=(c.textContent||'').trim();if(t)add('pre',t.slice(0,3000))}
else if(g==='figure'||g==='img'||g==='picture'){var im=g==='img'?c:c.querySelector('img');
if(im){var u=im.currentSrc||im.src||'',w=im.naturalWidth||parseInt(im.getAttribute('width')||'0',10);
if(/^https?:\/\//i.test(u)&&w>=240){var fc=g==='figure'?c.querySelector('figcaption'):null;blocks.push({k:'img',t:fc?T(fc).slice(0,200):'',u:u})}}}
else walk(c)}}
walk(best);
if(chars<500||blocks.length<3)return null;
var h=best.querySelector('h1')||document.querySelector('h1');
return JSON.stringify({title:(h?T(h):document.title||'').slice(0,200),site:location.hostname.replace(/^www\./,''),lang:(document.documentElement.lang||'').slice(0,20),blocks:blocks})
}catch(e){return null}})()"""
}
