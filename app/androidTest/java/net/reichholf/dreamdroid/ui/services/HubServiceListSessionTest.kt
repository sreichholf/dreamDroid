package net.reichholf.dreamdroid.ui.services

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubServiceListSessionTest {
    @Test
    fun staleReloadDoesNotReplaceNewerServiceList() {
        val session = HubServiceListSession()
        val state = ServiceListState()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.listState = state
        session.refresh = ComposeRefreshState()

        val staleGeneration = session.beginLoad()
        val freshGeneration = session.beginLoad()
        session.applyLoadResult(
            generation = staleGeneration,
            success = true,
            rows = listOf(
                ServiceNowNext(
                    serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                    serviceName = "Stale bouquet"
                )
            ),
            errorText = null
        )
        assertEquals(emptyList<String>(), state.items.map { it.name })

        session.applyLoadResult(
            generation = freshGeneration,
            success = true,
            rows = listOf(
                ServiceNowNext(
                    serviceReference = "1:0:1:2:1:1:1:0:0:0:",
                    serviceName = "Fresh bouquet"
                )
            ),
            errorText = null
        )
        assertEquals(listOf("Fresh bouquet"), state.items.map { it.name })

        session.applyLoadResult(
            generation = staleGeneration,
            success = false,
            rows = emptyList(),
            errorText = "timeout"
        )
        assertEquals(listOf("Fresh bouquet"), state.items.map { it.name })
    }

    @Test
    fun serviceListToolbarHasMultiEpgAndPopupDoesNot() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val inflater = android.view.MenuInflater(ctx)
        val toolbarAnchor = android.widget.TextView(ctx)
        val toolbarMenu = androidx.appcompat.widget.PopupMenu(ctx, toolbarAnchor).menu
        inflater.inflate(R.menu.servicelistpage, toolbarMenu)
        assertNotNull(toolbarMenu.findItem(R.id.menu_multiepg))
        assertNotNull(toolbarMenu.findItem(R.id.menu_epg_list))

        val popupAnchor = android.widget.TextView(ctx)
        val popupMenu = androidx.appcompat.widget.PopupMenu(ctx, popupAnchor).menu
        inflater.inflate(R.menu.popup_servicelist, popupMenu)
        assertNotNull(popupMenu.findItem(R.id.menu_browse_epg))
        assertNull(popupMenu.findItem(R.id.menu_multiepg))
    }

    @Test
    fun serviceListEpgJumpsPersistDrawerMode() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val session = HubServiceListSession()
        session.context = ctx
        session.currentRef = "1:7:1:B"
        session.currentName = "Favourites"
        DrawerEpgMode.saveList(ctx)
        session.openMultiEpg()
        assertTrue(DrawerEpgMode.isMulti(prefs))
        session.openListEpg()
        assertFalse(DrawerEpgMode.isMulti(prefs))
    }

    @Test
    fun serviceListHidesEpgActionsUntilBouquetIsSet() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val session = HubServiceListSession()
        session.context = ctx
        val inflater = android.view.MenuInflater(ctx)
        val menu = androidx.appcompat.widget.PopupMenu(ctx, android.widget.TextView(ctx)).menu
        session.onCreateMenu(menu, inflater)
        session.currentRef = ""
        session.onPrepareMenu(menu)
        assertFalse(menu.findItem(R.id.menu_multiepg).isVisible)
        assertFalse(menu.findItem(R.id.menu_epg_list).isVisible)
        session.currentRef = "1:7:1:B"
        session.onPrepareMenu(menu)
        assertTrue(menu.findItem(R.id.menu_multiepg).isVisible)
        assertTrue(menu.findItem(R.id.menu_epg_list).isVisible)
    }
}
