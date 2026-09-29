package net.reichholf.dreamdroid.ui.profiles

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ProfilesViewModel] over the real [net.reichholf.dreamdroid.data.ProfileRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val viewModels = mutableListOf<ViewModel>()
    private var found: suspend () -> List<Profile> = { emptyList() }
    private var searches = 0

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test
    fun listsProfilesAndMarksTheActiveOne() = runTest {
        val living = saved("Living Room", "10.0.0.1")
        saved("Bedroom", "10.0.0.2")
        profiles.setCurrent(living.id!!)
        val viewModel = viewModel()

        viewModel.refresh()
        val rows = viewModel.uiState.first { it.profiles.size == 2 }.profiles

        assertEquals(listOf("Living Room", "Bedroom"), rows.map { it.name })
        assertEquals(listOf(true, false), rows.map { it.active })
        assertEquals(UiText.Resource(R.string.profiles), viewModel.uiState.value.title)
    }

    @Test
    fun activatingMakesTheProfileCurrentAndSaysSo() = runTest {
        saved("Living Room", "10.0.0.1")
        val bedroom = saved("Bedroom", "10.0.0.2")
        val viewModel = viewModel()
        viewModel.refresh()
        val row = viewModel.uiState.first { it.profiles.size == 2 }.profiles[1]

        viewModel.activate(row)
        val state = viewModel.uiState.first { it.profiles.getOrNull(1)?.active == true }

        assertEquals(bedroom.id, profiles.requireCurrent().id)
        assertEquals(namedMessage(R.string.profile_activated, "Bedroom"), state.userMessage)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun emptyDiscoveryFailsAndSearchesAgainNextTime() = runTest {
        val viewModel = viewModel()

        viewModel.detectDevices()
        val state = viewModel.uiState.first { !it.detecting }

        assertTrue(state.discoveryFailed)
        assertNull(state.discovered)
        viewModel.dismissDiscovery()
        viewModel.detectDevices()
        viewModel.uiState.first { !it.detecting }
        assertEquals(2, searches)
    }

    @Test
    fun discoveryShowsWhileSearchingAndReusesItsResult() = runTest {
        val gate = CompletableDeferred<List<Profile>>()
        found = { gate.await() }
        val viewModel = viewModel()

        viewModel.detectDevices()
        assertTrue(viewModel.uiState.value.detecting)
        gate.complete(listOf(receiver("dm920", "10.0.0.9")))
        val state = viewModel.uiState.first { !it.detecting }
        viewModel.dismissDiscovery()
        viewModel.detectDevices()

        assertEquals(listOf("dm920"), state.discovered?.map { it.name })
        assertFalse(state.discoveryFailed)
        assertEquals(listOf("dm920"), viewModel.uiState.value.discovered?.map { it.name })
        assertEquals(1, searches)
    }

    @Test
    fun addingAllDiscoveredSavesThem() = runTest {
        found = { listOf(receiver("dm920", "10.0.0.9"), receiver("dm7080", "10.0.0.8")) }
        val viewModel = viewModel()
        viewModel.detectDevices()
        viewModel.uiState.first { it.discovered != null }

        viewModel.addAllDetected()
        val state = viewModel.uiState.first { it.profiles.size == 2 }

        assertNull(state.discovered)
        assertEquals(listOf("dm920", "dm7080"), profiles.profiles().map { it.name })
        assertEquals(namedMessage(R.string.profile_added, "dm920', 'dm7080"), state.userMessage)
    }

    private fun viewModel() = ProfilesViewModel(
        profiles,
        ReceiverDiscovery {
            searches++
            found()
        }
    ).also { viewModels += it }

    private fun saved(name: String, host: String): Profile =
        receiver(name, host).also { runBlocking { profiles.save(it) } }
}

internal fun receiver(name: String, host: String): Profile = Profile.getDefault().apply {
    this.name = name
    this.host = host
}
