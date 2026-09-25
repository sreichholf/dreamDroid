package net.reichholf.dreamdroid.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class TvHubViewModelTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites")
    private val movieHeader = TvComposeHubHost.movieHeaderId("/hdd/movie/")

    private class FakeLoader(var locations: List<String>) : TvHubLoader {
        var browseCalls = 0
        var browseError: String? = null
        val movieCalls = mutableListOf<String>()

        override suspend fun browse(): TvHubBrowseResult {
            browseCalls++
            browseError?.let { error ->
                return TvHubBrowseResult(emptyList(), locations, error, usedCache = false)
            }
            return TvHubBrowseResult(
                rows = listOf(
                    HubBouquetRow(
                        Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites"),
                        emptyList()
                    )
                ),
                locations = locations,
                errorText = null,
                usedCache = false
            )
        }

        override suspend fun movies(dirname: String): TvHubMoviesResult {
            movieCalls += dirname
            return TvHubMoviesResult(
                movies = listOf(Movie(title = dirname)),
                errorText = null,
                usedCache = false
            )
        }
    }

    private fun factory(
        loader: TvHubLoader,
        sessions: MutableStateFlow<ConnectionStatus.Session?>
    ) = viewModelFactory { initializer { TvHubViewModel(loader, sessions) } }

    @Test
    fun hubLeavingCompositionKeepsSelectionAndLoadedMovies() {
        val loader = FakeLoader(listOf("/hdd/movie/"))
        val sessions = MutableStateFlow<ConnectionStatus.Session?>(ConnectionStatus.Session.Online)
        val hubFactory = factory(loader, sessions)
        var shown by mutableStateOf(true)
        val seen = mutableListOf<TvHubViewModel>()
        composeRule.setContent {
            if (shown) {
                seen += viewModel<TvHubViewModel>(factory = hubFactory)
            }
        }
        composeRule.runOnIdle { seen.last().selectHeader(movieHeader) }
        composeRule.runOnIdle { shown = false }
        composeRule.runOnIdle { shown = true }
        composeRule.runOnIdle {
            val vm = seen.last()
            assertSame(seen.first(), vm)
            assertEquals(movieHeader, vm.selectedHeaderId)
            assertEquals(1, loader.browseCalls)
            assertEquals(listOf("/hdd/movie/"), loader.movieCalls)
            assertEquals(setOf("/hdd/movie/"), vm.moviesByLocation.keys)
        }
    }

    @Test
    fun serviceTimerOverlaySurvivesLeavingComposition() {
        val loader = FakeLoader(listOf("/hdd/movie/"))
        val sessions = MutableStateFlow<ConnectionStatus.Session?>(ConnectionStatus.Session.Online)
        val hubFactory = factory(loader, sessions)
        var shown by mutableStateOf(true)
        val seen = mutableListOf<TvHubViewModel>()
        val service = ServiceNowNext(
            serviceReference = "1:0:1:0:0:0:0:0:0:0:Das Erste",
            serviceName = "Das Erste"
        )
        val event = Event(eventId = "42", title = "News")
        composeRule.setContent {
            if (shown) {
                seen += viewModel<TvHubViewModel>(factory = hubFactory)
            }
        }
        composeRule.runOnIdle {
            val vm = seen.last()
            assertEquals(null, vm.serviceTimerTarget)
            assertEquals(null, vm.editTimerEvent)
            vm.showServiceTimer(service, bouquet.reference)
            vm.showEditTimer(event)
        }
        composeRule.runOnIdle { shown = false }
        composeRule.runOnIdle { shown = true }
        composeRule.runOnIdle {
            val vm = seen.last()
            assertSame(seen.first(), vm)
            assertEquals(service to bouquet.reference, vm.serviceTimerTarget)
            assertEquals(event, vm.editTimerEvent)
            vm.dismissEditTimer()
            assertEquals(null, vm.editTimerEvent)
            assertEquals(service to bouquet.reference, vm.serviceTimerTarget)
            vm.dismissServiceTimer()
            assertEquals(null, vm.serviceTimerTarget)
        }
    }

    @Test
    fun headerSwitchBackDoesNotReloadMovies() {
        val loader = FakeLoader(listOf("/hdd/movie/"))
        val sessions = MutableStateFlow<ConnectionStatus.Session?>(ConnectionStatus.Session.Online)
        lateinit var vm: TvHubViewModel
        composeRule.setContent {
            vm = viewModel(factory = factory(loader, sessions))
        }
        composeRule.runOnIdle { vm.selectHeader(movieHeader) }
        composeRule.runOnIdle { vm.selectHeader(bouquet.reference) }
        composeRule.runOnIdle { vm.selectHeader(movieHeader) }
        composeRule.runOnIdle {
            assertEquals(listOf("/hdd/movie/"), loader.movieCalls)
        }
    }

    @Test
    fun onlyASessionChangeReloadsAndDropsAVanishedHeader() {
        val loader = FakeLoader(listOf("/hdd/movie/"))
        val sessions = MutableStateFlow<ConnectionStatus.Session?>(ConnectionStatus.Session.Online)
        lateinit var vm: TvHubViewModel
        composeRule.setContent {
            vm = viewModel(factory = factory(loader, sessions))
        }
        composeRule.runOnIdle { vm.selectHeader(movieHeader) }
        composeRule.runOnIdle { sessions.value = ConnectionStatus.Session.Online }
        composeRule.runOnIdle {
            assertEquals(1, loader.browseCalls)
            loader.locations = emptyList()
            sessions.value = ConnectionStatus.Session.Offline
        }
        composeRule.runOnIdle {
            assertEquals(2, loader.browseCalls)
            assertEquals(TvComposeHubHost.HEADER_SETTINGS_ID, vm.selectedHeaderId)
        }
    }

    @Test
    fun reloadAfterFailedBrowseFetchesAgain() {
        val loader = FakeLoader(emptyList())
        loader.browseError = "no route to host"
        val sessions = MutableStateFlow<ConnectionStatus.Session?>(ConnectionStatus.Session.Online)
        lateinit var vm: TvHubViewModel
        composeRule.setContent {
            vm = viewModel(factory = factory(loader, sessions))
        }
        composeRule.runOnIdle {
            assertEquals("no route to host", vm.errorText)
            loader.browseError = null
            vm.reload()
        }
        composeRule.runOnIdle {
            assertEquals(2, loader.browseCalls)
            assertEquals(null, vm.errorText)
            assertEquals(listOf(bouquet.reference), vm.bouquetRows.map { it.bouquet.reference })
        }
    }
}
