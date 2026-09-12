package net.reichholf.dreamdroid.helpers

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleHttpClientOkHttpTest {
    @Test
    fun buildUrl_httpHostAndQuery() {
        val profile = Profile().apply {
            host = "box.local"
            port = 80
            setSsl(false)
            setLogin(false)
        }
        val client = SimpleHttpClient.getInstance(profile)
        val url = client.buildUrl("/web/about", ArrayList())
        assertEquals("http://box.local:80/web/about?", url)
    }

    @Test
    fun buildUrl_httpsWhenSsl() {
        val profile = Profile().apply {
            host = "box.local"
            port = 443
            setSsl(true)
            setLogin(false)
        }
        val client = SimpleHttpClient.getInstance(profile)
        val url = client.buildUrl("/web/about", ArrayList())
        assertTrue(url.startsWith("https://"))
        assertTrue(url.contains("box.local:443/web/about"))
    }

    @Test
    fun buildAuthedUrl_embedsUserInfo() {
        val profile = Profile().apply {
            host = "box.local"
            port = 80
            setSsl(false)
            setLogin(true)
            user = "root"
            pass = "secret"
        }
        val client = SimpleHttpClient.getInstance(profile)
        val url = client.buildAuthedUrl("/web/about", ArrayList())
        assertTrue(url.contains("root:secret@box.local:80"))
    }
}
