package net.reichholf.dreamdroid.helpers.enigma2

import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.testutil.TestProfiles
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

    @Test
    fun piconMapper_onlineProfileLoadsFromItsReceiverPath() = runBlocking<Unit> {
        val profiles = TestProfiles()
        val profile = loginProfile(ssl = false, port = 80).apply {
            name = "box"
            piconsOnline = true
            piconsOnlinePath = "/media/hdd/picon"
        }
        profiles.repository.save(profile)
        assertTrue(profiles.repository.setCurrent(profile.id!!))

        val mapped = map(profiles, PiconKey(REFERENCE, "Das Erste HD"))

        assertEquals(pageUrl(profile, "/media/hdd/picon/$REFERENCE_FILE.png").toUri(), mapped)
    }

    @Test
    fun piconMapper_onlineProfileNamesByServiceNameWhenItSaysSo() = runBlocking<Unit> {
        val profiles = TestProfiles()
        val profile = loginProfile(ssl = false, port = 80).apply {
            name = "box"
            piconsOnline = true
            piconsOnlineUseName = true
        }
        profiles.repository.save(profile)
        assertTrue(profiles.repository.setCurrent(profile.id!!))

        val mapped = map(profiles, PiconKey(REFERENCE, "Das Erste HD"))

        assertEquals(
            pageUrl(profile, "${Profile.DEFAULT_PICON_PATH}/Das Erste HD.png").toUri(),
            mapped
        )
    }

    @Test
    fun piconMapper_offlineProfileLoadsTheSyncedFile() = runBlocking<Unit> {
        val profiles = TestProfiles()
        val profile = loginProfile(ssl = false, port = 80).apply { name = "box" }
        profiles.repository.save(profile)
        assertTrue(profiles.repository.setCurrent(profile.id!!))

        val mapped = map(profiles, PiconKey(REFERENCE, "Das Erste HD"))

        assertEquals(syncedUri(profiles), mapped)
    }

    @Test
    fun piconMapper_withoutProfileLoadsTheSyncedFile() {
        val profiles = TestProfiles()

        assertEquals(syncedUri(profiles), map(profiles, PiconKey(REFERENCE, "Das Erste HD")))
    }

    private fun map(profiles: TestProfiles, key: PiconKey) =
        PiconImageLoader.piconMapper(profiles.context, profiles.repository.current)
            .map(key, Options(profiles.context))

    private fun syncedUri(profiles: TestProfiles) =
        "file://${Picon.localDir(profiles.context)}$REFERENCE_FILE.png".toUri()

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

    private companion object {
        const val REFERENCE = "1:0:19:283D:3FB:1:C00000:0:0:0:"
        const val REFERENCE_FILE = "1_0_19_283D_3FB_1_C00000_0_0_0"
    }
}
