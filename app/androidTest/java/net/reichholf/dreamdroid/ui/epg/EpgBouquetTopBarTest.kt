package net.reichholf.dreamdroid.ui.epg

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgBouquetTopBarTest {
    @Test
    fun listEpgHidesMultiEpgUntilBouquetIsSet() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val app = ctx.applicationContext as Application
        val viewModel = EpgBouquetViewModel(app, SavedStateHandle())
        assertEquals(listOf(R.id.menu_pick_bouquet), actionIds(viewModel.bouquetRef))
        viewModel.ensureEpoch(
            epoch = 1,
            leafRef = "1:7:1:B",
            leafName = "Favourites",
            leafTimeSec = null,
            nowSec = 1_700_000_000
        )
        assertEquals(
            listOf(R.id.menu_multiepg, R.id.menu_pick_bouquet),
            actionIds(viewModel.bouquetRef)
        )
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

    private fun actionIds(bouquetRef: String): List<Int> = epgBouquetTopBarActions(
        bouquetRef = bouquetRef,
        multiEpgLabel = "MultiEPG",
        pickBouquetLabel = "Bouquets",
        onOpenMultiEpg = {},
        onPickBouquet = {}
    ).map { it.id }
}
