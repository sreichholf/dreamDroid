package net.reichholf.dreamdroid.ui.epg

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.testutil.recordingNavHandle
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.nav.MultiEpg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgBouquetTopBarTest {
    @Test
    fun listEpgHidesMultiEpgUntilBouquetIsSet() {
        assertEquals(listOf(R.id.menu_pick_bouquet), actionIds(""))
        assertEquals(listOf(R.id.menu_multiepg, R.id.menu_pick_bouquet), actionIds("1:7:1:B"))
    }

    @Test
    fun openMultiEpgPersistsDrawerMode() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        DrawerEpgMode.saveList(ctx)
        openBouquetMultiEpg(
            context = ctx,
            handle = null,
            bouquetRef = "1:7:1:B",
            bouquetName = "Favourites",
            timeSec = 0L
        )
        assertTrue(DrawerEpgMode.isMulti(prefs))
        DrawerEpgMode.saveList(ctx)
        assertFalse(DrawerEpgMode.isMulti(prefs))
    }

    @Test
    fun openMultiEpgForwardsTheTabRoot() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        var routed: MultiEpg? = null
        val handle = recordingNavHandle(onMultiEpg = { routed = it })

        openBouquetMultiEpg(
            context = ctx,
            handle = handle,
            bouquetRef = FOLDER,
            bouquetName = "Satellites",
            timeSec = 0L,
            tabRootRef = TAB
        )

        assertEquals(FOLDER, routed?.serviceRef)
        assertEquals(TAB, routed?.tabRootRef)
    }

    private fun actionIds(bouquetRef: String): List<Int> = epgBouquetTopBarActions(
        bouquetRef = bouquetRef,
        multiEpgLabel = "MultiEPG",
        pickBouquetLabel = "Bouquets",
        onOpenMultiEpg = {},
        onPickBouquet = {}
    ).map { it.id }

    private companion object {
        const val TAB =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val FOLDER = "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
    }
}
