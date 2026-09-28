package net.reichholf.dreamdroid.ui.epg

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.BOUQUET
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.event
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.nav.Epg
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [EpgBouquetViewModel] over the real [net.reichholf.dreamdroid.data.EpgRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgBouquetViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<EpgBouquetViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun firstShowSeedsBouquetAndTimeFromTheRouteAndLoads() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)

        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = LATER)
        val state = viewModel.uiState.first { it.events.isNotEmpty() }

        assertEquals(listOf("Tagesschau", "N/A"), state.events.map { it.title })
        assertNull(state.emptyMessage)
        assertFalse(state.refreshing)
        assertEquals(UiText.Raw("Favourites"), state.title)
        assertEquals(NOW.toInt(), state.timeSec)
        val url = receiver.server.takeRequest().requestUrl!!
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(NOW.toString(), url.queryParameter("time"))
        val saved = readEpgBouquetNavSaved(handle)
        assertEquals(BOUQUET, saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
        assertEquals(NOW, saved.timeSec)
    }

    @Test
    fun routeWithoutTimeStartsNow() {
        val viewModel = viewModel()

        viewModel.onShown(Epg(BOUQUET, "Favourites"), remountEpoch = 0, nowSec = LATER)

        assertEquals(LATER, viewModel.uiState.value.timeSec)
    }

    @Test
    fun restoredSnapshotWinsOverTheRouteAndAPickedBouquetStays() {
        val handle = SavedStateHandle()
        EpgBouquetNavSaved(PICKED, "Picked TV", LATER.toLong(), false).writeTo(handle)
        val viewModel = viewModel(handle)

        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())

        val state = viewModel.uiState.value
        assertEquals(PICKED, state.bouquetRef)
        assertEquals("Picked TV", state.bouquetName)
        assertEquals(LATER, state.timeSec)
    }

    @Test
    fun remountResetsBouquetAndTimeToTheRoute() = runTest {
        val viewModel = viewModel()
        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        viewModel.onBouquetPicked(PICKED, "Picked TV")
        viewModel.uiState.first { it.bouquetRef == PICKED && !it.refreshing }
        viewModel.onScrolledToTop()

        viewModel.onShown(ROUTE, remountEpoch = 1, nowSec = LATER)

        val state = viewModel.uiState.value
        assertEquals(BOUQUET, state.bouquetRef)
        assertEquals(NOW.toInt(), state.timeSec)
        assertTrue(state.scrollToTop)
    }

    @Test
    fun noBouquetOpensThePickerOnce() {
        val viewModel = viewModel()

        viewModel.onShown(Epg(), remountEpoch = 0, nowSec = NOW.toInt())

        val state = viewModel.uiState.value
        assertTrue(state.openPicker)
        assertTrue(state.waitingForPicker)
        viewModel.onPickerOpened()
        viewModel.reload()
        assertFalse(viewModel.uiState.value.openPicker)
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun pickedBouquetLoadsInsteadOfTheRouteDefault() = runTest {
        val viewModel = viewModel()
        viewModel.onShown(Epg(), remountEpoch = 0, nowSec = NOW.toInt())
        viewModel.onPickerOpened()

        viewModel.onBouquetPicked(PICKED, "Picked TV")
        val state = viewModel.uiState.first { it.events.isNotEmpty() }

        assertEquals(PICKED, state.bouquetRef)
        assertEquals("Picked TV", state.bouquetName)
        assertFalse(state.waitingForPicker)
        assertTrue(state.scrollToTop)
        assertEquals(PICKED, receiver.server.takeRequest().requestUrl?.queryParameter("bRef"))
        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        assertEquals(PICKED, viewModel.uiState.value.bouquetRef)
    }

    @Test
    fun offlineLoadPaintsRoomWithoutAskingTheReceiver() = runTest {
        receiver.writeChunk(BOUQUET, NOW, listOf(event("News", NOW)))
        receiver.goOffline()
        val viewModel = viewModel()

        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        val state = viewModel.uiState.first { it.events.isNotEmpty() }

        assertEquals(listOf("News"), state.events.map { it.title })
        assertNull(state.emptyMessage)
        assertFalse(state.refreshing)
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun failedRefreshFallsBackToRoom() = runTest {
        receiver.writeChunk(BOUQUET, NOW, listOf(event("News", NOW)))
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()
        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        viewModel.uiState.first { !it.refreshing }

        viewModel.reload(forceRefresh = true)
        val state = viewModel.uiState.first { !it.refreshing }

        assertEquals(listOf("News"), state.events.map { it.title })
        assertNull(state.emptyMessage)
    }

    @Test
    fun failureWithoutCacheShowsTheContentError() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()

        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        val state = viewModel.uiState.first { !it.refreshing }

        assertEquals(emptyList<Any>(), state.events)
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun emptyListSaysSo() = runTest {
        receiver.answer = { MockResponse().setBody("<e2eventlist></e2eventlist>") }
        val viewModel = viewModel()

        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        val state = viewModel.uiState.first { !it.refreshing }

        assertEquals(UiText.Resource(R.string.no_list_item), state.emptyMessage)
    }

    @Test
    fun newInstantReloadsAtThatTime() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        viewModel.uiState.first { !it.refreshing }

        viewModel.onInstantSet(LATER)
        viewModel.uiState.first { !it.refreshing }

        assertEquals(2, receiver.server.requestCount)
        receiver.server.takeRequest()
        assertEquals(
            LATER.toString(),
            receiver.server.takeRequest().requestUrl?.queryParameter("time")
        )
        assertEquals(LATER.toLong(), readEpgBouquetNavSaved(handle).timeSec)
    }

    @Test
    fun sessionChangeReloadsOnlyOnceBound() = runTest {
        val viewModel = viewModel()
        receiver.goOffline()
        assertEquals(0, receiver.server.requestCount)
        viewModel.onShown(ROUTE, remountEpoch = 0, nowSec = NOW.toInt())
        viewModel.uiState.first { !it.refreshing }

        receiver.sessions.onSuccess()
        viewModel.uiState.first { !it.refreshing }

        assertEquals(2, receiver.server.requestCount)
    }

    @Test
    fun titleSaysLoadingWhileRefreshing() {
        val state = EpgBouquetUiState(bouquetName = "Favourites", refreshing = true)

        assertEquals(UiText.Resource(R.string.loading), state.title)
        assertEquals(UiText.Resource(R.string.epg), EpgBouquetUiState().title)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        EpgBouquetViewModel(handle, receiver.repository, receiver.sessions)
            .also { viewModels += it }

    private companion object {
        const val NOW = 1_893_456_000L
        const val LATER = 1_893_470_400
        val ROUTE = Epg(BOUQUET, "Favourites", NOW)
        const val PICKED =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
    }
}
