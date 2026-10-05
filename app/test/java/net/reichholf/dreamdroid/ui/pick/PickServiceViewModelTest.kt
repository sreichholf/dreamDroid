package net.reichholf.dreamdroid.ui.pick

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
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.room.BouquetTabEntity
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.jobs
import net.reichholf.dreamdroid.testutil.joinJobsSince
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [PickServiceViewModel] over the real [net.reichholf.dreamdroid.data.ServiceRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
class PickServiceViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<PickServiceViewModel>()

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
    fun listsTvThenRadioBouquets() = runBlocking {
        val state = viewModel().settled()

        assertEquals(listOf("Favourites (TV)", "Favourites (Radio)"), state.items.map { it.name })
        assertNull(state.emptyMessage)
        assertEquals(UiText.Resource(R.string.services), state.title)
    }

    @Test
    fun titleAndMessageSayLoadingWhileLoading() = runBlocking<Unit> {
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
        viewModel.settled()
    }

    @Test
    fun failedReceiverShowsTheTabStrips() = runBlocking {
        receiver.profiles.database.rosterDao().replaceTabStrip(
            PROFILE_ID,
            "TV",
            listOf(BouquetTabEntity(PROFILE_ID, "TV", 0, TV_BOUQUET, "Cached TV"))
        )
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()
        viewModel.settled()
        val before = viewModel.jobs()

        viewModel.reload()
        viewModel.joinJobsSince(before)

        val state = viewModel.uiState.value
        assertEquals(listOf("Cached TV"), state.items.map { it.name })
        assertNull(state.emptyMessage)
        assertEquals(2, receiver.requests.size)
    }

    @Test
    fun failedReceiverWithoutTabStripShowsTheError() = runBlocking {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().settled()

        assertTrue(state.items.isEmpty())
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun emptyIndexesSayNoItems() = runBlocking {
        receiver.answer = { MockResponse().setBody("<e2servicelist></e2servicelist>") }

        val state = viewModel().settled()

        assertEquals(UiText.Resource(R.string.no_list_item), state.emptyMessage)
    }

    @Test
    fun reloadAsksAgain() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()
        val before = viewModel.jobs()

        viewModel.reload()
        viewModel.joinJobsSince(before)

        assertEquals(4, receiver.requests.size)
    }

    @Test
    fun offlineReloadPaintsTheTabStripsWithoutTheReceiver() = runBlocking {
        val viewModel = viewModel()
        viewModel.settled()
        receiver.goOffline()
        val before = viewModel.jobs()

        viewModel.reload()
        viewModel.joinJobsSince(before)

        assertEquals(
            listOf("Favourites (TV)", "Favourites (Radio)"),
            viewModel.uiState.value.items.map { it.name }
        )
        assertEquals(2, receiver.requests.size)
        viewModel.reload(forceRefresh = true)
        viewModel.joinJobsSince(before)
        assertEquals(4, receiver.requests.size)
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.queryParameter("sRef")) {
            TV_ROOTS[0] -> MockResponse().setBody(list(TV_BOUQUET, "Favourites (TV)"))
            RADIO_ROOTS[0] -> MockResponse().setBody(list(RADIO_BOUQUET, "Favourites (Radio)"))
            else -> MockResponse().setResponseCode(404)
        }

    private fun viewModel(): PickServiceViewModel =
        PickServiceViewModel(receiver.services).also { viewModels += it }

    private suspend fun PickServiceViewModel.settled(): PickServiceUiState =
        withTimeout(5_000L) { uiState.first { !it.refreshing } }

    private fun list(ref: String, name: String): String =
        "<e2servicelist><e2service><e2servicereference>${ref.replace("\"", "&quot;")}" +
            "</e2servicereference><e2servicename>$name</e2servicename></e2service>" +
            "</e2servicelist>"

    private companion object {
        const val TV_BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val RADIO_BOUQUET =
            "1:7:2:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.radio\" ORDER BY bouquet"
    }
}
