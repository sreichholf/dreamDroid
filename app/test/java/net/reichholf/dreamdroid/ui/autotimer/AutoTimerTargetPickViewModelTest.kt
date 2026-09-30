package net.reichholf.dreamdroid.ui.autotimer

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [AutoTimerTargetPickViewModel] over the real service repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoTimerTargetPickViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<AutoTimerTargetPickViewModel>()

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
    fun picksWholeBouquetsAndSingleChannelsInOrder() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        assertEquals(
            listOf("Section", "Favourites", "Radio"),
            viewModel.settled().rows.map {
                it.name
            }
        )

        viewModel.toggle(MARKER)
        viewModel.toggle(RADIO)
        viewModel.open(BOUQUET)
        val channels = viewModel.settled()
        viewModel.toggle(CHANNEL)

        assertEquals(UiText.Raw("Favourites"), channels.title)
        val picked = listOf(
            Target.Bouquet(RADIO.reference, "Radio"),
            Target.Channel(CHANNEL.reference, "Das Erste HD")
        )
        assertEquals(picked, viewModel.uiState.value.selected)

        // Process death keeps the open bouquet and the picks.
        val restored = viewModel(handle)
        assertEquals(picked, restored.uiState.value.selected)
        assertEquals(
            Target.Bouquet(BOUQUET.reference, "Favourites"),
            restored.uiState.value.bouquet
        )
    }

    @Test
    fun aSecondTapTakesThePickBack() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.toggle(RADIO)
        viewModel.toggle(RADIO)

        assertEquals(emptyList<Target>(), viewModel.uiState.value.selected)
    }

    @Test
    fun backShowsTheBouquetsAgainAndKeepsThePicks() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.open(BOUQUET)
        viewModel.settled()
        viewModel.toggle(CHANNEL)
        val requests = receiver.requests.size

        viewModel.showBouquets()

        val state = viewModel.uiState.value
        assertNull(state.bouquet)
        assertEquals(listOf("Section", "Favourites", "Radio"), state.rows.map { it.name })
        assertEquals(1, state.selected.size)
        assertEquals(requests, receiver.requests.size)
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.queryParameter("sRef")) {
            TV_ROOTS[0] -> MockResponse().setBody(list(MARKER, BOUQUET))
            RADIO_ROOTS[0] -> MockResponse().setBody(list(RADIO))
            BOUQUET.reference -> MockResponse().setBody(list(CHANNEL))
            else -> MockResponse().setResponseCode(404)
        }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        AutoTimerTargetPickViewModel(handle, receiver.services).also { viewModels += it }

    private suspend fun AutoTimerTargetPickViewModel.settled(): AutoTimerTargetPickUiState =
        withTimeout(5_000L) { uiState.first { !it.refreshing && it.rows.isNotEmpty() } }

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
