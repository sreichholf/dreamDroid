package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.BOUQUET
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.event
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.withRealTimeout
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun routeQueryFillsTheFieldAndSearchesRightAway() = runTest {
        val viewModel = viewModel()

        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        val state = viewModel.awaitState { !it.searching && it.sections.isNotEmpty() }

        assertEquals("Tagesschau", viewModel.queryState.text.toString())
        assertEquals(listOf("Tagesschau", "N/A"), state.titles())
        assertFalse(state.showRecent)
        assertFalse(state.cached)
        assertEquals("Tagesschau", receiver.server.takeRequest().requestUrl?.searchQuery())
    }

    @Test
    fun emptyRouteListsRecentSearches() = runTest {
        val viewModel = viewModel()

        viewModel.syncRoute("", remountEpoch = 0)

        assertTrue(viewModel.uiState.value.showRecent)
        assertFalse(viewModel.uiState.value.searching)
    }

    @Test
    fun typingAsksTheReceiverOnceAfterThePause() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("", remountEpoch = 0)

        listOf("Tag", "Tage", "Tages", "Tagesschau").forEach {
            viewModel.type(it)
            advanceTimeBy(EpgSearchViewModel.RECEIVER_SETTLE_MS / 2)
        }
        advanceTimeBy(EpgSearchViewModel.RECEIVER_SETTLE_MS)

        val sent = receiver.awaitRequestsTo(SEARCH_PATH, 1)
        assertEquals("Tagesschau", sent.single().requestUrl?.searchQuery())
        val state = viewModel.awaitState { !it.searching && it.sections.isNotEmpty() }
        assertEquals(listOf("Tagesschau", "N/A"), state.titles())
        assertEquals(1, receiver.requestsTo(SEARCH_PATH).size)
    }

    @Test
    fun receiverSearchesRunOneAtATimeAndSkipToTheNewest() = runTest {
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            if (request.requestUrl?.searchQuery() == "Tagesschau") {
                release.await(5, TimeUnit.SECONDS)
            }
            MockResponse().setBody(loadWebFixture("epgservice.xml"))
        }
        val viewModel = viewModel()
        viewModel.syncRoute("", remountEpoch = 0)

        viewModel.type("Tagesschau")
        advanceTimeBy(EpgSearchViewModel.RECEIVER_SETTLE_MS + 1)
        receiver.awaitRequestsTo(SEARCH_PATH, 1)
        viewModel.type("Tatort")
        advanceTimeBy(EpgSearchViewModel.RECEIVER_SETTLE_MS + 1)
        viewModel.type("Wetter")
        advanceTimeBy(EpgSearchViewModel.RECEIVER_SETTLE_MS + 1)
        release.countDown()

        val sent = receiver.awaitRequestsTo(SEARCH_PATH, 2)
        assertEquals(listOf("Tagesschau", "Wetter"), sent.map { it.requestUrl?.searchQuery() })
        viewModel.awaitState { !it.searching && it.sections.isNotEmpty() }
        assertEquals(2, receiver.requestsTo(SEARCH_PATH).size)
    }

    @Test
    fun submittingTheAnsweredQueryOnlyRemembersIt() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("", remountEpoch = 0)
        viewModel.type("Tagesschau")
        advanceTimeBy(EpgSearchViewModel.RECEIVER_SETTLE_MS + 1)
        viewModel.awaitState { !it.searching && it.sections.isNotEmpty() }

        viewModel.submit()

        assertFalse(viewModel.uiState.value.searching)
        assertEquals(listOf("Tagesschau", "N/A"), viewModel.uiState.value.titles())
        // The remembered query is written after submit returned; the search would have
        // been requested before that write.
        viewModel.awaitState { it.recentSearches == listOf("Tagesschau") }
        assertEquals(1, receiver.requestsTo(SEARCH_PATH).size)
    }

    @Test
    fun submittingAFailedQuerySearchesAgain() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()
        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        val failed = viewModel.awaitState { !it.searching }
        assertTrue(failed.retryable)
        receiver.answer = { MockResponse().setBody(loadWebFixture("epgservice.xml")) }

        viewModel.submit()

        val state = viewModel.awaitState { !it.searching && it.sections.isNotEmpty() }
        assertFalse(state.retryable)
        assertEquals(2, receiver.requestsTo(SEARCH_PATH).size)
    }

    @Test
    fun typingKeepsThePreviousResultsUntilTheNewOnesArrive() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        viewModel.awaitState { !it.searching && it.sections.isNotEmpty() }

        viewModel.type("Tagesschau spät")

        val typing = viewModel.awaitState { it.searching }
        assertEquals(listOf("Tagesschau", "N/A"), typing.titles())
    }

    @Test
    fun shortTypedQueryNeverReachesTheReceiver() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("", remountEpoch = 0)

        viewModel.type("Ta")
        advanceTimeBy(EpgSearchViewModel.RECEIVER_SETTLE_MS + 1)
        assertTrue(viewModel.uiState.value.showRecent)
        viewModel.submit()

        // The submit is the first search: the typed letters alone never reached the receiver.
        val sent = receiver.awaitRequestsTo(SEARCH_PATH, 1)
        assertEquals("Ta", sent.first().requestUrl?.searchQuery())
    }

    @Test
    fun offlineResultsComeFromTheCacheGroupedByDay() = runTest {
        val now = System.currentTimeMillis() / 1000L
        receiver.writeChunk(
            BOUQUET,
            now,
            listOf(
                event("ÄRGER IM PARADIES", start = now),
                event("Ärger morgen", start = now + 86_400)
            )
        )
        receiver.goOffline()
        val viewModel = viewModel()
        viewModel.syncRoute("", remountEpoch = 0)

        viewModel.type("ärger")
        val state = viewModel.awaitState { !it.searching && it.sections.isNotEmpty() }

        assertEquals(listOf("ÄRGER IM PARADIES", "Ärger morgen"), state.titles())
        assertEquals(
            listOf(UiText.Resource(R.string.today), UiText.Resource(R.string.tomorrow)),
            state.sections.map { it.day }
        )
        assertTrue(state.cached)
        assertEquals(0, receiver.requestsTo(SEARCH_PATH).size)
    }

    @Test
    fun offlineCacheWithoutMatchSaysOnlyOpenedBouquetsAreSearched() = runTest {
        val now = System.currentTimeMillis() / 1000L
        receiver.writeChunk(BOUQUET, now, listOf(event("News", start = now)))
        receiver.goOffline()
        val viewModel = viewModel()

        viewModel.syncRoute("Tatort", remountEpoch = 0)
        val state = viewModel.awaitState { !it.searching }

        assertTrue(state.cached)
        assertEquals(UiText.Resource(R.string.epg_search_no_cached_match), state.emptyMessage)
    }

    @Test
    fun receiverResultsReplaceTheCachedOnes() = runTest {
        val now = System.currentTimeMillis() / 1000L
        receiver.writeChunk(BOUQUET, now, listOf(event("Tagesschau kompakt", start = now)))
        val viewModel = viewModel()

        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        val state = viewModel.awaitState { !it.searching && !it.cached }

        assertEquals(listOf("Tagesschau", "N/A"), state.titles())
    }

    @Test
    fun failureWithoutCacheShowsTheContentError() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()

        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        val state = viewModel.awaitState { !it.searching }

        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun failureWithCacheKeepsTheCachedResults() = runTest {
        val now = System.currentTimeMillis() / 1000L
        receiver.writeChunk(BOUQUET, now, listOf(event("News", start = now)))
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()

        viewModel.syncRoute("news", remountEpoch = 0)
        val state = viewModel.awaitState { !it.searching }

        assertEquals(listOf("News"), state.titles())
        assertTrue(state.cached)
    }

    @Test
    fun noResultsSaySo() = runTest {
        receiver.answer = { MockResponse().setBody("<e2eventlist></e2eventlist>") }
        val viewModel = viewModel()

        viewModel.syncRoute("nothing", remountEpoch = 0)
        val state = viewModel.awaitState { !it.searching }

        assertEquals(UiText.Resource(R.string.no_list_item), state.emptyMessage)
    }

    @Test
    fun submittedAndOpenedQueriesBecomeRecentSearches() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("", remountEpoch = 0)

        viewModel.type("Tatort")
        viewModel.submit()
        viewModel.awaitState { it.recentSearches == listOf("Tatort") }
        viewModel.searchRecent("Tagesschau")
        viewModel.awaitState { it.sections.isNotEmpty() && !it.searching }
        viewModel.onResultOpened()
        viewModel.awaitState { it.recentSearches == listOf("Tagesschau", "Tatort") }

        viewModel.forgetRecent("Tatort")

        viewModel.awaitState { it.recentSearches == listOf("Tagesschau") }
        assertEquals("Tagesschau", viewModel.queryState.text.toString())
    }

    @Test
    fun restoredDraftSurvivesTheFirstSync() {
        val handle = SavedStateHandle()
        viewModel(handle).type("Tagesschau ext")

        val restored = viewModel(handle)
        restored.syncRoute("Tagesschau", remountEpoch = 0)

        assertEquals("Tagesschau ext", restored.queryState.text.toString())
    }

    @Test
    fun sameRouteKeepsTheDraft() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        viewModel.type("Tagesschau ext")

        viewModel.syncRoute("Tagesschau", remountEpoch = 0)

        assertEquals("Tagesschau ext", viewModel.queryState.text.toString())
    }

    @Test
    fun remountResetsTheDraftAndSearchesAgain() = runTest {
        val viewModel = viewModel()
        viewModel.syncRoute("Tagesschau", remountEpoch = 0)
        receiver.awaitRequestsTo(SEARCH_PATH, 1)
        viewModel.type("Tagesschau ext")

        viewModel.syncRoute("Tagesschau", remountEpoch = 1)

        assertEquals("Tagesschau", viewModel.queryState.text.toString())
        val sent = receiver.awaitRequestsTo(SEARCH_PATH, 2)
        assertEquals("Tagesschau", sent[1].requestUrl?.searchQuery())
    }

    private suspend fun EpgSearchViewModel.awaitState(
        predicate: (EpgSearchUiState) -> Boolean
    ): EpgSearchUiState = withRealTimeout(5_000L) { uiState.first(predicate) }

    private fun EpgSearchUiState.titles() = sections.flatMap { it.events }.map { it.title }

    private fun HttpUrl.searchQuery() = queryParameter("search")

    /** Types like the user does: the edit reaches the field's observer. */
    private fun EpgSearchViewModel.type(text: String) {
        queryState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        EpgSearchViewModel(handle, receiver.repository, SettingsRepository(preferences)).also {
            viewModels += it
        }

    private companion object {
        const val SEARCH_PATH = "/web/epgsearch"
    }
}
