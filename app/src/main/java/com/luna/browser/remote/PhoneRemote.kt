package com.luna.browser.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** What a phone can ask the TV to do. */
enum class RemoteAction {
    Ping, Open, Search, Type, TypeEnter,
    /** A remote key: pressed once, or held ([KeyDown] repeated, then [KeyUp]). */
    Key, KeyDown, KeyUp,
    Volume, Home,
}

/**
 * A tiny web server so a phone on the same Wi-Fi can send an address, a search or text to the TV
 * (typing with a remote is the slowest part of any TV browser).
 *
 * Security model: it only runs while Luna is on screen and the setting is on; every request must carry
 * the PIN shown in Settings (paired once, the phone page remembers it); a wrong PIN five times locks
 * it for half a minute; request size and time are capped; it can only open web pages, search, and type
 * into the field that has focus. The traffic is plain HTTP on the home network — don't use it on a
 * network you don't trust. Pure Java sockets, no dependencies.
 */
class PhoneRemote(
    private val pin: () -> String,
    private val labels: () -> Map<String, String>,
    /** Runs the action on the UI thread and reports success through the callback. */
    private val perform: (RemoteAction, String, (Boolean) -> Unit) -> Unit,
    /** Total time one connection may take; a client that trickles bytes can't hold a worker longer. */
    private val budgetMs: Long = 5000,
) {
    /** The port in use, or 0 when stopped. Observable, so Settings shows the live address. */
    var port by mutableIntStateOf(0)
        private set

    @Volatile private var server: ServerSocket? = null
    // Three workers and a short queue: when busy, new connections are dropped rather than piling up.
    private val pool = ThreadPoolExecutor(
        3, 3, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(6),
        { r -> Thread(r, "luna-remote").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy(),
    )

    private var failures = 0
    private var lockedUntil = 0L

    private var wanted = false

    @Synchronized
    fun start() {
        if (wanted) return
        wanted = true
        Thread({
            val s = bind() ?: return@Thread
            synchronized(this) {
                if (!wanted) { s.close(); return@Thread }   // stopped while binding
                server = s
                port = s.localPort
            }
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Exception) { break }
                runCatching { pool.execute { serve(c) } }.onFailure { runCatching { c.close() } }
            }
        }, "luna-remote-accept").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        wanted = false
        server?.let { runCatching { it.close() } }
        server = null
        port = 0
    }

    private fun bind(): ServerSocket? {
        for (p in PORTS) {
            try {
                return ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(p)) }
            } catch (e: Exception) { /* in use: try the next one */ }
        }
        return null
    }

    // ---- HTTP ----
    private class Request(val head: String, val body: String)

    private fun serve(c: Socket) {
        c.use {
            try {
                // Only devices on the local network may talk to the TV, even if a router forwards the port.
                val peer = it.inetAddress
                if (!(peer.isLoopbackAddress || peer.isSiteLocalAddress || peer.isLinkLocalAddress)) return
                val out = it.getOutputStream()
                val req = readRequest(it) ?: return
                val lines = req.head.split("\r\n")
                val (method, path) = lines[0].split(' ').let { p -> (p.getOrNull(0) ?: "") to (p.getOrNull(1) ?: "/") }
                when {
                    method == "GET" && (path == "/" || path.startsWith("/?")) -> respond(out, 200, "text/html; charset=utf-8", page())
                    method == "POST" && path == "/api" -> {
                        val (code, json) = api(parseForm(req.body))
                        respond(out, code, "application/json; charset=utf-8", json)
                    }
                    else -> respond(out, 404, "text/plain; charset=utf-8", "Not found")
                }
            } catch (e: Exception) { /* a dropped, slow or malformed connection: nothing to do */ }
        }
    }

    /**
     * Reads one request within [budgetMs] in total (not per read), refusing heads over [MAX_HEAD] and
     * bodies over [MAX_BODY]. Returns null for anything that doesn't fit.
     */
    private fun readRequest(c: Socket): Request? {
        val deadline = System.currentTimeMillis() + budgetMs
        val input = c.getInputStream()
        val data = ByteArrayOutputStream()
        val chunk = ByteArray(2048)
        var headEnd = -1
        var length = 0
        while (true) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) return null
            c.soTimeout = left.toInt()
            val n = input.read(chunk)
            if (n < 0) return null
            data.write(chunk, 0, n)
            if (data.size() > MAX_HEAD + MAX_BODY) return null
            val b = data.toByteArray()
            if (headEnd < 0) {
                headEnd = indexOfHeadEnd(b)
                if (headEnd < 0) {
                    if (b.size > MAX_HEAD) return null
                    continue
                }
                if (headEnd > MAX_HEAD) return null
                val head = String(b, 0, headEnd, Charsets.ISO_8859_1)
                length = head.split("\r\n").drop(1)
                    .firstOrNull { l -> l.startsWith("content-length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                if (length !in 0..MAX_BODY) return Request(head, "")
            }
            if (b.size - (headEnd + 4) >= length) {
                val head = String(b, 0, headEnd, Charsets.ISO_8859_1)
                return Request(head, String(b, headEnd + 4, length, Charsets.UTF_8))
            }
        }
    }

    private fun indexOfHeadEnd(b: ByteArray): Int {
        for (i in 0..b.size - 4) {
            if (b[i] == 13.toByte() && b[i + 1] == 10.toByte() && b[i + 2] == 13.toByte() && b[i + 3] == 10.toByte()) return i
        }
        return -1
    }

    private fun parseForm(body: String): Map<String, String> = body.split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i < 0) null else URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }.toMap()

    private fun respond(out: java.io.OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    // ---- API ----
    private fun api(form: Map<String, String>): Pair<Int, String> {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now < lockedUntil) return 429 to reply(false, "locked")
        }
        val given = form["pin"] ?: ""
        val ok = MessageDigest.isEqual(given.toByteArray(), pin().toByteArray())
        synchronized(this) {
            if (!ok) {
                if (++failures >= MAX_FAILURES) { lockedUntil = now + LOCK_MS; failures = 0 }
                return 401 to reply(false, "pin")
            }
            failures = 0
        }
        val action = when (form["act"]) {
            "ping" -> RemoteAction.Ping
            "open" -> RemoteAction.Open
            "search" -> RemoteAction.Search
            "type" -> RemoteAction.Type
            "enter" -> RemoteAction.TypeEnter
            "key" -> RemoteAction.Key
            "kd" -> RemoteAction.KeyDown
            "ku" -> RemoteAction.KeyUp
            "vol" -> RemoteAction.Volume
            "home" -> RemoteAction.Home
            else -> return 400 to reply(false, "act")
        }
        val text = (form["text"] ?: "").take(MAX_TEXT)
        if (action != RemoteAction.Ping && action != RemoteAction.Home && text.isBlank()) return 400 to reply(false, "text")
        val latch = CountDownLatch(1)
        var success = false
        perform(action, text) { success = it; latch.countDown() }
        latch.await(3, TimeUnit.SECONDS)
        return 200 to reply(success, if (success) "" else "nofield")
    }

    private fun reply(ok: Boolean, error: String) = JSONObject().put("ok", ok).put("error", error).toString()

    private fun page(): String {
        val l = labels()
        var html = PAGE
        l.forEach { (k, v) -> html = html.replace("{{$k}}", escape(v)) }
        return html.replace("{{labels}}", JSONObject(l).toString().replace("</", "<\\/"))
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        private val PORTS = 8765..8775
        private const val MAX_HEAD = 8 * 1024
        private const val MAX_BODY = 8 * 1024
        private const val MAX_TEXT = 2000
        private const val MAX_FAILURES = 5
        private const val LOCK_MS = 30_000L

        /** The TV's address on the local network (Wi-Fi or Ethernet), or null when offline. */
        fun localAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        } catch (e: Exception) { null }

        private val PAGE = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="dark">
<title>{{ph_title}}</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;min-height:100vh;background:#0c0e18;color:#f1f3fb;font:17px/1.4 system-ui,-apple-system,Roboto,sans-serif;padding:20px max(16px,env(safe-area-inset-left))}
main{max-width:520px;margin:0 auto}h1{font-size:22px;margin:8px 0 20px;display:flex;gap:10px;align-items:center}
h1 span{color:#8aa4ff}.card{background:#171a2b;border:1px solid #262b45;border-radius:18px;padding:16px;margin-bottom:14px}
input,textarea{width:100%;background:#0c0e18;color:inherit;border:1px solid #333a5c;border-radius:12px;padding:14px;font:inherit}
input:focus,textarea:focus{outline:2px solid #8aa4ff;border-color:transparent}textarea{min-height:90px;resize:vertical}
button{width:100%;margin-top:10px;padding:14px;border:0;border-radius:12px;font:inherit;font-weight:600;color:#0c0e18;background:#8aa4ff;touch-action:manipulation;-webkit-tap-highlight-color:transparent}
.tabs{display:flex;gap:8px;margin-bottom:14px}.tabs button{margin:0;background:#171a2b;color:#aab0cc;border:1px solid #262b45}.tabs button.on{background:#8aa4ff;color:#0c0e18;border-color:#8aa4ff}
.pad{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;max-width:330px;margin:4px auto 14px}
.k{margin:0;padding:0;height:78px;font-size:26px;background:#262b45;color:#f1f3fb;user-select:none;-webkit-user-select:none}
.k:active,.k.on{background:#8aa4ff;color:#0c0e18}.pad .ok{background:#8aa4ff;color:#0c0e18;font-size:22px;border-radius:50%}
.pad .u{grid-column:2}.pad .l{grid-column:1;grid-row:2}.pad .ok{grid-column:2;grid-row:2}.pad .r{grid-column:3;grid-row:2}.pad .d{grid-column:2;grid-row:3}
.row3{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;max-width:330px;margin:0 auto 10px}.row3 .k{height:58px;font-size:22px}
button.alt{background:#262b45;color:#f1f3fb}p{margin:0 0 10px;color:#aab0cc}#msg{min-height:1.4em;color:#9be3b0}#msg.err{color:#ff9a9a}
</style></head><body><main>
<h1><span>&#9790;</span> {{ph_title}}</h1>
<div id="pair" class="card" hidden><p>{{ph_pin}}</p><input id="pin" inputmode="numeric" maxlength="8" autocomplete="off"><button id="go">{{ph_pair}}</button></div>
<div id="app" hidden>
<div class="tabs"><button id="tabRemote" class="on">{{ph_tab_remote}}</button><button id="tabType">{{ph_tab_type}}</button></div>
<div id="remote" class="card">
<div class="pad"><button class="k u hold" data-k="DPAD_UP">&#9650;</button><button class="k l hold" data-k="DPAD_LEFT">&#9664;</button>
<button class="k ok tap" data-a="key" data-k="DPAD_CENTER">OK</button><button class="k r hold" data-k="DPAD_RIGHT">&#9654;</button><button class="k d hold" data-k="DPAD_DOWN">&#9660;</button></div>
<div class="row3"><button class="k tap" data-a="key" data-k="BACK">&#8617;</button><button class="k tap" data-a="home" data-k="1">&#8962;</button><button class="k tap" data-a="key" data-k="MENU">&#9776;</button></div>
<div class="row3"><button class="k tap" data-a="key" data-k="MEDIA_REWIND">&#9194;</button><button class="k tap" data-a="key" data-k="MEDIA_PLAY_PAUSE">&#9199;</button><button class="k tap" data-a="key" data-k="MEDIA_FAST_FORWARD">&#9193;</button></div>
<div class="row3"><button class="k tap" data-a="vol" data-k="down">&#128265;</button><button class="k tap" data-a="vol" data-k="mute">&#128263;</button><button class="k tap" data-a="vol" data-k="up">&#128266;</button></div>
</div>
<div id="typing" class="card" hidden><textarea id="text" placeholder="{{ph_hint}}" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
<button id="open">{{ph_open}}</button><button id="search" class="alt">{{ph_search}}</button><button id="type" class="alt">{{ph_type}}</button><button id="enter" class="alt">{{ph_enter}}</button></div>
</div><div id="msg"></div></main><script>
var L=__LABELS__,pin=null;try{pin=localStorage.getItem('luna.pin')}catch(e){}
var qr=(location.hash||'').match(/pin=(\d{1,8})/);if(qr){pin=qr[1];try{history.replaceState(null,'',location.pathname)}catch(e){}}
function $(i){return document.getElementById(i)}
function say(t,e){var m=$('msg');m.textContent=t||'';m.className=e?'err':''}
function call(act,text){var b='pin='+encodeURIComponent(pin||'')+'&act='+act+'&text='+encodeURIComponent(text||'');
return fetch('/api',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b}).then(function(r){return r.json().then(function(j){j.status=r.status;return j})})}
function show(paired){$('pair').hidden=paired;$('app').hidden=!paired}
function tab(t){$('remote').hidden=t!=='remote';$('typing').hidden=t!=='type';$('tabRemote').className=t==='remote'?'on':'';$('tabType').className=t==='type'?'on':''}
$('tabRemote').onclick=function(){tab('remote')};$('tabType').onclick=function(){tab('type')};
function key(act,k){var b='pin='+encodeURIComponent(pin||'')+'&act='+act+'&text='+encodeURIComponent(k);
return fetch('/api',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b}).then(function(r){return r.json()}).then(function(j){if(!j.ok&&(j.error==='pin'||j.error==='locked'))result(j)}).catch(function(){})}
function buzz(){try{navigator.vibrate&&navigator.vibrate(10)}catch(e){}}
var holds={};
function press(el){var k=el.dataset.k;if(holds[k])return;buzz();el.classList.add('on');var h={t0:Date.now()};holds[k]=h;key('kd',k);
h.delay=setTimeout(function(){h.iv=setInterval(function(){key('kd',k)},110)},380)}
function release(el){var k=el.dataset.k,h=holds[k];if(!h)return;delete holds[k];el.classList.remove('on');clearTimeout(h.delay);clearInterval(h.iv);
setTimeout(function(){key('ku',k)},Math.max(0,130-(Date.now()-h.t0)))}
Array.prototype.forEach.call(document.querySelectorAll('.hold'),function(el){
el.addEventListener('pointerdown',function(e){e.preventDefault();press(el)});
['pointerup','pointercancel','pointerleave'].forEach(function(n){el.addEventListener(n,function(){release(el)})});
el.addEventListener('contextmenu',function(e){e.preventDefault()})});
Array.prototype.forEach.call(document.querySelectorAll('.tap'),function(el){el.addEventListener('click',function(){buzz();key(el.dataset.a,el.dataset.k)});
el.addEventListener('contextmenu',function(e){e.preventDefault()})});
function result(j){if(j.ok){say(L.ph_sent);return}if(j.error==='pin'){try{localStorage.removeItem('luna.pin')}catch(e){}pin=null;show(false);say(L.ph_bad_pin,1)}
else if(j.error==='locked')say(L.ph_locked,1);else if(j.error==='nofield')say(L.ph_nofield,1);else say('?',1)}
function send(act){var t=$('text').value;if(!t.trim())return;say('...');call(act,t).then(result).catch(function(){say('?',1)})}
$('go').onclick=function(){pin=$('pin').value.trim();call('ping','').then(function(j){if(j.ok){try{localStorage.setItem('luna.pin',pin)}catch(e){}show(true);say('')}else result(j)})};
$('open').onclick=function(){send('open')};$('search').onclick=function(){send('search')};$('type').onclick=function(){send('type')};$('enter').onclick=function(){send('enter')};
if(pin){call('ping','').then(function(j){if(j.ok){try{localStorage.setItem('luna.pin',pin)}catch(e){}show(true)}else{show(false);result(j)}}).catch(function(){show(false)})}else show(false);
</script></body></html>""".replace("__LABELS__", "{{labels}}")
    }
}
