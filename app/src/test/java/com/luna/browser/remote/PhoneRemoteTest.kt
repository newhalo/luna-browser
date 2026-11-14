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
    private val calls = CopyOnWriteArrayList<Pair<RemoteAction, String>>()
    private lateinit var remote: PhoneRemote

    @Before fun start() {
        remote = PhoneRemote(
            pin = { "4321" },
            labels = { mapOf("ph_title" to "Luna <remote>", "ph_hint" to "Address \"or\" search") },
            perform = { a, t, done -> calls += a to t; done(a != RemoteAction.Type) },
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

    @Test fun badRequestsAreRefused() {
        assertEquals(400, post("pin=4321&act=format-disk").first)
        assertEquals(400, post("pin=4321&act=open&text=").first)
    }
}
