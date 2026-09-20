package net.reichholf.dreamdroid.ui.multiepg

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEpgMenuTest {
    @Test
    fun multiEpgToolbarHasListEpgJump() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val inflater = android.view.MenuInflater(ctx)
        val anchor = android.widget.TextView(ctx)
        val menu = androidx.appcompat.widget.PopupMenu(ctx, anchor).menu
        inflater.inflate(R.menu.multiepg, menu)
        val item = menu.findItem(R.id.menu_epg_list)
        assertNotNull(item)
        assertNotNull(item.icon)
    }

    @Test
    fun listEpgJumpHidesUntilBouquetIsSet() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val session = MultiEpgMenuSession()
        session.context = ctx
        val inflater = android.view.MenuInflater(ctx)
        val menu = androidx.appcompat.widget.PopupMenu(ctx, android.widget.TextView(ctx)).menu
        session.onCreateMenu(menu, inflater)
        session.bouquetRef = ""
        session.onPrepareMenu(menu)
        assertFalse(menu.findItem(R.id.menu_epg_list).isVisible)
        session.bouquetRef = "1:7:1:B"
        session.onPrepareMenu(menu)
        assertTrue(menu.findItem(R.id.menu_epg_list).isVisible)
    }

    @Test
    fun openListEpgPersistsDrawerMode() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        DrawerEpgMode.saveMulti(ctx)
        val session = MultiEpgMenuSession()
        session.context = ctx
        session.bouquetRef = "1:7:1:B"
        session.bouquetName = "Favourites"
        session.openListEpg()
        assertFalse(DrawerEpgMode.isMulti(prefs))
    }
}
