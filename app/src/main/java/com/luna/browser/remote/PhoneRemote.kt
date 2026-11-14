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
    /** Touchpad: pointer movement and scroll in per-mille of the pad ("dx,dy"), and a tap (click). */
    Move, Scroll, Tap,
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
    /** Time one request may take from its first byte; a client that trickles bytes can't hold a worker longer. */
    private val budgetMs: Long = 5000,
    /** How long a connection may sit idle between two requests (keep-alive) before it is closed. */
    private val idleMs: Long = 8000,
) {
    /** The port in use, or 0 when stopped. Observable, so Settings shows the live address. */
    var port by mutableIntStateOf(0)
        private set

    @Volatile private var server: ServerSocket? = null
    // A few workers and a short queue: when busy, new connections are dropped rather than piling up. A phone
    // keeps its connection open between requests (smooth touchpad), so each paired phone holds one worker.
    private val pool = ThreadPoolExecutor(
        8, 8, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        { r -> Thread(r, "luna-remote").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy(),
    )

    /** Wrong-PIN bookkeeping per client address: one stale phone can't lock the others out. */
    private class Fails(var count: Int = 0, var until: Long = 0L)

    private val fails = HashMap<String, Fails>()
    private var globalFails = 0
    private var globalWindow = 0L
    private var globalUntil = 0L

    private var wanted = false
    private val open = java.util.Collections.synchronizedSet(HashSet<Socket>())

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
        // Open keep-alive connections must not outlive "Luna is on screen".
        synchronized(open) { open.toList() }.forEach { runCatching { it.close() } }
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
    private class Request(val head: String, val body: String, val keepAlive: Boolean)

    private fun serve(c: Socket) {
        open.add(c)
        c.use {
            try {
                // Only devices on the local network may talk to the TV, even if a router forwards the port.
                val peer = it.inetAddress
                if (!(peer.isLoopbackAddress || peer.isSiteLocalAddress || peer.isLinkLocalAddress)) return
                it.tcpNoDelay = true
                val out = it.getOutputStream()
                var served = 0
                while (served < MAX_PER_CONNECTION && wanted) {
                    // The first request must arrive promptly; later ones may wait (the phone is just idle).
                    val req = readRequest(it, if (served == 0) budgetMs else idleMs) ?: return
                    served++
                    val keep = req.keepAlive && served < MAX_PER_CONNECTION
                    val lines = req.head.split("\r\n")
                    val (method, path) = lines[0].split(' ').let { p -> (p.getOrNull(0) ?: "") to (p.getOrNull(1) ?: "/") }
                    when {
                        method == "GET" && (path == "/" || path.startsWith("/?")) -> respond(out, 200, "text/html; charset=utf-8", page(), keep)
                        method == "POST" && path == "/api" -> {
                            val (code, json) = api(parseForm(req.body), it.inetAddress?.hostAddress ?: "")
                            respond(out, code, "application/json; charset=utf-8", json, keep)
                        }
                        else -> respond(out, 404, "text/plain; charset=utf-8", "Not found", keep)
                    }
                    if (!keep) return
                }
            } catch (e: Exception) { /* a dropped, slow or malformed connection: nothing to do */ }
            finally { open.remove(c) }
        }
    }

    /**
     * Reads one request: the first byte may take up to [firstWait]; after that the whole request must arrive
     * within [budgetMs], with a head under [MAX_HEAD] and a body under [MAX_BODY]. Returns null for anything
     * that doesn't fit, or when the client went quiet / closed.
     */
    private fun readRequest(c: Socket, firstWait: Long): Request? {
        val input = c.getInputStream()
        val data = ByteArrayOutputStream()
        val chunk = ByteArray(2048)
        var deadline = 0L
        var headEnd = -1
        var length = 0
        while (true) {
            val left = if (deadline == 0L) firstWait else deadline - System.currentTimeMillis()
            if (left <= 0) return null
            c.soTimeout = left.toInt()
            val n = try { input.read(chunk) } catch (e: java.net.SocketTimeoutException) { return null }
            if (n < 0) return null
            if (deadline == 0L) deadline = System.currentTimeMillis() + budgetMs
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
                if (length !in 0..MAX_BODY) return Request(head, "", false)
            }
            if (b.size - (headEnd + 4) >= length) {
                val head = String(b, 0, headEnd, Charsets.ISO_8859_1)
                val extra = b.size - (headEnd + 4) - length     // bytes of a pipelined next request: not supported
                val close = head.split("\r\n").drop(1).any { l -> l.startsWith("connection:", ignoreCase = true) && l.contains("close", ignoreCase = true) }
                return Request(head, String(b, headEnd + 4, length, Charsets.UTF_8), keepAlive = extra == 0 && !close)
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

    private fun respond(out: java.io.OutputStream, code: Int, type: String, body: String, keepAlive: Boolean) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n" +
            (if (keepAlive) "Connection: keep-alive\r\nKeep-Alive: timeout=${idleMs / 1000}\r\n\r\n" else "Connection: close\r\n\r\n")
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    // ---- API ----
    private fun api(form: Map<String, String>, client: String): Pair<Int, String> {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (fails.size > 64) fails.entries.removeAll { it.value.until < now && it.value.count == 0 }
            if (now < globalUntil || now < (fails[client]?.until ?: 0L)) return 429 to reply(false, "locked")
        }
        val given = form["pin"] ?: ""
        val ok = MessageDigest.isEqual(given.toByteArray(), pin().toByteArray())
        synchronized(this) {
            val f = fails.getOrPut(client) { Fails() }
            if (!ok) {
                if (++f.count >= MAX_FAILURES) { f.until = now + LOCK_MS; f.count = 0 }
                // Many different addresses all guessing: slow everyone down for a while.
                if (now - globalWindow > 60_000) { globalWindow = now; globalFails = 0 }
                if (++globalFails >= GLOBAL_FAILURES) { globalUntil = now + 2 * LOCK_MS; globalFails = 0 }
                return 401 to reply(false, "pin")
            }
            f.count = 0
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
            "mv" -> RemoteAction.Move
            "sc" -> RemoteAction.Scroll
            "tap" -> RemoteAction.Tap
            else -> return 400 to reply(false, "act")
        }
        val text = (form["text"] ?: "").take(MAX_TEXT)
        if (action != RemoteAction.Ping && action != RemoteAction.Home && action != RemoteAction.Tap && text.isBlank()) return 400 to reply(false, "text")
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
        private const val MAX_PER_CONNECTION = 5000
        private const val MAX_FAILURES = 5
        private const val LOCK_MS = 30_000L
        private const val GLOBAL_FAILURES = 40

        /** The TV's address on the local network (Wi-Fi or Ethernet), or null when offline. */
        fun localAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        } catch (e: Exception) { null }

        private val PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
<meta name="color-scheme" content="dark"><meta name="theme-color" content="#0b0d12">
<title>{{ph_title}}</title><style>
:root{color-scheme:dark;--bg:#0b0d12;--panel:#151826;--panel2:#1c2034;--line:#292e4a;--ink:#f1f3fb;--mute:#9aa3c4;--acc:#8aa4ff;--acc2:#b48cff}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent;-webkit-touch-callout:none}
html,body{height:100%;margin:0}
body{background:radial-gradient(120% 70% at 50% 0%,#1d2347 0%,#0b0d12 62%) no-repeat,#0b0d12;color:var(--ink);font:16px/1.4 system-ui,-apple-system,Roboto,"Segoe UI",sans-serif;-webkit-user-select:none;user-select:none;overscroll-behavior:none}
.app{min-height:100%;min-height:100dvh;display:flex;flex-direction:column;padding:max(10px,env(safe-area-inset-top)) 18px max(12px,env(safe-area-inset-bottom));max-width:480px;margin:0 auto}
header{display:flex;align-items:center;gap:10px;padding:8px 2px 4px}
header b{font-size:20px;letter-spacing:.2px}.logo{width:30px;height:30px}
.dot{margin-left:auto;width:10px;height:10px;border-radius:50%;background:#5b617d;box-shadow:0 0 0 4px rgba(91,97,125,.2)}.dot.live{background:#4fe3a0;box-shadow:0 0 0 4px rgba(79,227,160,.18)}
main{flex:1;display:flex;flex-direction:column;justify-content:center;padding:6px 0}
.ic{width:26px;height:26px;fill:none;stroke:currentColor;stroke-width:2.3;stroke-linecap:round;stroke-linejoin:round;pointer-events:none}
button{font:inherit;color:inherit;border:0;cursor:pointer;touch-action:manipulation}
/* d-pad */
.dpad{position:relative;width:min(80vw,310px);aspect-ratio:1;margin:6px auto 22px;border-radius:50%;background:radial-gradient(circle at 50% 30%,#262c4d 0%,#161a2e 70%);border:1px solid var(--line);box-shadow:0 18px 40px rgba(0,0,0,.45),inset 0 1px 0 rgba(255,255,255,.06)}
.w{position:absolute;inset:0;background:transparent;border-radius:50%;color:#dfe5ff;-webkit-user-select:none;user-select:none}
.w .ic{position:absolute;width:30px;height:30px}
.w.up{clip-path:polygon(0 0,100% 0,50% 50%)}.w.up .ic{left:calc(50% - 15px);top:9%}
.w.down{clip-path:polygon(0 100%,100% 100%,50% 50%)}.w.down .ic{left:calc(50% - 15px);bottom:9%}
.w.left{clip-path:polygon(0 0,0 100%,50% 50%)}.w.left .ic{top:calc(50% - 15px);left:8%}
.w.right{clip-path:polygon(100% 0,100% 100%,50% 50%)}.w.right .ic{top:calc(50% - 15px);right:8%}
.w.on{background:rgba(138,164,255,.24);color:#fff}
.ok{position:absolute;left:50%;top:50%;width:36%;aspect-ratio:1;transform:translate(-50%,-50%);border-radius:50%;background:linear-gradient(135deg,var(--acc),var(--acc2));color:#0b0d12;font-weight:800;font-size:22px;z-index:2;box-shadow:0 6px 18px rgba(138,164,255,.35)}
.ok:active{transform:translate(-50%,-50%) scale(.94)}
/* round buttons */
.row{display:flex;justify-content:center;gap:20px;margin:0 0 18px}
.rb{width:66px;height:66px;border-radius:50%;background:var(--panel2);border:1px solid var(--line);display:grid;place-items:center;color:#e8ecff;transition:transform .08s,background .12s}
.rb:active{transform:scale(.92);background:var(--acc);color:#0b0d12}
.rb.big{width:78px;height:78px;background:linear-gradient(135deg,var(--acc),var(--acc2));color:#0b0d12;border:0}
.rb.big .ic{width:32px;height:32px}
.vol{display:flex;align-items:center;justify-content:space-between;max-width:290px;margin:0 auto;background:var(--panel);border:1px solid var(--line);border-radius:999px;padding:4px}
.vol .rb{width:60px;height:52px;border-radius:999px;background:transparent;border:0}
.vol .rb:active{background:var(--acc)}
/* d-pad / touchpad switch + touchpad */
.seg{display:grid;grid-template-columns:1fr 1fr;gap:6px;background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:5px;width:min(80vw,310px);margin:0 auto 16px}
.seg button{display:flex;align-items:center;justify-content:center;gap:8px;height:42px;border-radius:12px;background:transparent;color:var(--mute);font-weight:600;font-size:15px}
.seg button .ic{width:20px;height:20px}.seg button.on{background:var(--panel2);color:#fff;box-shadow:inset 0 0 0 1px var(--line)}
.tpad{position:relative;width:min(80vw,310px);height:min(62vw,240px);margin:0 auto 12px;border-radius:26px;background:radial-gradient(circle at 50% 30%,#262c4d 0%,#161a2e 75%);border:1px solid var(--line);overflow:hidden;touch-action:none;box-shadow:0 18px 40px rgba(0,0,0,.45),inset 0 1px 0 rgba(255,255,255,.06)}
.thint{position:absolute;inset:0;display:grid;place-items:center;text-align:center;padding:26px;color:#7f87aa;font-size:14px;line-height:1.5;pointer-events:none}
.tglow{position:absolute;left:0;top:0;width:60px;height:60px;border-radius:50%;background:radial-gradient(circle,rgba(138,164,255,.6),rgba(138,164,255,0) 70%);opacity:0;transition:opacity .15s;pointer-events:none}
.clickbar{display:block;width:min(80vw,310px);height:54px;margin:0 auto 18px;border-radius:18px;background:var(--panel2);border:1px solid var(--line);font-weight:700;font-size:18px;color:#e8ecff}
.clickbar:active{background:var(--acc);color:#0b0d12}
/* tabs */
.tabbar{display:grid;grid-template-columns:1fr 1fr;gap:6px;background:var(--panel);border:1px solid var(--line);border-radius:20px;padding:6px;margin-top:8px}
.tabbar button{display:flex;align-items:center;justify-content:center;gap:8px;height:50px;border-radius:15px;background:transparent;color:var(--mute);font-weight:600}
.tabbar button .ic{width:22px;height:22px}.tabbar button.on{background:var(--panel2);color:#fff;box-shadow:inset 0 0 0 1px var(--line)}
.tabbar button.on .ic{color:var(--acc)}
/* typing */
.card{background:var(--panel);border:1px solid var(--line);border-radius:22px;padding:14px}
textarea,input{width:100%;background:#0e1120;color:var(--ink);border:1px solid var(--line);border-radius:16px;padding:14px 16px;font:inherit;font-size:18px;-webkit-user-select:text;user-select:text}
textarea{min-height:128px;resize:none}textarea:focus,input:focus{outline:2px solid var(--acc);border-color:transparent}
.btn{width:100%;height:56px;border-radius:16px;display:flex;align-items:center;justify-content:center;gap:10px;font-weight:700;margin-top:10px}
.btn .ic{width:22px;height:22px}
.btn.pri{background:linear-gradient(135deg,var(--acc),var(--acc2));color:#0b0d12}
.btn.sec{background:var(--panel2);color:var(--ink);border:1px solid var(--line);justify-content:flex-start;padding:0 18px;font-weight:600}
.btn:active{transform:scale(.985);filter:brightness(1.1)}
.pairc{text-align:center;padding:26px 18px}.pairc p{color:var(--mute);margin:14px 0 16px}
.pairc input{text-align:center;font-size:28px;letter-spacing:8px;padding:14px 8px}
.toast{position:fixed;left:50%;top:max(14px,env(safe-area-inset-top));transform:translate(-50%,-90px);background:#1f2540;border:1px solid var(--line);color:var(--ink);padding:11px 18px;border-radius:999px;font-weight:600;max-width:90vw;text-align:center;transition:transform .22s;z-index:9;box-shadow:0 8px 24px rgba(0,0,0,.5)}
.toast.show{transform:translate(-50%,0)}.toast.err{background:#47202a;border-color:#7a3340}
@media (max-height:760px){.dpad{width:min(62vw,250px);margin-bottom:14px}.tpad{height:min(46vw,185px)}.rb{width:56px;height:56px}.rb.big{width:66px;height:66px}.row{margin-bottom:12px}.seg{margin-bottom:10px}.clickbar{height:46px;margin-bottom:12px}.vol .rb{height:44px}header{padding-top:0}}
</style></head><body>
<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>
<linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#8aa4ff"/><stop offset="1" stop-color="#be8cff"/></linearGradient>
<symbol id="i-up" viewBox="0 0 24 24"><path d="M5.5 14.5l6.5-6.5 6.5 6.5"/></symbol>
<symbol id="i-down" viewBox="0 0 24 24"><path d="M5.5 9.5l6.5 6.5 6.5-6.5"/></symbol>
<symbol id="i-left" viewBox="0 0 24 24"><path d="M14.5 5.5L8 12l6.5 6.5"/></symbol>
<symbol id="i-right" viewBox="0 0 24 24"><path d="M9.5 5.5L16 12l-6.5 6.5"/></symbol>
<symbol id="i-back" viewBox="0 0 24 24"><path d="M9 14L4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 010 11H11"/></symbol>
<symbol id="i-home" viewBox="0 0 24 24"><path d="M3.5 11L12 3.5 20.5 11"/><path d="M5.5 9.5V20h4.5v-6h4v6h4.5V9.5"/></symbol>
<symbol id="i-menu" viewBox="0 0 24 24"><path d="M4 7h16M4 12h16M4 17h16"/></symbol>
<symbol id="i-rew" viewBox="0 0 24 24"><path fill="currentColor" stroke-width="1.4" d="M11.5 6.5v11L4 12l7.5-5.5zM20.5 6.5v11L13 12l7.5-5.5z"/></symbol>
<symbol id="i-fwd" viewBox="0 0 24 24"><path fill="currentColor" stroke-width="1.4" d="M12.5 6.5v11L20 12l-7.5-5.5zM3.5 6.5v11L11 12 3.5 6.5z"/></symbol>
<symbol id="i-pp" viewBox="0 0 24 24"><path fill="currentColor" stroke-width="1.4" d="M4.5 5.5v13L13.5 12l-9-6.5z"/><path d="M17.5 6v12M21 6v12"/></symbol>
<symbol id="i-vdn" viewBox="0 0 24 24"><path fill="currentColor" stroke-width="1.4" d="M4 9.5h3.5L12.5 5.5v13L7.5 14.5H4z"/><path d="M16 9.5a3.5 3.5 0 010 5"/></symbol>
<symbol id="i-vup" viewBox="0 0 24 24"><path fill="currentColor" stroke-width="1.4" d="M3 9.5h3.5L11.5 5.5v13L6.5 14.5H3z"/><path d="M15 9.5a3.5 3.5 0 010 5M17.8 6.8a7.5 7.5 0 010 10.4"/></symbol>
<symbol id="i-mute" viewBox="0 0 24 24"><path fill="currentColor" stroke-width="1.4" d="M3 9.5h3.5L11.5 5.5v13L6.5 14.5H3z"/><path d="M15.5 9.5l5 5M20.5 9.5l-5 5"/></symbol>
<symbol id="i-tv" viewBox="0 0 24 24"><rect x="3" y="4.5" width="18" height="12.5" rx="2.5"/><path d="M8 20.5h8M12 17v3.5"/></symbol>
<symbol id="i-search" viewBox="0 0 24 24"><circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/></symbol>
<symbol id="i-type" viewBox="0 0 24 24"><path d="M4.5 6.5h4M6.5 6.5v11M4.5 17.5h4"/><path d="M14 8.5h3.5a2.5 2.5 0 012.5 2.5v2a2.5 2.5 0 01-2.5 2.5H14"/></symbol>
<symbol id="i-enter" viewBox="0 0 24 24"><path d="M20 5v8a3 3 0 01-3 3H5"/><path d="M9 12l-4 4 4 4"/></symbol>
<symbol id="i-dpad" viewBox="0 0 24 24"><path d="M12 3v18M3 12h18"/><path d="M9 6l3-3 3 3M9 18l3 3 3-3M6 9l-3 3 3 3M18 9l3 3-3 3"/></symbol>
<symbol id="i-touch" viewBox="0 0 24 24"><rect x="3" y="4.5" width="18" height="15" rx="3.5"/><path d="M3 14.5h18M12 14.5v5"/></symbol>
<symbol id="i-remote" viewBox="0 0 24 24"><rect x="7" y="2.5" width="10" height="19" rx="3.5"/><circle cx="12" cy="8.5" r="2.2"/><path d="M10 14.5h4M10 18h4"/></symbol>
<symbol id="i-kbd" viewBox="0 0 24 24"><rect x="2.5" y="6" width="19" height="12" rx="3"/><path d="M6.5 10h.01M10 10h.01M14 10h.01M17.5 10h.01M8 14h8"/></symbol>
</defs></svg>
<div class="app">
<header><svg class="logo" viewBox="0 0 108 108"><path d="M60,24 A30,30 0 1,0 84,64 A24,24 0 0,1 60,24 z" fill="url(#g)"/></svg><b>Luna</b><span id="dot" class="dot"></span></header>
<main>
<section id="pair" class="card pairc" hidden><p>{{ph_pin}}</p><input id="pin" inputmode="numeric" maxlength="8" autocomplete="off" placeholder="&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;"><button id="go" class="btn pri">{{ph_pair}}</button></section>
<section id="app" hidden>
<div id="remote">
<div class="seg"><button id="segD" class="on"><svg class="ic"><use href="#i-dpad"/></svg>{{ph_pad_dpad}}</button><button id="segT"><svg class="ic"><use href="#i-touch"/></svg>{{ph_pad_touch}}</button></div>
<div id="dpadwrap">
<div class="dpad">
<button class="w up hold" data-k="DPAD_UP" aria-label="Up"><svg class="ic"><use href="#i-up"/></svg></button>
<button class="w down hold" data-k="DPAD_DOWN" aria-label="Down"><svg class="ic"><use href="#i-down"/></svg></button>
<button class="w left hold" data-k="DPAD_LEFT" aria-label="Left"><svg class="ic"><use href="#i-left"/></svg></button>
<button class="w right hold" data-k="DPAD_RIGHT" aria-label="Right"><svg class="ic"><use href="#i-right"/></svg></button>
<button class="ok tap" data-a="key" data-k="DPAD_CENTER">OK</button>
</div>
</div>
<div id="touchwrap" hidden>
<div class="tpad" id="tpad"><div class="tglow" id="tglow"></div><div class="thint">{{ph_pad_hint}}</div></div>
<button class="clickbar tap" data-a="tap" data-k="1">OK</button>
</div>
<div class="row">
<button class="rb tap" data-a="key" data-k="BACK" aria-label="Back"><svg class="ic"><use href="#i-back"/></svg></button>
<button class="rb tap" data-a="home" data-k="1" aria-label="Home"><svg class="ic"><use href="#i-home"/></svg></button>
<button class="rb tap" data-a="key" data-k="MENU" aria-label="Menu"><svg class="ic"><use href="#i-menu"/></svg></button>
</div>
<div class="row">
<button class="rb tap" data-a="key" data-k="MEDIA_REWIND" aria-label="Rewind"><svg class="ic"><use href="#i-rew"/></svg></button>
<button class="rb big tap" data-a="key" data-k="MEDIA_PLAY_PAUSE" aria-label="Play / pause"><svg class="ic"><use href="#i-pp"/></svg></button>
<button class="rb tap" data-a="key" data-k="MEDIA_FAST_FORWARD" aria-label="Fast forward"><svg class="ic"><use href="#i-fwd"/></svg></button>
</div>
<div class="vol">
<button class="rb tap" data-a="vol" data-k="down" aria-label="Volume down"><svg class="ic"><use href="#i-vdn"/></svg></button>
<button class="rb tap" data-a="vol" data-k="mute" aria-label="Mute"><svg class="ic"><use href="#i-mute"/></svg></button>
<button class="rb tap" data-a="vol" data-k="up" aria-label="Volume up"><svg class="ic"><use href="#i-vup"/></svg></button>
</div>
</div>
<div id="typing" hidden>
<div class="card"><textarea id="text" placeholder="{{ph_hint}}" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
<button id="open" class="btn pri"><svg class="ic"><use href="#i-tv"/></svg>{{ph_open}}</button>
<button id="search" class="btn sec"><svg class="ic"><use href="#i-search"/></svg>{{ph_search}}</button>
<button id="type" class="btn sec"><svg class="ic"><use href="#i-type"/></svg>{{ph_type}}</button>
<button id="enter" class="btn sec"><svg class="ic"><use href="#i-enter"/></svg>{{ph_enter}}</button></div>
</div>
<nav class="tabbar"><button id="tabRemote" class="on"><svg class="ic"><use href="#i-remote"/></svg>{{ph_tab_remote}}</button><button id="tabType"><svg class="ic"><use href="#i-kbd"/></svg>{{ph_tab_type}}</button></nav>
</section>
</main></div>
<div id="toast" class="toast"></div>
<script>
var L=__LABELS__,pin=null;try{pin=localStorage.getItem('luna.pin')}catch(e){}
var qr=(location.hash||'').match(/pin=(\d{1,8})/);if(qr){pin=qr[1];try{history.replaceState(null,'',location.pathname)}catch(e){}}
function $(i){return document.getElementById(i)}
function say(t,e){var x=$('toast');x.textContent=t||'';x.className='toast'+(t?' show':'')+(e?' err':'');clearTimeout(say.t);if(t)say.t=setTimeout(function(){x.className='toast'},2400)}
function live(ok){$('dot').className='dot'+(ok?' live':'')}
function body(act,text){return 'pin='+encodeURIComponent(pin||'')+'&act='+act+'&text='+encodeURIComponent(text||'')}
function post(act,text){return fetch('/api',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body(act,text)}).then(function(r){return r.json().then(function(j){j.status=r.status;return j})})}
function show(paired){$('pair').hidden=paired;$('app').hidden=!paired;if(!paired)live(false)}
function result(j){if(j.ok){live(true);say(L.ph_sent);return}
if(j.error==='pin'){try{localStorage.removeItem('luna.pin')}catch(e){}pin=null;show(false);say(L.ph_bad_pin,1)}
else if(j.error==='locked')say(L.ph_locked,1);else if(j.error==='nofield')say(L.ph_nofield,1);else say('?',1)}
function send(act){var t=$('text').value;if(!t.trim())return;post(act,t).then(result).catch(function(){live(false);say('?',1)})}
function tab(t){$('remote').hidden=t!=='remote';$('typing').hidden=t!=='type';$('tabRemote').className=t==='remote'?'on':'';$('tabType').className=t==='type'?'on':''}
$('tabRemote').onclick=function(){tab('remote')};$('tabType').onclick=function(){tab('type')};
$('go').onclick=function(){pin=$('pin').value.trim();post('ping','').then(function(j){if(j.ok){try{localStorage.setItem('luna.pin',pin)}catch(e){}show(true);live(true)}else result(j)}).catch(function(){say('?',1)})};
$('open').onclick=function(){send('open')};$('search').onclick=function(){send('search')};$('type').onclick=function(){send('type')};$('enter').onclick=function(){send('enter')};
function key(act,k){return post(act,k).then(function(j){live(!!j.ok||j.error==='nofield');if(!j.ok&&(j.error==='pin'||j.error==='locked'))result(j)}).catch(function(){live(false)})}
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
/* touchpad */
var padmode='dpad';try{padmode=localStorage.getItem('luna.pad')||'dpad'}catch(e){}
function setPad(m){padmode=m;try{localStorage.setItem('luna.pad',m)}catch(e){}$('dpadwrap').hidden=m!=='dpad';$('touchwrap').hidden=m!=='touch';$('segD').className=m==='dpad'?'on':'';$('segT').className=m==='touch'?'on':''}
$('segD').onclick=function(){setPad('dpad')};$('segT').onclick=function(){setPad('touch')};setPad(padmode);
var tp=$('tpad'),ptrs={},t0=0,moved=0,acc={x:0,y:0,sy:0},busy=false;
function pend(){return acc.x||acc.y||acc.sy}
function flush(){if(busy)return;var r=tp.getBoundingClientRect(),w=Math.max(1,r.width),h=Math.max(1,r.height);
if(acc.x||acc.y){var dx=Math.round(acc.x/w*1000),dy=Math.round(acc.y/h*1000);acc.x=acc.y=0;if(dx||dy){busy=true;post('mv',dx+','+dy).then(sent).catch(sent);return}}
if(acc.sy){var sy=Math.round(acc.sy/h*1000);acc.sy=0;if(sy){busy=true;post('sc','0,'+sy).then(sent).catch(sent)}}}
function sent(j){busy=false;if(j&&j.status){if(!j.ok&&(j.error==='pin'||j.error==='locked'))result(j);else live(!!j.ok)}else live(false);if(pend())flush()}
function glow(e,on){var g=$('tglow'),r=tp.getBoundingClientRect();g.style.opacity=on?1:0;g.style.transform='translate('+(e.clientX-r.left-30)+'px,'+(e.clientY-r.top-30)+'px)'}
tp.addEventListener('pointerdown',function(e){e.preventDefault();try{tp.setPointerCapture(e.pointerId)}catch(x){}
ptrs[e.pointerId]={x:e.clientX,y:e.clientY,t:e.timeStamp};if(Object.keys(ptrs).length===1){t0=e.timeStamp;moved=0}glow(e,true)});
tp.addEventListener('pointermove',function(e){var p=ptrs[e.pointerId];if(!p)return;
var dx=e.clientX-p.x,dy=e.clientY-p.y,dt=Math.max(1,e.timeStamp-p.t);p.x=e.clientX;p.y=e.clientY;p.t=e.timeStamp;moved+=Math.abs(dx)+Math.abs(dy);
var n=Object.keys(ptrs).length;
if(n>=2){acc.sy+=dy/n}else{var v=Math.hypot(dx,dy)/dt,g=1+Math.min(2.2,v*1.1);acc.x+=dx*g;acc.y+=dy*g}
glow(e,true);flush()});
function lift(e){if(!ptrs[e.pointerId])return;delete ptrs[e.pointerId];glow(e,false);
if(Object.keys(ptrs).length===0&&moved<10&&e.timeStamp-t0<260){buzz();key('tap','1')}}
tp.addEventListener('pointerup',lift);tp.addEventListener('pointercancel',lift);
tp.addEventListener('contextmenu',function(e){e.preventDefault()});
document.addEventListener('visibilitychange',function(){if(document.hidden)Object.keys(holds).forEach(function(k){var el=document.querySelector('.hold[data-k="'+k+'"]');if(el)release(el)})});
if(pin){post('ping','').then(function(j){if(j.ok){try{localStorage.setItem('luna.pin',pin)}catch(e){}show(true);live(true)}else{show(false);result(j)}}).catch(function(){show(false)})}else show(false);
</script></body></html>""".replace("__LABELS__", "{{labels}}")
    }
}
