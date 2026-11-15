package com.luna.browser.remote

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/** Runs the real server on the loopback interface and talks to it like a phone would. */
class PhoneRemoteTest {
    // Every label the page uses (AppState.labels provides the same ones).
    private val PAGE_KEYS = listOf(
        "ph_title", "ph_pin", "ph_pair", "ph_hint", "ph_open", "ph_search", "ph_type", "ph_enter",
        "ph_sent", "ph_bad_pin", "ph_locked", "ph_nofield", "ph_tab_remote", "ph_tab_type", "ph_pad_dpad", "ph_pad_touch", "ph_pad_hint",
        "ph_share_ask", "ph_share_no", "ph_bm_title", "ph_bm_text", "ph_bm_copy", "ph_copied",
        "ph_tab_favs", "ph_fav_save", "ph_fav_saved", "ph_fav_empty", "ph_fav_remove", "ph_fav_confirm", "ph_fav_err",
    )
    private val calls = CopyOnWriteArrayList<Pair<RemoteAction, String>>()
    private lateinit var remote: PhoneRemote

    @Before fun start() {
        remote = PhoneRemote(
            pin = { "4321" },
            labels = { PAGE_KEYS.associateWith { "text-$it" } + mapOf("ph_title" to "Luna <remote>", "ph_hint" to "Address \"or\" search") },
            perform = { a, t, done -> calls += a to t; done(a != RemoteAction.Type) },
            budgetMs = 500,
            idleMs = 1500,
            favorites = { """[{"t":"Sintel <1>","u":"https://example.com/sintel"}]""" },
        )
        remote.start()
        val end = System.currentTimeMillis() + 3000
        while (remote.port == 0 && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue("server did not start", remote.port != 0)
    }

    @After fun stop() = remote.stop()

    private fun get(path: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:${remote.port}$path").openConnection() as HttpURLConnection
        return c.responseCode to (if (c.responseCode < 400) c.inputStream else c.errorStream).readBytes().toString(Charsets.UTF_8)
    }

    private fun post(body: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:${remote.port}/api").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray()) }
        return c.responseCode to (if (c.responseCode < 400) c.inputStream else c.errorStream).readBytes().toString(Charsets.UTF_8)
    }

    @Test fun servesThePageWithEscapedLabels() {
        val (code, html) = get("/")
        assertEquals(200, code)
        assertTrue(html.contains("Luna &lt;remote&gt;"))
        assertTrue(html.contains("Address &quot;or&quot; search"))
        assertTrue(!html.contains("{{"))
    }

    @Test fun pageOffersSharedLinksOnlyAfterAConfirmingTap() {
        val html = get("/").second
        // A link arrives in the #fragment, is shown and sent only when the viewer taps; it never auto-sends.
        assertTrue(html.contains("id=\"shareGo\""))
        assertTrue(html.contains("open=([^&]+)"))
        assertTrue(html.contains("/^https?:"))
        assertTrue(html.contains("location.origin"))
    }

    @Test fun favoritesAreListedOnlyForAPairedPhone() {
        val (code, body) = post("pin=4321&act=favs")
        assertEquals(200, code)
        val o = org.json.JSONObject(body)
        assertTrue(o.getBoolean("ok"))
        assertEquals("https://example.com/sintel", o.getJSONArray("favs").getJSONObject(0).getString("u"))
        assertEquals(401, post("pin=0000&act=favs").first)
        assertEquals(401, post("act=favs").first)
        assertTrue("listing never runs an app action", calls.isEmpty())
    }

    @Test fun savingAndRemovingFavoritesReachTheApp() {
        assertEquals(200, post("pin=4321&act=fa&text=https%3A%2F%2Fexample.com%2Fa%0AMy%20title").first)
        assertEquals(200, post("pin=4321&act=fd&text=https%3A%2F%2Fexample.com%2Fa").first)
        assertEquals(
            listOf(RemoteAction.FavAdd to "https://example.com/a
My title", RemoteAction.FavDel to "https://example.com/a"),
            calls.toList(),
        )
        assertEquals(401, post("pin=1&act=fa&text=https%3A%2F%2Fexample.com%2F").first)
        assertEquals(400, post("pin=4321&act=fa&text=").first)
    }

    @Test fun unknownPathsAre404() = assertEquals(404, get("/admin").first)

    @Test fun rightPinRunsTheAction() {
        val (code, body) = post("pin=4321&act=open&text=" + java.net.URLEncoder.encode("phim hay", "UTF-8"))
        assertEquals(200, code)
        assertTrue(body.contains("\"ok\":true"))
        assertEquals(listOf(RemoteAction.Open to "phim hay"), calls.toList())
    }

    @Test fun wrongPinIsRejectedAndNothingRuns() {
        assertEquals(401, post("pin=0000&act=open&text=x").first)
        assertEquals(401, post("act=open&text=x").first)
        assertTrue(calls.isEmpty())
    }

    @Test fun repeatedWrongPinsLockEvenTheRightOne() {
        repeat(5) { post("pin=1&act=ping") }
        assertEquals(429, post("pin=4321&act=ping").first)
        assertTrue(calls.isEmpty())
    }

    @Test fun failedActionIsReportedToThePhone() {
        val (code, body) = post("pin=4321&act=type&text=hello")
        assertEquals(200, code)
        assertTrue(body.contains("\"ok\":false"))
        assertTrue(body.contains("nofield"))
    }

    @Test fun clientsThatStallDontBlockTheServer() {
        // More half-sent requests than there are workers: each is dropped once its time budget is spent.
        val stalled = List(6) { java.net.Socket("127.0.0.1", remote.port).also { it.getOutputStream().write("GET / HT".toByteArray()) } }
        Thread.sleep(900)
        assertEquals(200, get("/").first)
        stalled.forEach { it.close() }
    }

    @Test fun oversizedHeadersAreDropped() {
        java.net.Socket("127.0.0.1", remote.port).use {
            it.soTimeout = 2000
            val crlf = "\r\n"
            it.getOutputStream().write(("GET / HTTP/1.1${crlf}X: " + "a".repeat(20_000) + crlf + crlf).toByteArray())
            assertTrue(it.getInputStream().read() == -1)   // closed without an answer
        }
        assertEquals(200, get("/").first)
    }

    @Test fun padKeysReachTheApp() {
        assertEquals(200, post("pin=4321&act=kd&text=DPAD_UP").first)
        assertEquals(200, post("pin=4321&act=ku&text=DPAD_UP").first)
        assertEquals(200, post("pin=4321&act=key&text=BACK").first)
        assertEquals(200, post("pin=4321&act=vol&text=up").first)
        assertEquals(200, post("pin=4321&act=home").first)   // no text needed
        assertEquals(
            listOf(
                RemoteAction.KeyDown to "DPAD_UP", RemoteAction.KeyUp to "DPAD_UP", RemoteAction.Key to "BACK",
                RemoteAction.Volume to "up", RemoteAction.Home to "",
            ),
            calls.toList(),
        )
    }

    @Test fun touchpadActionsReachTheApp() {
        assertEquals(200, post("pin=4321&act=mv&text=12%2C-7").first)
        assertEquals(200, post("pin=4321&act=sc&text=0%2C40").first)
        assertEquals(200, post("pin=4321&act=tap").first)          // a tap needs no text
        assertEquals(
            listOf(RemoteAction.Move to "12,-7", RemoteAction.Scroll to "0,40", RemoteAction.Tap to ""),
            calls.toList(),
        )
        assertEquals(401, post("pin=1&act=mv&text=1%2C1").first)
    }

    @Test fun oneConnectionServesManyRequests() {
        // A phone keeps its connection open, so each touchpad movement doesn't pay for a new TCP handshake.
        java.net.Socket("127.0.0.1", remote.port).use { sock ->
            sock.soTimeout = 3000
            val out = sock.getOutputStream()
            val inp = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            repeat(5) { n ->
                val body = "pin=4321&act=mv&text=$n%2C0"
                out.write(("POST /api HTTP/1.1\r\nHost: x\r\nContent-Length: ${body.length}\r\n\r\n$body").toByteArray())
                out.flush()
                val status = inp.readLine()
                assertTrue("request $n: $status", status.startsWith("HTTP/1.1 200"))
                var length = 0
                while (true) {
                    val h = inp.readLine()
                    if (h.isEmpty()) break
                    if (h.startsWith("Content-Length:", ignoreCase = true)) length = h.substringAfter(':').trim().toInt()
                }
                inp.skip(length.toLong())
            }
        }
        assertEquals(5, calls.size)
    }

    @Test fun idleConnectionsAreClosedAndStoppingClosesThemAll() {
        val idle = java.net.Socket("127.0.0.1", remote.port)
        idle.soTimeout = 4000
        idle.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
        val inp = idle.getInputStream()
        while (inp.available() == 0) Thread.sleep(10)          // the page arrived: the connection is now idle
        remote.stop()                                           // ...and stopping the remote must end it too
        var closed = false
        val end = System.currentTimeMillis() + 3000
        while (!closed && System.currentTimeMillis() < end) {
            try { closed = inp.read() == -1 } catch (e: java.net.SocketException) { closed = true } catch (e: java.net.SocketTimeoutException) { }
        }
        assertTrue("connection stayed open after stop()", closed)
        idle.close()
    }

    @Test fun padKeysNeedThePin() {
        assertEquals(401, post("pin=1&act=kd&text=DPAD_UP").first)
        assertEquals(400, post("pin=4321&act=key&text=").first)
        assertTrue(calls.isEmpty())
    }

    @Test fun badRequestsAreRefused() {
        assertEquals(400, post("pin=4321&act=format-disk").first)
        assertEquals(400, post("pin=4321&act=open&text=").first)
    }
}
