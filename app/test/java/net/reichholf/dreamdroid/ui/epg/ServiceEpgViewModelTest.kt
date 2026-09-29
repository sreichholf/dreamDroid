package net.reichholf.dreamdroid.ui.epg

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import java.time.ZoneId
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
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.CHANNEL
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.event
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ServiceEpgViewModel] over the real [net.reichholf.dreamdroid.data.EpgRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceEpgViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<ServiceEpgViewModel>()

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
    fun loadsTheRouteServiceOnceWhenCreated() = runTest {
        val viewModel = viewModel()

        val state = viewModel.uiState.first { it.sections.isNotEmpty() }

        assertEquals(listOf("Tagesschau", "N/A"), state.titles())
        assertFalse(state.refreshing)
        assertNull(state.emptyMessage)
        assertEquals(
            UiText.Resource(
                R.string.title_with_status,
                listOf(UiText.Resource(R.string.epg), UiText.Raw("Das Erste HD"))
            ),
            state.title
        )
        assertEquals(1, receiver.server.requestCount)
        assertEquals(CHANNEL, receiver.server.takeRequest().requestUrl?.queryParameter("sRef"))
    }

    @Test
    fun sessionChangeReloads() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.refreshing }

        receiver.goOffline()
        viewModel.uiState.first { !it.refreshing }

        assertEquals(2, receiver.server.requestCount)
    }

    @Test
    fun offlineShowsRoomWithoutAskingTheReceiver() = runTest {
        val now = System.currentTimeMillis() / 1000L
        receiver.writeChunk(BOUQUET, now, listOf(event("News", start = now)))
        receiver.goOffline()

        val state = viewModel().uiState.first { it.sections.isNotEmpty() }

        assertEquals(listOf("News"), state.titles())
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun offlineScheduleIsGroupedByDay() = runTest {
        val now = System.currentTimeMillis() / 1000L
        // Noon tomorrow, not now + 24 h: a DST day is 23 or 25 hours long.
        val tomorrowNoon = LocalDate.now().plusDays(1).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toEpochSecond()
        receiver.writeChunk(
            BOUQUET,
            now,
            listOf(event("News", start = now), event("News tomorrow", start = tomorrowNoon))
        )
        receiver.goOffline()

        val state = viewModel().uiState.first { it.sections.isNotEmpty() }

        assertEquals(
            listOf(UiText.Resource(R.string.today), UiText.Resource(R.string.tomorrow)),
            state.sections.map { it.day }
        )
        assertEquals(listOf("News", "News tomorrow"), state.titles())
    }

    @Test
    fun failureShowsTheContentError() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().uiState.first { it.emptyMessage != null && !it.refreshing }

        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun routeWithoutServiceDoesNotLoad() {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.reload()

        assertEquals("", viewModel.uiState.value.serviceRef)
        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun titleSaysLoadingWhileRefreshing() {
        assertEquals(
            UiText.Resource(R.string.loading),
            ServiceEpgUiState(serviceName = "Das Erste HD", refreshing = true).title
        )
    }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(
            mapOf("serviceRef" to CHANNEL, "serviceName" to "Das Erste HD")
        )
    ) = ServiceEpgViewModel(handle, receiver.repository, receiver.sessions)
        .also { viewModels += it }

    private fun ServiceEpgUiState.titles() = sections.flatMap { it.events }.map { it.title }
}
