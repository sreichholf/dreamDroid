package net.reichholf.dreamdroid.ui.multiepg

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEpgTopBarTest {
    @Test
    fun listEpgJumpHidesUntilBouquetIsSet() {
        val session = MultiEpgTopBarSession()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.bouquetRef = ""
        assertTrue(session.topBarActions("EPG list").isEmpty())
        session.bouquetRef = "1:7:1:B"
        val action = session.topBarActions("EPG list").single()
        assertEquals(R.id.menu_epg_list, action.id)
        assertNotNull(action.iconRes)
    }

    @Test
    fun openListEpgPersistsDrawerMode() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        DrawerEpgMode.saveMulti(ctx)
        val session = MultiEpgTopBarSession()
        session.context = ctx
        session.bouquetRef = "1:7:1:B"
        session.bouquetName = "Favourites"
        session.openListEpg()
        assertFalse(DrawerEpgMode.isMulti(prefs))
    }
}
