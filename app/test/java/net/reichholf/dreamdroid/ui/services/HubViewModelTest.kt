package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CopyOnWriteArrayList
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
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
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

/** [HubViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val clients = enigmaClients(profiles)
    private val movies = MovieRepository(
        receiver.profiles.context,
        clients,
        profiles,
        receiver.profiles.database
    )
    private val timers = TimerRepository(clients, profiles, receiver.profiles.database)
    private val viewModels = mutableListOf<HubViewModel>()
    private val main = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(main)
        receiver.answer = ::routes
        receiver.start()
        profiles.setDeviceInfo(profiles.requireCurrent(), "<e2deviceinfo/>")
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheBouquetsAndSelectsTheProfileDefault() = runBlocking<Unit> {
        profiles.requireCurrent().setDefaultRefValues(SPORTS.reference, SPORTS.name)
        val handle = SavedStateHandle()

        val state = viewModel(handle).loaded()

        assertEquals(listOf(FAVOURITES, SPORTS), state.tvBouquets)
        assertEquals(listOf(RADIO), state.radioBouquets)
        assertNull(state.bouquetError)
        assertEquals(1, state.selectedRow)
        // The load publishes the state before it saves it, on another thread.
        val currentTv = withTimeout(5_000L) {
            handle.getStateFlow<String?>(HubShellSavedKeys.CURRENT_TV, null)
                .first { it != null }
        }
        assertEquals(SPORTS.reference, currentTv)
        assertEquals(listOf(FAVOURITES, SPORTS), receiver.services.cachedBouquets().tv)
    }

    @Test
    fun withoutAStripTheHubWaitsForTheProfileCheck() = runBlocking<Unit> {
        profiles.setDeviceInfo(profiles.requireCurrent(), null)
        val beforeTheCheck = CopyOnWriteArrayList<String?>()
        receiver.answer = { request ->
            if (profiles.deviceInfo(profiles.requireCurrent()) == null) {
                beforeTheCheck += request.requestUrl?.encodedPath
            }
            routes(request)
        }
        val viewModel = viewModel()

        main.scheduler.advanceTimeBy(10_000L)
        profiles.setDeviceInfo(profiles.requireCurrent(), "<e2deviceinfo/>")
        main.scheduler.advanceTimeBy(200L)

        assertEquals(listOf(FAVOURITES, SPORTS), viewModel.loaded().tvBouquets)
        assertTrue(beforeTheCheck.isEmpty(), "sent before the check: $beforeTheCheck")
    }

    @Test
    fun offlineSessionPaintsTheRoomStripWithoutTheReceiver() = runBlocking<Unit> {
        receiver.writeTabStrip(SPORTS)
        receiver.goOffline()

        val state = viewModel().loaded()

        assertEquals(listOf(SPORTS), state.tvBouquets)
        assertTrue(receiver.requests.isEmpty())
    }

    @Test
    fun failureWithoutAStripShowsTheError() = runBlocking<Unit> {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().loaded()

        assertTrue(state.tvBouquets.isEmpty())
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.bouquetError
        )
    }

    @Test
    fun sessionChangeLoadsTheBouquetsAgain() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.loaded()
        val before = receiver.requestsTo(GET_SERVICES).size

        receiver.sessions.resetForProfileChange()
        receiver.sessions.onSuccess()

        receiver.awaitRequestsTo(GET_SERVICES, before + 2)
    }

    @Test
    fun modeAndRowRestoreFromTheSavedState() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        HubShellSaved(
            mode = HubModes.RADIO,
            currentRadio = RADIO.reference,
            selectedRow = 0,
            timerRemountEpoch = 3,
            nowPlayingReloadEpoch = 4
        ).writeTo(handle)

        val state = viewModel(handle).loaded()

        assertEquals(HubModes.RADIO, state.mode)
        assertEquals(0, state.selectedRow)
        assertEquals(3, state.timerRemountEpoch)
        assertEquals(4, state.nowPlayingReloadEpoch)
    }

    @Test
    fun rowSelectionIsRememberedPerMode() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.loaded()

        viewModel.onRowSelected(1)
        viewModel.selectRadio()
        assertEquals(HubModes.RADIO, viewModel.uiState.value.mode)
        viewModel.onRowSelected(2)
        viewModel.selectTv()

        assertEquals(1, viewModel.uiState.value.selectedRow)
        val saved = readHubShellSaved(handle)
        assertEquals(SPORTS.reference, saved.currentTv)
        assertEquals(RADIO_ROOTS[2], saved.currentRadio)
        assertEquals(HubModes.TV, saved.mode)
    }

    @Test
    fun dedicatedRootsFollowTheLoadedBouquets() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.loaded()

        viewModel.onRowSelected(3)
        viewModel.selectTimer()
        assertEquals(0, viewModel.uiState.value.selectedRow)
        viewModel.selectTv()

        assertEquals(3, viewModel.uiState.value.selectedRow)
        assertEquals(TV_ROOTS[2], readHubShellSaved(handle).currentTv)
    }

    @Test
    fun moviesSayLoadingUntilTheLocationsAreKnown() = runBlocking<Unit> {
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == LOCATIONS) {
                release.await(5, TimeUnit.SECONDS)
            }
            routes(request)
        }
        val handle = SavedStateHandle()
        HubShellSaved(currentMovie = USB).writeTo(handle)
        val viewModel = viewModel(handle)
        viewModel.ensureLocations()

        viewModel.selectMovies()
        assertEquals(UiText.Resource(R.string.loading), viewModel.uiState.value.userMessage)
        assertEquals(0, viewModel.uiState.value.selectedRow)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
        release.countDown()
        val state = withTimeout(5_000L) { viewModel.uiState.first { it.locationsReady } }

        assertEquals(listOf(HDD, USB), state.movieLocations)
        assertEquals(1, state.selectedRow)
        assertEquals(listOf(HDD, USB), movies.cachedLocations())
    }

    @Test
    fun failedLocationsPaintTheSnapshotAndAreNotAskedAgain() = runBlocking<Unit> {
        movies.saveLocations(listOf(USB))
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                LOCATIONS, TAGS -> MockResponse().setResponseCode(500)
                else -> routes(request)
            }
        }
        val viewModel = viewModel()

        viewModel.ensureLocations()
        val state = withTimeout(5_000L) { viewModel.uiState.first { it.locationsReady } }
        viewModel.ensureLocations()

        assertEquals(listOf(USB), state.movieLocations)
        assertEquals(1, receiver.requestsTo(LOCATIONS).size)
    }

    @Test
    fun knownLocationsAreReadyAtOnce() = runBlocking<Unit> {
        receiver.answer = ::routes
        timers.locationsAndTags()

        val state = viewModel().uiState.value

        assertTrue(state.locationsReady)
        assertEquals(listOf(HDD, USB), state.movieLocations)
    }

    @Test
    fun selectedRowIsClampedToTheRows() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onRowSelected(4)

        viewModel.clampSelectedRow(2)
        assertEquals(1, viewModel.uiState.value.selectedRow)
        viewModel.clampSelectedRow(0)

        assertEquals(0, viewModel.uiState.value.selectedRow)
    }

    @Test
    fun epochsGrowAndAreSaved() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)

        viewModel.bumpTimerRemount()
        viewModel.bumpNowPlayingReload()
        viewModel.bumpNowPlayingReload()

        val saved = readHubShellSaved(handle)
        assertEquals(1, saved.timerRemountEpoch)
        assertEquals(2, saved.nowPlayingReloadEpoch)
        assertFalse(viewModel.uiState.value.locationsReady)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = HubViewModel(
        handle,
        receiver.services,
        movies,
        timers,
        ReceiverRepository(clients, profiles),
        profiles,
        receiver.sessions
    ).also { viewModels += it }

    private suspend fun HubViewModel.loaded(): HubUiState =
        withTimeout(5_000L) { uiState.first { it.bouquetsLoaded } }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            GET_SERVICES -> when (request.requestUrl?.queryParameter("sRef")) {
                TV_ROOTS[0] -> serviceList(FAVOURITES, SPORTS)
                RADIO_ROOTS[0] -> serviceList(RADIO)
                else -> MockResponse().setResponseCode(404)
            }

            LOCATIONS -> MockResponse().setBody(
                "<e2locations><e2location>$HDD</e2location>" +
                    "<e2location>$USB</e2location></e2locations>"
            )

            TAGS -> MockResponse().setBody("<e2tags><e2tag>News</e2tag></e2tags>")

            else -> MockResponse().setResponseCode(404)
        }

    private fun serviceList(vararg services: Service): MockResponse = MockResponse().setBody(
        services.joinToString("", "<e2servicelist>", "</e2servicelist>") {
            "<e2service><e2servicereference>${it.reference}</e2servicereference>" +
                "<e2servicename>${it.name}</e2servicename></e2service>"
        }
    )

    private companion object {
        const val GET_SERVICES = "/web/getservices"
        const val LOCATIONS = "/web/getlocations"
        const val TAGS = "/web/gettags"
        const val HDD = "/media/hdd/movie"
        const val USB = "/media/usb/movie"
        val FAVOURITES = Service(EpgTestReceiver.BOUQUET, "Favourites (TV)")
        val SPORTS = Service(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet",
            "Sports"
        )
        val RADIO = Service(
            "1:7:2:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.radio\" ORDER BY bouquet",
            "Favourites (Radio)"
        )
    }
}
