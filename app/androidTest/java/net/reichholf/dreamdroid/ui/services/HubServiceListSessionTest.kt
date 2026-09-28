package net.reichholf.dreamdroid.ui.services

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.testutil.CurrentProfileRule
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HubServiceListSessionTest {
    @get:Rule
    val currentProfile = CurrentProfileRule()

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
    fun serviceListTopBarHasEpgJumpsAndPopupDoesNot() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val session = HubServiceListSession()
        session.context = ctx
        session.currentRef = "1:7:1:B"
        assertEquals(
            listOf(R.id.menu_multiepg, R.id.menu_epg_list, R.id.menu_default),
            actionIds(session)
        )

        assertEquals(
            listOf(
                ServiceRowAction.CurrentEvent,
                ServiceRowAction.BrowseEpg,
                ServiceRowAction.Zap,
                ServiceRowAction.Stream
            ),
            session.rowActions(ServiceNowNext(serviceReference = "1:0:1:1:1:1:1:0:0:0:"))
        )

        DreamDroid.enableNowNext()
        assertTrue(
            ServiceRowAction.NextEvent in session.rowActions(
                ServiceNowNext(serviceReference = "1:0:1:1:1:1:1:0:0:0:", next = Event())
            )
        )
        DreamDroid.disableNowNext()
        assertFalse(
            ServiceRowAction.NextEvent in session.rowActions(
                ServiceNowNext(serviceReference = "1:0:1:1:1:1:1:0:0:0:", next = Event())
            )
        )
        DreamDroid.enableNowNext()
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
        val session = HubServiceListSession()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.currentRef = ""
        assertEquals(listOf(R.id.menu_default), actionIds(session))
        session.currentRef = "1:7:1:B"
        assertTrue(actionIds(session).containsAll(listOf(R.id.menu_multiepg, R.id.menu_epg_list)))
    }

    private fun actionIds(session: HubServiceListSession): List<Int> = session.topBarActions(
        multiEpgLabel = "MultiEPG",
        listEpgLabel = "EPG list",
        setDefaultLabel = "Set default",
        resetDefaultLabel = "Reset default"
    ).map { it.id }
}
