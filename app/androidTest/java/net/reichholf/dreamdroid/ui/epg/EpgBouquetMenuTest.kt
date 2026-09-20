package net.reichholf.dreamdroid.ui.epg

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
        val session = EpgBouquetSession()
        session.context = ctx
        val inflater = android.view.MenuInflater(ctx)
        val menu = androidx.appcompat.widget.PopupMenu(ctx, android.widget.TextView(ctx)).menu
        session.onCreateMenu(menu, inflater)
        session.bouquetRef = ""
        session.onPrepareMenu(menu)
        assertFalse(menu.findItem(R.id.menu_multiepg).isVisible)
        session.bouquetRef = "1:7:1:B"
        session.onPrepareMenu(menu)
        assertTrue(menu.findItem(R.id.menu_multiepg).isVisible)
    }

    @Test
    fun openMultiEpgPersistsDrawerMode() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        DrawerEpgMode.saveList(ctx)
        val session = EpgBouquetSession()
        session.context = ctx
        session.bouquetRef = "1:7:1:B"
        session.bouquetName = "Favourites"
        session.openMultiEpg()
        assertTrue(DrawerEpgMode.isMulti(prefs))
        DrawerEpgMode.saveList(ctx)
        assertFalse(DrawerEpgMode.isMulti(prefs))
    }
}
