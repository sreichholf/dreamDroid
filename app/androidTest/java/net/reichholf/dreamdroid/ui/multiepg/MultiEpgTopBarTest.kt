package net.reichholf.dreamdroid.ui.multiepg

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.testutil.recordingNavHandle
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.nav.Epg
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

    @Test
    fun openListEpgForwardsTheTabRoot() {
        val folder = "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
        val tab = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.tab.tv\" ORDER BY bouquet"
        var routed: Epg? = null
        val session = MultiEpgTopBarSession()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.bouquetRef = folder
        session.bouquetName = "Satellites"
        session.visibleStartSec = 1_234L
        session.tabRootRef = tab
        session.handle = recordingNavHandle(onEpg = { routed = it })

        session.topBarActions("EPG list").single().onClick()

        assertEquals(folder, routed?.serviceRef)
        assertEquals(tab, routed?.tabRootRef)
        assertEquals(1_234L, routed?.timeSec ?: -1L)
    }
}
