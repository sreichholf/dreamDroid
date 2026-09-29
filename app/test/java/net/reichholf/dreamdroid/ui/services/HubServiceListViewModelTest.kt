package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [HubServiceListViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class HubServiceListViewModelTest {
    private val receiver = EpgTestReceiver()
    private val services = receiver.services
    private val viewModels = mutableListOf<HubServiceListViewModel>()
    private val preferences = MemorySharedPreferences()
    private val settings = SettingsRepository(preferences)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = ::routes
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheTabWithNowNextFromTheReceiver() = runBlocking {
        val state = viewModel().settled()

        assertEquals(listOf("Favourites (TV)", "Das Erste HD", "--------"), state.items.names())
        assertEquals("News Now", state.items[1].nowTitle)
        assertEquals("News Next", state.items[1].nextTitle)
        assertNull(state.emptyMessage)
        assertEquals(UiText.Raw("Tab"), state.title)
        val roster = receiver.requestsTo(GET_SERVICES).single().requestUrl!!
        assertEquals(TAB, roster.queryParameter("sRef"))
        val nowNext = receiver.requestsTo(EPG_NOW_NEXT).single().requestUrl!!
        assertEquals(TAB, nowNext.queryParameter("bRef"))
    }

    @Test
    fun titleSaysLoadingWhileRefreshing() = runBlocking {
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            release.await(5, TimeUnit.SECONDS)
            routes(request)
        }
        val viewModel = viewModel()

        val loading = viewModel.uiState.value
        assertTrue(loading.refreshing)
        assertEquals(UiText.Resource(R.string.loading), loading.title)
        assertEquals(UiText.Resource(R.string.loading), loading.emptyMessage)
        release.countDown()
        assertEquals(UiText.Raw("Tab"), viewModel.settled().title)
    }

    @Test
    fun liveAnswerOfAHubTabStoresTheRosterAndFillsTheEpg() = runBlocking {
        receiver.writeTabStrip(Service(TAB, "Tab"))

        viewModel().settled()
        awaitRequest(EPG_MULTI)

        assertEquals(
            listOf("Favourites (TV)", "Das Erste HD", "--------"),
            services.cachedNowNext(TAB)!!.map { it.serviceName }
        )
        assertEquals(
            TAB,
            receiver.requestsTo(EPG_MULTI).single().requestUrl!!.queryParameter("bRef")
        )
    }

    @Test
    fun listOutsideTheHubTabsIsNotStored() = runBlocking {
        viewModel().settled()

        assertNull(services.cachedNowNext(TAB))
        Thread.sleep(100)
        assertTrue(receiver.requestsTo(EPG_MULTI).isEmpty())
    }

    @Test
    fun offlineFirstOpenPaintsTheRosterWithoutTheReceiver() = runBlocking {
        seedRoster("Cached HD")
        receiver.goOffline()

        val state = viewModel().settled()

        assertEquals(listOf("Cached HD"), state.items.names())
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun forcedRefreshAsksTheReceiverAndKeepsTheRosterWhenItFails() = runBlocking {
        seedRoster("Cached HD")
        receiver.goOffline()
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.reload(forceRefresh = true)
        val state = viewModel.settled()

        assertEquals(1, receiver.requestsTo(GET_SERVICES).size)
        assertEquals(listOf("Cached HD"), state.items.names())
        assertNull(state.emptyMessage)
    }

    @Test
    fun checkingSessionPaintsTheRosterThenTheReceiverAnswer() = runBlocking {
        seedRoster("Cached HD")
        receiver.sessions.resetForProfileChange()
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            release.await(5, TimeUnit.SECONDS)
            routes(request)
        }
        val viewModel = viewModel()

        withTimeout(5_000L) {
            viewModel.uiState.first { it.items.names() == listOf("Cached HD") }
        }
        release.countDown()
        val live = viewModel.uiState.first { it.items.names().contains("Das Erste HD") }

        assertEquals(listOf("Favourites (TV)", "Das Erste HD", "--------"), live.items.names())
    }

    @Test
    fun failureWithoutRosterShowsTheError() = runBlocking {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().settled()

        assertTrue(state.items.isEmpty())
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun emptyListSaysSo() = runBlocking {
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                GET_SERVICES -> MockResponse().setBody("<e2servicelist></e2servicelist>")
                else -> routes(request)
            }
        }

        val state = viewModel().settled()

        assertEquals(UiText.Resource(R.string.no_list_item), state.emptyMessage)
    }

    @Test
    fun openedFolderLoadsAndBackReturnsToTheTab() = runBlocking {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.settled()

        viewModel.openDirectory(0)
        val folder = viewModel.settled()

        assertEquals(FAVOURITES, folder.currentRef)
        assertEquals(UiText.Raw("Favourites (TV)"), folder.title)
        assertEquals(1, folder.historyDepth)
        assertEquals(FAVOURITES, receiver.requestsTo(GET_SERVICES).last().sRef())
        assertEquals(FAVOURITES, readHubServiceListSaved(handle, TAB).currentRef)

        assertTrue(viewModel.navigateUp())
        val tab = viewModel.settled()
        assertEquals(TAB, tab.currentRef)
        assertEquals(0, tab.historyDepth)
        assertEquals(TAB, receiver.requestsTo(GET_SERVICES).last().sRef())
        assertFalse(viewModel.navigateUp())
    }

    @Test
    fun savedFolderRestoresWithOneStepBackToTheTab() = runBlocking {
        val handle = SavedStateHandle()
        HubServiceListSaved(FAVOURITES, "Favourites (TV)").writeTo(handle, TAB)

        val viewModel = viewModel(handle)
        val restored = viewModel.settled()

        assertEquals(FAVOURITES, restored.currentRef)
        assertEquals(1, restored.historyDepth)
        assertEquals(FAVOURITES, receiver.requestsTo(GET_SERVICES).single().sRef())

        viewModel.upOrReload()
        val tab = viewModel.settled()
        assertEquals(TAB, tab.currentRef)
        assertEquals(0, tab.historyDepth)
    }

    @Test
    fun reselectOnTheTabReloads() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.upOrReload()
        viewModel.settled()

        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun staleLoadDoesNotReplaceANewerList() = runBlocking {
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            if (request.sRef() == FAVOURITES || request.bRef() == FAVOURITES) {
                release.await(5, TimeUnit.SECONDS)
                MockResponse().setBody(serviceList("Stale HD"))
            } else {
                routes(request)
            }
        }
        val handle = SavedStateHandle()
        HubServiceListSaved(FAVOURITES, "Favourites (TV)").writeTo(handle, TAB)
        val viewModel = viewModel(handle)

        viewModel.navigateUp()
        val fresh = viewModel.settled()
        assertEquals(listOf("Favourites (TV)", "Das Erste HD", "--------"), fresh.items.names())
        release.countDown()
        // Join the ViewModel instead of sleeping: the released stale answer resumes
        // the already-cancelled load, which discards it, and joining proves no
        // stale rows can still arrive before asserting the fresh list survived.
        viewModel.cancelAndJoin()
        assertEquals(fresh.items, viewModel.uiState.value.items)
    }

    @Test
    fun sessionChangeLoadsAgain() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()

        receiver.goOffline()
        awaitRequest(GET_SERVICES, count = 2)
    }

    @Test
    fun rowMenuOffersNextEventOnlyWithNowNext() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.onItemMenu(1)
        assertEquals(ServiceRowAction.entries, viewModel.uiState.value.menu?.actions)
        assertEquals("1:${CHANNEL_44D}", viewModel.uiState.value.menu?.rowKey)
        viewModel.onMenuDismiss()
        assertNull(viewModel.uiState.value.menu)

        DreamDroid.disableNowNext()
        try {
            viewModel.onItemMenu(1)
            assertEquals(
                listOf(
                    ServiceRowAction.CurrentEvent,
                    ServiceRowAction.BrowseEpg,
                    ServiceRowAction.Zap,
                    ServiceRowAction.Stream
                ),
                viewModel.uiState.value.menu?.actions
            )
        } finally {
            DreamDroid.enableNowNext()
        }
    }

    @Test
    fun menuActionsHandTheRowToThePage() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(1)

        viewModel.onMenuAction(ServiceRowAction.CurrentEvent)
        val show = viewModel.uiState.value.effect as HubServiceEffect.ShowEvent
        assertEquals("News Now", show.event.title)
        assertNull(viewModel.uiState.value.menu)
        viewModel.onEffectHandled()
        assertNull(viewModel.uiState.value.effect)

        viewModel.onMenuAction(ServiceRowAction.NextEvent)
        assertEquals(
            "News Next",
            (viewModel.uiState.value.effect as HubServiceEffect.ShowEvent).event.title
        )

        viewModel.onMenuAction(ServiceRowAction.BrowseEpg)
        assertEquals(
            HubServiceEffect.ServiceEpg(CHANNEL_44D, "Das Erste HD"),
            viewModel.uiState.value.effect
        )

        viewModel.onMenuAction(ServiceRowAction.Stream)
        val stream = viewModel.uiState.value.effect as HubServiceEffect.Stream
        assertEquals(CHANNEL_44D, stream.row.serviceReference)
        assertEquals(TAB, stream.bouquetRef)
        viewModel.onStreamFailed()
        assertNull(viewModel.uiState.value.effect)
        assertEquals(
            UiText.Resource(R.string.missing_stream_player),
            viewModel.uiState.value.userMessage
        )
    }

    @Test
    fun zapSendsTheChannelAndReportsTheReply() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.zap(1)
        val state = viewModel.uiState.first { it.userMessage != null }

        assertEquals(UiText.Raw("Zapped"), state.userMessage)
        assertEquals(HubServiceEffect.Zapped, state.effect)
        assertEquals(CHANNEL_44D, receiver.requestsTo(ZAP).single().sRef())
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun blockedSessionIgnoresOnlineOnlyActions() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()
        receiver.goOffline()
        viewModel.settled()

        viewModel.zap(1)
        viewModel.onItemMenu(1)
        viewModel.onMenuAction(ServiceRowAction.Zap)
        viewModel.onItemMenu(1)
        viewModel.onMenuAction(ServiceRowAction.Stream)

        Thread.sleep(100)
        assertTrue(receiver.requestsTo(ZAP).isEmpty())
        assertNull(viewModel.uiState.value.effect)
    }

    @Test
    fun defaultBouquetToggles() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()
        assertFalse(viewModel.uiState.value.isDefaultBouquet)

        viewModel.toggleDefaultBouquet()
        val set = viewModel.uiState.first { it.isDefaultBouquet }
        assertEquals(TAB, receiver.profiles.repository.requireCurrent().defaultBouquetTv)
        assertEquals(
            UiText.Resource(
                R.string.default_bouquet_set_to_name,
                listOf(UiText.Resource(R.string.default_bouquet_set_to), "Tab")
            ),
            set.userMessage
        )
        viewModel.onMessageShown()

        viewModel.toggleDefaultBouquet()
        val reset = viewModel.uiState.first { !it.isDefaultBouquet }
        assertNull(receiver.profiles.repository.requireCurrent().defaultBouquetTv)
        assertNull(reset.userMessage)
    }

    @Test
    fun epgJumpsOpenTheListOnScreenAndRememberTheDrawerEpgMode() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.openMultiEpg()

        assertEquals(HubServiceEffect.MultiEpg(TAB, "Tab"), viewModel.uiState.value.effect)
        assertTrue(DrawerEpgMode.isMulti(preferences))
        assertTrue(settings.drawerEpgMulti)
        viewModel.onEffectHandled()

        viewModel.openListEpg()

        assertEquals(HubServiceEffect.ListEpg(TAB, "Tab"), viewModel.uiState.value.effect)
        assertEquals(
            DrawerEpgMode.LIST,
            preferences.getString(DreamDroid.PREFS_KEY_DRAWER_EPG_MODE, null)
        )
        assertFalse(settings.drawerEpgMulti)
    }

    @Test
    fun topBarHasEpgJumpsOnceAListIsOpen() {
        val none = HubServiceListUiState(currentRef = "", currentName = "")
        assertEquals(listOf(Statics.ITEM_SET_DEFAULT), actions(none).map { it.id })

        val open = none.copy(currentRef = TAB)
        assertEquals(
            listOf(R.id.menu_multiepg, R.id.menu_epg_list, Statics.ITEM_SET_DEFAULT),
            actions(open).map { it.id }
        )
        assertEquals("Set default", actions(open).last().label)
        assertEquals(
            "Reset default",
            actions(open.copy(isDefaultBouquet = true)).last().label
        )
    }

    private fun actions(state: HubServiceListUiState) = hubServiceTopBarActions(
        state = state,
        multiEpgLabel = "MultiEPG",
        listEpgLabel = "EPG list",
        setDefaultLabel = "Set default",
        resetDefaultLabel = "Reset default",
        onMultiEpg = {},
        onListEpg = {},
        onToggleDefault = {}
    )

    private suspend fun seedRoster(name: String) {
        receiver.writeTabStrip(Service(TAB, "Tab"))
        assertTrue(services.persistRoster(TAB, TAB, listOf(ServiceNowNext(CHANNEL_44D, name))))
    }

    private suspend fun awaitRequest(path: String, count: Int = 1) {
        withTimeout(5_000L) {
            while (receiver.requestsTo(path).size < count) {
                delay(20)
            }
        }
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            GET_SERVICES -> MockResponse().setBody(loadWebFixture("getservices.xml"))

            EPG_NOW_NEXT -> MockResponse().setBody(loadWebFixture("epgnownext.xml"))

            EPG_MULTI -> MockResponse().setBody(loadWebFixture("epgmulti.xml"))

            ZAP -> MockResponse().setBody(
                "<e2simplexmlresult><e2state>True</e2state>" +
                    "<e2statetext>Zapped</e2statetext></e2simplexmlresult>"
            )

            else -> MockResponse().setResponseCode(404)
        }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): HubServiceListViewModel =
        HubServiceListViewModel(
            Service(TAB, "Tab"),
            handle,
            services,
            receiver.repository,
            ReceiverRepository(
                enigmaClients(receiver.profiles.repository),
                receiver.profiles.repository
            ),
            receiver.profiles.repository,
            receiver.sessions,
            settings
        ).also { viewModels += it }

    private suspend fun HubServiceListViewModel.settled(): HubServiceListUiState =
        withTimeout(5_000L) { uiState.first { !it.refreshing } }

    private fun List<ServiceListItem>.names(): List<String> = map { it.name }

    private fun RecordedRequest.sRef(): String? = requestUrl?.queryParameter("sRef")

    private fun RecordedRequest.bRef(): String? = requestUrl?.queryParameter("bRef")

    private fun serviceList(name: String): String =
        "<e2servicelist><e2service><e2servicereference>$CHANNEL_44D</e2servicereference>" +
            "<e2servicename>$name</e2servicename></e2service></e2servicelist>"

    private companion object {
        const val TAB = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.tab.tv\" ORDER BY bouquet"

        /** The folder row of `getservices.xml`. */
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"

        /** The channel of `getservices.xml` and `epgnownext.xml`. */
        const val CHANNEL_44D = "1:0:1:6DCA:44D:1:C00000:0:0:0:"

        const val GET_SERVICES = "/web/getservices"
        const val EPG_NOW_NEXT = "/web/epgnownext"
        const val EPG_MULTI = "/web/epgmulti"
        const val ZAP = "/web/zap"
    }
}
