package net.reichholf.dreamdroid.ui.pick

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.tv.ui.tvTimerServicePickRows
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TimerServicePickViewModel] over the real [net.reichholf.dreamdroid.data.ServiceRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerServicePickViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<TimerServicePickViewModel>()

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
    fun startsOnTheBouquetList() = runBlocking {
        val state = viewModel().settled()

        assertTrue(state.showsBouquets)
        assertEquals(listOf("Section", "Favourites", "Radio"), state.items.map { it.name })
        assertEquals(UiText.Resource(R.string.service), state.title)
    }

    @Test
    fun bouquetRowOpensItsChannelsAndChannelRowIsThePick() = runBlocking {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.settled()

        assertNull(viewModel.onRowClick(MARKER))
        assertNull(viewModel.onRowClick(BOUQUET))
        val channels = viewModel.settled()

        assertEquals(BOUQUET.reference, channels.bouquetRef)
        assertEquals(UiText.Raw("Favourites"), channels.title)
        assertEquals(listOf("Das Erste HD"), channels.items.map { it.name })
        assertEquals(
            BOUQUET.reference,
            receiver.requests.last().requestUrl!!.queryParameter("sRef")
        )
        assertEquals(BOUQUET.reference, readTimerServicePickSaved(handle).bouquetRef)
        assertEquals(CHANNEL, viewModel.onRowClick(CHANNEL))
    }

    @Test
    fun backShowsTheLoadedBouquetsWithoutAskingAgain() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onRowClick(BOUQUET)
        viewModel.settled()
        val requests = receiver.requests.size

        viewModel.showBouquetList()

        val state = viewModel.uiState.value
        assertTrue(state.showsBouquets)
        assertEquals(listOf("Section", "Favourites", "Radio"), state.items.map { it.name })
        assertEquals(requests, receiver.requests.size)
    }

    @Test
    fun savedBouquetRestoresItsChannels() = runBlocking {
        val handle = SavedStateHandle()
        TimerServicePickSaved(BOUQUET.reference, BOUQUET.name).writeTo(handle)

        val state = viewModel(handle).settled()

        assertEquals(BOUQUET.reference, state.bouquetRef)
        assertEquals(listOf("Das Erste HD"), state.items.map { it.name })
    }

    @Test
    fun shownAgainKeepsALoadedList() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()
        val requests = receiver.requests.size

        viewModel.onShown()

        assertEquals(requests, receiver.requests.size)
    }

    @Test
    fun shownAgainRetriesAFailedList() = runBlocking {
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()
        assertTrue(viewModel.settled().items.isEmpty())

        receiver.answer = ::routes
        viewModel.onShown()

        assertEquals(3, viewModel.settled().items.size)
    }

    @Test
    fun failedChannelsFallBackToTheRoster() = runBlocking {
        receiver.writeTabStrip(BOUQUET)
        receiver.services.persistRoster(
            BOUQUET.reference,
            BOUQUET.reference,
            listOf(ServiceNowNext(CHANNEL.reference, "Cached HD"))
        )
        val handle = SavedStateHandle()
        TimerServicePickSaved(BOUQUET.reference, BOUQUET.name).writeTo(handle)
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel(handle).settled()

        assertEquals(listOf("Cached HD"), state.items.map { it.name })
        assertNull(state.emptyMessage)
    }

    @Test
    fun failedChannelsWithoutRosterShowTheError() = runBlocking {
        val handle = SavedStateHandle()
        TimerServicePickSaved(BOUQUET.reference, BOUQUET.name).writeTo(handle)
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel(handle).settled()

        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun staleBouquetLoadDoesNotReplaceTheChannels() = runBlocking {
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            if (request.requestUrl?.queryParameter("sRef") == TV_ROOTS[0]) {
                release.await(5, TimeUnit.SECONDS)
            }
            routes(request)
        }
        val viewModel = viewModel()

        viewModel.onRowClick(BOUQUET)
        val channels = viewModel.settled()
        release.countDown()
        Thread.sleep(200)

        assertEquals(listOf("Das Erste HD"), channels.items.map { it.name })
        assertSame(channels.items, viewModel.uiState.value.items)
    }

    @Test
    fun tvPickerHidesMarkersOnlyOnTheBouquetList() {
        val bouquets = TimerServicePickUiState(items = listOf(MARKER, BOUQUET))
        assertEquals(listOf(BOUQUET), tvTimerServicePickRows(bouquets))

        val channels = bouquets.copy(bouquetRef = BOUQUET.reference)
        assertEquals(listOf(MARKER, BOUQUET), tvTimerServicePickRows(channels))
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.queryParameter("sRef")) {
            TV_ROOTS[0] -> MockResponse().setBody(list(MARKER, BOUQUET))
            RADIO_ROOTS[0] -> MockResponse().setBody(list(RADIO))
            BOUQUET.reference -> MockResponse().setBody(list(CHANNEL))
            else -> MockResponse().setResponseCode(404)
        }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        TimerServicePickViewModel(handle, receiver.services).also { viewModels += it }

    private suspend fun TimerServicePickViewModel.settled(): TimerServicePickUiState =
        withTimeout(5_000L) { uiState.first { !it.refreshing } }

    private fun list(vararg services: Service): String =
        services.joinToString("", "<e2servicelist>", "</e2servicelist>") {
            "<e2service><e2servicereference>${it.reference.replace("\"", "&quot;")}" +
                "</e2servicereference><e2servicename>${it.name}</e2servicename></e2service>"
        }

    private companion object {
        val MARKER = Service("1:64:0:0:0:0:0:0:0:0::Section", "Section")
        val BOUQUET = Service(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            "Favourites"
        )
        val RADIO = Service(
            "1:7:2:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.radio\" ORDER BY bouquet",
            "Radio"
        )
        val CHANNEL = Service("1:0:1:6DCA:44D:1:C00000:0:0:0:", "Das Erste HD")
    }
}
