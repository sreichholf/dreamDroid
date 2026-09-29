package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.MovieTestReceiver
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.HDD
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.movieList
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.jobs
import net.reichholf.dreamdroid.testutil.joinJobsSince
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [HubMovieListViewModel] over the real repositories and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class HubMovieListViewModelTest {
    private val receiver = MovieTestReceiver()
    private val preferences = MemorySharedPreferences()
    private val viewModels = mutableListOf<HubMovieListViewModel>()

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
    fun loadsLocationWhenCreated() = runTest {
        val state = viewModel().settled()

        assertEquals(listOf("Evening News", "Empty Size"), state.items.map { it.title })
        assertNull(state.emptyMessage)
        assertEquals(UiText.Raw(HDD), state.title)
        assertEquals(HDD, receiver.server.takeRequest().requestUrl!!.queryParameter("dirname"))
    }

    @Test
    fun titleSaysLoadingWhileRefreshingAndMoviesForDefaultLocation() = runTest {
        val release = CountDownLatch(1)
        receiver.answer = {
            release.await(5, TimeUnit.SECONDS)
            movieList("News")
        }
        val viewModel = viewModel(location = "")

        val loading = viewModel.uiState.value
        assertTrue(loading.refreshing)
        assertEquals(UiText.Resource(R.string.loading), loading.title)
        assertEquals(UiText.Resource(R.string.loading), loading.emptyMessage)
        release.countDown()
        assertEquals(UiText.Resource(R.string.movies), viewModel.settled().title)
    }

    @Test
    fun emptyLocationSaysNoItems() = runTest {
        receiver.answer = { movieList() }

        assertEquals(
            UiText.Resource(R.string.no_list_item),
            viewModel().settled().emptyMessage
        )
    }

    @Test
    fun failureWithoutSnapshotShowsError() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().settled()

        assertTrue(state.items.isEmpty())
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun failurePaintsSnapshot() = runTest {
        receiver.movies.saveMovies(HDD, listOf(Movie(title = "Cached")))
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().settled()

        assertEquals(listOf("Cached"), state.items.map { it.title })
        assertNull(state.emptyMessage)
    }

    @Test
    fun failureOnWrittenEmptySnapshotSaysNoItems() = runTest {
        receiver.movies.saveMovies(HDD, emptyList())
        receiver.answer = { MockResponse().setResponseCode(500) }

        assertEquals(
            UiText.Resource(R.string.no_list_item),
            viewModel().settled().emptyMessage
        )
    }

    @Test
    fun failedReloadClearsTheList() = runTest {
        val viewModel = viewModel()
        viewModel.settled()
        receiver.answer = { MockResponse().setResponseCode(500) }
        receiver.profiles.database.movieDao().deleteAllForProfile(PROFILE_ID)

        viewModel.reload()

        assertTrue(viewModel.settled().items.isEmpty())
    }

    @Test
    fun staleLoadDoesNotReplaceNewerList() = runTest {
        val staleSent = CountDownLatch(1)
        val releaseStale = CountDownLatch(1)
        receiver.answer = { request ->
            if (request.requestUrl?.queryParameter("tag") == null) {
                staleSent.countDown()
                releaseStale.await(5, TimeUnit.SECONDS)
                movieList("Stale")
            } else {
                movieList("Fresh")
            }
        }
        receiver.profiles.repository.tags().apply {
            clear()
            add("news")
        }
        val viewModel = viewModel()
        assertTrue(staleSent.await(5, TimeUnit.SECONDS))

        viewModel.onPickTags()
        viewModel.onTagsPicked(listOf(0))
        assertEquals(listOf("Fresh"), viewModel.settled().items.map { it.title })
        releaseStale.countDown()
        // The pick cancelled the stale load; it ends once its held answer arrives.
        viewModel.jobs().filter { it.isCancelled }.joinAll()

        assertEquals(listOf("Fresh"), viewModel.uiState.value.items.map { it.title })
    }

    @Test
    fun pickedTagsFilterAndSurviveRecreation() = runTest {
        receiver.profiles.repository.tags().apply {
            clear()
            addAll(listOf("news", "sports", "kids"))
        }
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle = handle)
        viewModel.settled()

        viewModel.onPickTags()
        assertEquals(listOf("news", "sports", "kids"), viewModel.uiState.value.tagPicker)
        viewModel.onTagsPicked(listOf(0, 2))
        viewModel.settled()

        assertNull(viewModel.uiState.value.tagPicker)
        assertEquals(listOf("news", "kids"), viewModel.uiState.value.selectedTags)
        val tagged = receiver.takeRequests(2).last()
        assertEquals("news kids", tagged.requestUrl!!.queryParameter("tag"))

        val restored = viewModel(handle = handle)
        restored.settled()
        assertEquals(listOf("news", "kids"), restored.uiState.value.selectedTags)
        val restoredRequest = receiver.takeRequests(1).single()
        assertEquals("news kids", restoredRequest.requestUrl!!.queryParameter("tag"))
    }

    @Test
    fun unchangedTagsDoNotReload() = runTest {
        receiver.profiles.repository.tags().apply {
            clear()
            add("news")
        }
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.onPickTags()
        viewModel.onTagsPicked(emptyList())

        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun rowMenuTargetsTheTappedRow() = runTest {
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.onItemMenu(1)
        assertEquals(RowMenuState(1, MovieRowAction.entries), viewModel.uiState.value.menu)

        viewModel.onMenuDismiss()
        assertNull(viewModel.uiState.value.menu)
    }

    @Test
    fun infoShowsDetailOrSaysThereIsNone() = runTest {
        val viewModel = viewModel()
        val movies = viewModel.settled()

        viewModel.onItemMenu(0)
        viewModel.onMenuAction(MovieRowAction.Info)
        assertEquals("Evening News", viewModel.uiState.value.detail?.title)
        viewModel.onDetailDismissed()
        assertNull(viewModel.uiState.value.detail)

        viewModel.onItemMenu(1)
        viewModel.onMenuAction(MovieRowAction.Info)
        assertNull(viewModel.uiState.value.detail)
        assertEquals(
            UiText.Resource(R.string.no_epg_available),
            viewModel.uiState.value.userMessage
        )
        assertEquals("Empty Size", movies.items[1].title)
    }

    @Test
    fun zapSendsTheRecordingAndShowsTheReply() = runTest {
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                "/web/zap" -> simpleResult("True", "Zapped")
                else -> movieList("News")
            }
        }
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.zap(0)
        val message = viewModel.uiState.first { it.userMessage != null }.userMessage

        assertEquals(UiText.Raw("Zapped"), message)
        val zap = receiver.takeRequests(2).last()
        assertEquals(ZAP, zap.requestUrl!!.encodedPath)
        assertEquals(
            "1:0:0:0:0:0:0:0:0:0:/media/hdd/movie/News.ts",
            zap.requestUrl!!.queryParameter("sRef")
        )
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun confirmedDeleteReloadsOnSuccess() = runTest {
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                "/web/moviedelete" -> simpleResult("True", "Deleted")
                else -> movieList("News")
            }
        }
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(0)

        viewModel.onMenuAction(MovieRowAction.Delete)
        assertEquals("News", viewModel.uiState.value.deleteConfirm)
        viewModel.onDeleteConfirmed()

        val paths = receiver.takeRequests(3).map { it.requestUrl!!.encodedPath }
        assertEquals(listOf(MOVIES, DELETE, MOVIES), paths)
        val state = viewModel.uiState.first { it.userMessage != null && !it.refreshing }
        assertNull(state.deleteConfirm)
        assertNull(state.progress)
        assertEquals(UiText.Raw("Deleted"), state.userMessage)
    }

    @Test
    fun rejectedDeleteDoesNotReload() = runTest {
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                "/web/moviedelete" -> simpleResult("False", "Locked")
                else -> movieList("News")
            }
        }
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(0)
        viewModel.onMenuAction(MovieRowAction.Delete)
        val before = viewModel.jobs()

        viewModel.onDeleteConfirmed()

        assertEquals(
            UiText.Raw("Locked"),
            viewModel.uiState.first { it.userMessage != null }.userMessage
        )
        viewModel.joinJobsSince(before)
        val paths = receiver.takeRequests(2).map { it.requestUrl!!.encodedPath }
        assertEquals(listOf(MOVIES, DELETE), paths)
        assertEquals(2, receiver.server.requestCount)
    }

    @Test
    fun blockedSessionIgnoresOnlineOnlyActions() = runTest {
        val viewModel = viewModel()
        viewModel.settled()
        receiver.goOffline()
        assertTrue(viewModel.uiState.value.blocked)

        viewModel.onItemMenu(0)
        viewModel.zap(0)
        viewModel.onMenuAction(MovieRowAction.Zap)
        viewModel.onMenuAction(MovieRowAction.Stream)
        viewModel.onMenuAction(MovieRowAction.Download)
        viewModel.onMenuAction(MovieRowAction.Delete)
        viewModel.onDeleteConfirmed()

        assertNull(viewModel.uiState.value.open)
        assertNull(viewModel.uiState.value.progress)
        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun streamHandsTheMovieOver() = runTest {
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(0)

        viewModel.onMenuAction(MovieRowAction.Stream)
        val open = viewModel.uiState.value.open as MovieOpen.Stream
        assertEquals("Evening News", open.movie.title)
        val profile = receiver.profiles.repository.requireCurrent()
        assertEquals(
            EnigmaUrls.fileStream(profile, open.movie.reference, open.movie.fileName),
            open.url
        )
        viewModel.onOpened()
        assertNull(viewModel.uiState.value.open)

        viewModel.onMenuAction(MovieRowAction.Stream)
        viewModel.onOpenFailed()
        assertNull(viewModel.uiState.value.open)
        assertEquals(
            UiText.Resource(R.string.missing_stream_player),
            viewModel.uiState.value.userMessage
        )
    }

    @Test
    fun downloadWithoutLoginOpensTheLink() = runTest {
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(0)

        viewModel.onMenuAction(MovieRowAction.Download)

        val open = viewModel.uiState.value.open as MovieOpen.Link
        assertTrue(open.url.contains("/file?file="))
        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun downloadWithLoginCopiesIntoTheCache() = runTest {
        receiver.useProfile(PROFILE_ID, login = true)
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                "/file" -> MockResponse().setBody("recording")
                else -> MockResponse().setBody(loadMovies())
            }
        }
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(0)

        viewModel.onMenuAction(MovieRowAction.Download)
        val state = viewModel.uiState.first { it.open != null }

        assertNull(state.progress)
        val file = (state.open as MovieOpen.CachedFile).file
        assertEquals("news.ts", file.name)
        assertEquals("recording", file.readText())
    }

    @Test
    fun failedDownloadSaysSo() = runTest {
        receiver.useProfile(PROFILE_ID, login = true)
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                "/file" -> MockResponse().setResponseCode(500)
                else -> MockResponse().setBody(loadMovies())
            }
        }
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(0)

        viewModel.onMenuAction(MovieRowAction.Download)
        val state = viewModel.uiState.first { it.userMessage != null }

        assertNull(state.open)
        assertEquals(
            EnigmaFailure.fromHttpStatus(500, "Server Error").userMessageText(),
            state.userMessage
        )
    }

    @Test
    fun detailMatchesTheMovie() = runTest {
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.onItemMenu(0)

        viewModel.onMenuAction(MovieRowAction.Info)

        val expected = receiver.movies.cachedMovies(HDD)!!.first().toMovieDetailContent()
        assertEquals(expected, viewModel.uiState.value.detail)
    }

    @Test
    fun instantZapSwapsTapAndLongPress() {
        val viewModel = viewModel()
        assertFalse(viewModel.zapsOnTap(isLong = false))
        assertTrue(viewModel.zapsOnTap(isLong = true))

        preferences.edit().putBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, true).commit()

        assertTrue(viewModel.zapsOnTap(isLong = false))
        assertFalse(viewModel.zapsOnTap(isLong = true))
    }

    private fun viewModel(
        location: String = HDD,
        handle: SavedStateHandle = SavedStateHandle()
    ): HubMovieListViewModel = HubMovieListViewModel(
        location,
        handle,
        receiver.movies,
        receiver.receiver,
        receiver.profiles.repository,
        receiver.sessions,
        SettingsRepository(preferences)
    ).also { viewModels += it }

    private suspend fun HubMovieListViewModel.settled(): HubMovieListUiState =
        uiState.first { !it.refreshing }

    private fun loadMovies(): String = loadWebFixture("movielist.xml")

    private companion object {
        const val MOVIES = "/web/movielist"
        const val DELETE = "/web/moviedelete"
        const val ZAP = "/web/zap"
    }
}
