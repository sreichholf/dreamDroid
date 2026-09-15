package net.reichholf.dreamdroid.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpUserInfoTest {
    @Test
    fun httpNeverEmbedsCredentials() {
        assertEquals(
            "",
            HttpUserInfo.embed(
                enabled = true,
                user = "root",
                pass = "secret",
                scheme = "http"
            )
        )
        assertEquals(
            "",
            HttpUserInfo.embed(
                enabled = true,
                user = "root",
                pass = "secret",
                scheme = "HTTP"
            )
        )
    }

    @Test
    fun httpsEmbedsWhenLoginEnabled() {
        assertEquals(
            "root:secret@",
            HttpUserInfo.embed(
                enabled = true,
                user = "root",
                pass = "secret",
                scheme = "https"
            )
        )
        assertEquals(
            "",
            HttpUserInfo.embed(
                enabled = false,
                user = "root",
                pass = "secret",
                scheme = "https"
            )
        )
    }

    @Test
    fun rtspEmbedsWhenLoginEnabled() {
        assertEquals(
            "enc:pw@",
            HttpUserInfo.embed(
                enabled = true,
                user = "enc",
                pass = "pw",
                scheme = "rtsp"
            )
        )
    }
}
