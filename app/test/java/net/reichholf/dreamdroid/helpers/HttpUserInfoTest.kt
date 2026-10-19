package net.reichholf.dreamdroid.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpUserInfoTest {
    @Test
    fun httpEmbedsWhenLoginEnabled() {
        assertEquals(
            "root:secret@",
            HttpUserInfo.embed(enabled = true, user = "root", pass = "secret")
        )
    }

    @Test
    fun nothingWhenLoginDisabled() {
        assertEquals(
            "",
            HttpUserInfo.embed(enabled = false, user = "root", pass = "secret")
        )
    }

    @Test
    fun embeddedCredentialsArePercentEncoded() {
        assertEquals(
            "me%40home:p%3Aw%2Fd%23%3F%20x@",
            HttpUserInfo.embed(
                enabled = true,
                user = "me@home",
                pass = "p:w/d#? x"
            )
        )
    }

    @Test
    fun redactMasksUserInfoOnly() {
        assertEquals(
            "https://***@box.local:8001/1%3A0%3A1",
            HttpUserInfo.redact("https://root:secret@box.local:8001/1%3A0%3A1")
        )
        assertEquals(
            "rtsp://***@box.local:554/stream?ref=1",
            HttpUserInfo.redact("rtsp://enc:pw@box.local:554/stream?ref=1")
        )
        assertEquals(
            "http://box.local:80/file?file=%2Fa%40b.ts",
            HttpUserInfo.redact("http://box.local:80/file?file=%2Fa%40b.ts")
        )
    }

    @Test
    fun basicAuthMovesTheLoginIntoAHeader() {
        val (url, header) = HttpUserInfo.toBasicAuth("http://me%40home:p%3Aw@box:8001/1:0:1:")

        assertEquals("http://box:8001/1:0:1:", url)
        assertEquals(okhttp3.Credentials.basic("me@home", "p:w", Charsets.UTF_8), header)
    }

    @Test
    fun basicAuthLeavesAUrlWithoutLoginAlone() {
        assertEquals("http://box:8001/x" to null, HttpUserInfo.toBasicAuth("http://box:8001/x"))
    }
}
