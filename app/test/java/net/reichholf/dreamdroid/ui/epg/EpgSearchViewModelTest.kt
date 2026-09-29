package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
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
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [EpgSearchViewModel] over the real [net.reichholf.dreamdroid.data.EpgRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgSearchViewModelTest {
    private val receiver = EpgTestReceiver()
    private val preferences = MemorySharedPreferences()
    private val viewModels = mutableListOf<EpgSearchViewModel>()

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
    fun routeQueryFillsTheFieldAndSearches() = runTest {
        val viewModel = viewModel()

        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        val state = viewModel.uiState.first { it.events.isNotEmpty() }

        assertEquals("Tagesschau", viewModel.queryState.text.toString())
        assertEquals(listOf("Tagesschau", "N/A"), state.events.map { it.title })
        assertEquals(UiText.Resource(R.string.epg_search), state.title)
        assertEquals(
            "Tagesschau",
            receiver.server.takeRequest().requestUrl?.queryParameter("search")
        )
    }

    @Test
    fun restoredDraftSurvivesTheFirstSync() {
        val handle = SavedStateHandle()
        viewModel(handle).type("Tagesschau ext")

        val restored = viewModel(handle)
        restored.syncRoute("Tagesschau", remountEpoch = 0)

        assertEquals("Tagesschau ext", restored.queryState.text.toString())
        assertEquals("Tagesschau", restored.uiState.value.query)
    }

    @Test
    fun sameRouteKeepsTheDraftAndDoesNotSearchAgain() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        viewModel.uiState.first { !it.refreshing }
        viewModel.type("Tagesschau ext")

        viewModel.syncRoute("Tagesschau", remountEpoch = 0)

        assertEquals("Tagesschau ext", viewModel.queryState.text.toString())
        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun remountResetsTheDraftAndSearchesAgain() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        viewModel.uiState.first { !it.refreshing }
        viewModel.type("Tagesschau ext")

        viewModel.syncRoute("Tagesschau", remountEpoch = 1)
        viewModel.uiState.first { !it.refreshing }

        assertEquals("Tagesschau", viewModel.queryState.text.toString())
        assertEquals(2, receiver.server.requestCount)
    }

    @Test
    fun emptyQueryDoesNotSearch() {
        val viewModel = viewModel()

        viewModel.syncRoute("", remountEpoch = 0)

        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun failureShowsTheContentError() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()

        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        val state = viewModel.uiState.first { !it.refreshing }

        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun noResultsSaySo() = runTest {
        receiver.answer = { MockResponse().setBody("<e2eventlist></e2eventlist>") }
        val viewModel = viewModel()

        viewModel.syncRoute("nothing", remountEpoch = 0)
        val state = viewModel.uiState.first { !it.refreshing }

        assertEquals(UiText.Resource(R.string.no_list_item), state.emptyMessage)
    }

    /** Types like the user does: the edit reaches the field's observer. */
    private fun EpgSearchViewModel.type(text: String) {
        queryState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        EpgSearchViewModel(handle, receiver.repository, SettingsRepository(preferences)).also {
            viewModels +=
                it
        }
}
