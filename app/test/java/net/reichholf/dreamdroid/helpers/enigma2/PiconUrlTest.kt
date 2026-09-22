package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PiconUrlTest {
    @Test
    fun onlinePiconUrl_matchesPageAndOmitsUserInfo() {
        val profile = loginProfile(ssl = false, port = 80)
        val fileName = "/usr/share/enigma2/picon/1_0_1.png"
        val url = Picon.onlinePiconUrl(profile, fileName)
        assertEquals(pageUrl(profile, fileName), url)
        assertFalse(url.contains("root:secret@"))
        assertFalse(url.contains("user:pass@"))
        assertFalse(url.contains("@"))
        assertTrue(url.startsWith("http://box.local:80/file?"))
    }

    @Test
    fun onlinePiconUrl_httpsMatchesPageAndOmitsUserInfo() {
        val profile = loginProfile(ssl = true, port = 443)
        val fileName = "/usr/share/enigma2/picon/1_0_1.png"
        val url = Picon.onlinePiconUrl(profile, fileName)
        assertEquals(pageUrl(profile, fileName), url)
        assertFalse(url.contains("root:secret@"))
        assertFalse(url.contains("user:pass@"))
        assertFalse(url.contains("@"))
        assertTrue(url.startsWith("https://box.local:443/file?"))
    }

    private fun pageUrl(profile: Profile, fileName: String): String =
        EnigmaUrls.page(profile, URIStore.FILE, listOf(NameValuePair("file", fileName)))

    private fun loginProfile(ssl: Boolean, port: Int): Profile = Profile().apply {
        host = "box.local"
        this.port = port
        this.ssl = ssl
        login = true
        user = "root"
        pass = "secret"
    }
}
