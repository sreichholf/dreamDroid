package net.reichholf.dreamdroid.ui.multiepg

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgTimerClock
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.BOUQUET
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.event
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [MultiEpgViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiEpgViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<MultiEpgViewModel>()
    private val chunk = MultiEpgWindows.chunkContaining(NOW)

    @Volatile
    private var timerList: String = loadWebFixture("timerlist.xml")

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
    fun loadsTheBouquetWindowFromTheReceiver() = runBlocking {
        val viewModel = viewModel()

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        val state = awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        assertEquals(UiText.Raw("Favourites"), state.title)
        assertTrue(titles(state).contains("Tagesschau"))
        assertNull(state.grid.errorMessage)
        val epgMulti = receiver.requests.first { it.requestUrl?.encodedPath == "/web/epgmulti" }
        assertEquals(BOUQUET, epgMulti.requestUrl!!.queryParameter("bRef"))
        assertEquals(chunk.startSec.toString(), epgMulti.requestUrl!!.queryParameter("time"))
        assertEquals("1440", epgMulti.requestUrl!!.queryParameter("endTime"))
    }

    @Test
    fun titleFallsBackToMultiEpg() {
        assertEquals(UiText.Resource(R.string.multiepg), viewModel().uiState.value.title)
    }

    @Test
    fun hubTabBouquetIsStoredInRoom() = runBlocking {
        receiver.writeTabStrip(Service(BOUQUET, "Favourites"))
        val viewModel = viewModel()

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        assertNotNull(
            receiver.profiles.database.epgDao().getChunk(PROFILE_ID, BOUQUET, chunk.startSec)
        )
    }

    @Test
    fun bouquetOutsideTheHubTabsIsNotStored() = runBlocking {
        val viewModel = viewModel()

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        assertNull(
            receiver.profiles.database.epgDao().getChunk(PROFILE_ID, BOUQUET, chunk.startSec)
        )
    }

    @Test
    fun offlinePaintsRoomWithoutAskingTheReceiver() = runBlocking {
        receiver.writeChunk(BOUQUET, NOW, listOf(event("Cached", NOW)))
        receiver.goOffline()
        val viewModel = viewModel()

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        val state = awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        assertEquals(listOf("Cached"), titles(state))
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun timerClocksComeFromTheReceiverTimerList() = runBlocking {
        timerList = timerList.replace("<e2disabled>1</e2disabled>", "<e2disabled>0</e2disabled>")
        val viewModel = viewModel()

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        val state = awaitState(viewModel) { it.grid.timerClocks.isNotEmpty() }

        assertEquals(listOf(MultiEpgTimerClock.Zap), state.grid.timerClocks.values.toList())
    }

    @Test
    fun offlineTimerClocksComeFromTheTimerSnapshot() = runBlocking {
        timerList = timerList.replace("<e2disabled>1</e2disabled>", "<e2disabled>0</e2disabled>")
        timerRepository().timers()
        receiver.writeChunk(
            BOUQUET,
            NOW,
            listOf(event("Tagesschau", NOW - 600, service = "1:0:1:6DCA:44D:1:C00000:0:0:0:"))
        )
        receiver.goOffline()
        val requestsBefore = receiver.server.requestCount
        val viewModel = viewModel()

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        val state = awaitState(viewModel) { it.grid.timerClocks.isNotEmpty() }

        assertEquals(listOf(MultiEpgTimerClock.Zap), state.grid.timerClocks.values.toList())
        assertEquals(requestsBefore, receiver.server.requestCount)
    }

    @Test
    fun failedWindowShowsTheError() = runBlocking {
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == "/web/epgmulti") {
                MockResponse().setResponseCode(500)
            } else {
                routes(request)
            }
        }
        val viewModel = viewModel()

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        val state = awaitState(viewModel) { it.grid.errorMessage != null && !it.grid.syncing }

        assertFalse(state.grid.pullRefreshing)
    }

    @Test
    fun refreshRefetchesAFreshWindow() = runBlocking {
        val viewModel = viewModel()
        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }
        val before = epgMultiRequests()

        viewModel.refresh()
        // uiState combines the grid state, so it can still show the idle state from before
        // refresh(): wait for the new request as well.
        val state = awaitState(viewModel) {
            !it.grid.syncing && !it.grid.pullRefreshing && epgMultiRequests() > before
        }

        assertTrue(state.grid.channels.isNotEmpty())
    }

    @Test
    fun repeatedEnsureLoadedDoesNotReload() = runBlocking {
        val viewModel = viewModel()
        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }
        val before = receiver.server.requestCount

        viewModel.ensureLoaded(0, BOUQUET, "Favourites", NOW)

        assertEquals(before, receiver.server.requestCount)
    }

    @Test
    fun visibleMinutesRestoreFromAndWriteToSavedState() {
        val handle = SavedStateHandle(mapOf(MULTI_EPG_VISIBLE_MINUTES_KEY to 240))
        val viewModel = viewModel(handle)
        assertEquals(240, viewModel.uiState.value.visibleMinutes)

        viewModel.onVisibleMinutesChange(60)

        assertEquals(60, viewModel.uiState.value.visibleMinutes)
        assertEquals(60, handle.get<Int>(MULTI_EPG_VISIBLE_MINUTES_KEY))
        assertEquals(MULTI_EPG_VISIBLE_MINUTES, viewModel().uiState.value.visibleMinutes)
    }

    private fun epgMultiRequests(): Int =
        receiver.requests.count { it.requestUrl?.encodedPath == "/web/epgmulti" }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            "/web/epgmulti" -> MockResponse().setBody(loadWebFixture("epgmulti.xml"))
            "/web/getservices" -> MockResponse().setBody(loadWebFixture("getservices.xml"))
            "/web/timerlist" -> MockResponse().setBody(timerList)
            else -> MockResponse().setResponseCode(404)
        }

    private fun timerRepository(): TimerRepository = TimerRepository(
        enigmaClients(receiver.profiles.repository),
        receiver.profiles.repository,
        receiver.profiles.database
    )

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): MultiEpgViewModel =
        MultiEpgViewModel(
            handle,
            receiver.repository,
            receiver.services,
            timerRepository(),
            receiver.profiles.repository,
            receiver.sessions
        ).also { viewModels += it }

    private suspend fun awaitState(
        viewModel: MultiEpgViewModel,
        condition: (MultiEpgUiState) -> Boolean
    ): MultiEpgUiState = withTimeout(5_000L) { viewModel.uiState.first(condition) }

    private fun titles(state: MultiEpgUiState): List<String> =
        state.grid.channels.flatMap { channel -> channel.bars.map { it.event.title } }

    companion object {
        /** Inside the fixture's events. */
        const val NOW = 1_893_456_000L + 600L
    }
}
