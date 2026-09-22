package net.reichholf.dreamdroid.ui.epg

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgBouquetMenuTest {
    @Test
    fun listEpgToolbarHasMultiEpgAndBouquetPick() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val inflater = android.view.MenuInflater(ctx)
        val anchor = android.widget.TextView(ctx)
        val menu = androidx.appcompat.widget.PopupMenu(ctx, anchor).menu
        inflater.inflate(R.menu.epgbouquet, menu)
        assertNotNull(menu.findItem(R.id.menu_multiepg))
        assertNotNull(menu.findItem(R.id.menu_pick_bouquet))
    }

    @Test
    fun listEpgHidesMultiEpgUntilBouquetIsSet() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val app = ctx.applicationContext as Application
        val viewModel = EpgBouquetViewModel(app, SavedStateHandle())
        val provider = EpgBouquetMenuProvider(viewModel) {}
        val inflater = android.view.MenuInflater(ctx)
        val menu = androidx.appcompat.widget.PopupMenu(ctx, android.widget.TextView(ctx)).menu
        provider.onCreateMenu(menu, inflater)
        provider.onPrepareMenu(menu)
        assertFalse(menu.findItem(R.id.menu_multiepg).isVisible)
        viewModel.ensureEpoch(
            epoch = 1,
            leafRef = "1:7:1:B",
            leafName = "Favourites",
            leafTimeSec = null,
            nowSec = 1_700_000_000
        )
        provider.onPrepareMenu(menu)
        assertTrue(menu.findItem(R.id.menu_multiepg).isVisible)
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
}
