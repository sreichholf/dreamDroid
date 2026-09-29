package net.reichholf.dreamdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.dreamDroidApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks app data after a real 1.15 → 2.0 `adb install -r`. Driven by
 * `.github/upgrade-from-115/run.sh`, which seeds `seed-profile.sql` through
 * 1.15 and passes `-e upgradeFrom115 <scenario>`. Skipped in normal runs.
 */
@RunWith(AndroidJUnit4::class)
class Upgrade115Test {
    private val scenario: String? =
        InstrumentationRegistry.getArguments().getString(SCENARIO_ARG)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun roomV1ProfileSurvivesUpgrade() {
        assumeTrue(scenario == ROOM_V1)
        assertSeededProfile()
        assertEquals(setOf(DEMO, SEEDED_NAME), profileNames())
        assertCurrentIsSeeded()
    }

    @Test
    fun legacySqliteProfileIsImported() {
        assumeTrue(scenario == LEGACY_SQLITE)
        assertSeededProfile()
        assertEquals(setOf(SEEDED_NAME), profileNames())
        assertCurrentIsSeeded()
        assertFalse(DatabaseHelper.databaseFile(context).exists())
    }

    private fun profileNames(): Set<String?> = runBlocking {
        AppDatabase.profiles(context).getProfiles().map { it.name }.toSet()
    }

    private fun assertCurrentIsSeeded() {
        assertEquals(SEEDED_ID, dreamDroidApp().profiles.current.value?.id)
    }

    private fun assertSeededProfile() {
        val p = runBlocking { AppDatabase.profiles(context).getProfile(SEEDED_ID) }
        assertNotNull("profile $SEEDED_ID missing after upgrade", p)
        p!!
        assertEquals(SEEDED_NAME, p.name)
        assertEquals("10.11.12.13", p.host)
        assertEquals("10.11.12.14", p.streamHost)
        assertEquals(8443, p.port)
        assertEquals(8002, p.streamPort)
        assertEquals(8080, p.filePort)
        assertEquals("upuser", p.user)
        assertEquals("uppass", p.pass)
        assertTrue(p.login)
        assertTrue(p.ssl)
        assertTrue(p.allCertsTrusted)
        assertTrue(p.streamLogin)
        assertTrue(p.fileLogin)
        assertTrue(p.fileSsl)
        assertTrue(p.simpleRemote)
        assertEquals(FAVOURITES_REF, p.defaultBouquetTv)
        assertEquals("Favourites", p.defaultBouquetTvName)
        assertEquals(BOUQUETS_REF, p.defaultParentBouquetTv)
        assertEquals("Bouquets", p.defaultParentBouquetTvName)
        assertTrue(p.encoderStream)
        assertEquals("transcode", p.encoderPath)
        assertEquals(5554, p.encoderPort)
        assertTrue(p.encoderLogin)
        assertEquals("encuser", p.encoderUser)
        assertEquals("encpass", p.encoderPass)
        assertEquals(4000, p.encoderVideoBitrate)
        assertEquals(192, p.encoderAudioBitrate)
        assertEquals("UpgradeWifi", p.ssid)
        assertTrue(p.isDefaultProfileOnNoWifi)
        assertFalse(p.zapAndStream)
    }

    private companion object {
        const val SCENARIO_ARG = "upgradeFrom115"
        const val ROOM_V1 = "room_v1"
        const val LEGACY_SQLITE = "legacy_sqlite"
        const val SEEDED_ID = 42
        const val SEEDED_NAME = "Upgrade Box"
        const val DEMO = "Demo"
        const val FAVOURITES_REF =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val BOUQUETS_REF =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"bouquets.tv\" ORDER BY bouquet"
    }
}
