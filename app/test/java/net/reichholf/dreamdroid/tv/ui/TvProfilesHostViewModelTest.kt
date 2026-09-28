package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.awaitIdle
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TvProfilesHostViewModel] over the real repository and an in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvProfilesHostViewModelTest {
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val viewModels = mutableListOf<ViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.awaitIdle() } }
        Dispatchers.resetMain()
    }

    @Test
    fun listsProfilesWithTheActiveOne() = runTest {
        saved("Living Room")
        val bedroom = saved("Bedroom")
        profiles.setCurrent(bedroom.id!!)

        val rows = viewModel().uiState.first { it.profiles.size == 2 }
            .profiles

        assertEquals(listOf(false, true), rows.map { it.active })
    }

    @Test
    fun addingSavesTheDraftAndReturnsToTheList() = runTest {
        val viewModel = viewModel()
        viewModel.showAdd()
        val form = checkNotNull(viewModel.uiState.value.form)
        assertEquals(TvProfilesPage.Add, viewModel.uiState.value.page)

        viewModel.onFormChange(form.copy(name = "Kitchen", host = "10.0.0.4"))
        viewModel.showAdd()
        assertEquals("Kitchen", viewModel.uiState.value.form?.name)
        viewModel.save()
        val state = viewModel.uiState.first { it.event != null && it.profiles.size == 1 }

        assertEquals(TvProfilesEvent.Save(saved = true, currentProfile = false), state.event)
        assertEquals(TvProfilesPage.List, state.page)
        assertNull(state.form)
        assertEquals("Kitchen", profiles.profiles().single().name)
        viewModel.onEventHandled()
        assertNull(viewModel.uiState.value.event)
    }

    @Test
    fun emptyHostIsNotSaved() {
        val viewModel = viewModel()
        viewModel.showAdd()

        viewModel.onFormChange(checkNotNull(viewModel.uiState.value.form).copy(host = ""))
        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(UiText.Resource(R.string.host_empty), state.hostError)
        assertEquals(TvProfilesEvent.Save(saved = false, currentProfile = false), state.event)
        assertEquals(TvProfilesPage.Add, state.page)
        assertTrue(profiles.profiles().isEmpty())
    }

    @Test
    fun savingTheActiveProfileSwitchesToTheNewSettings() = runTest {
        val living = saved("Living Room")
        profiles.setCurrent(living.id!!, forceEvent = true)
        val switched = mutableListOf<String?>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.launch { profiles.switches.collect { switched.add(it.host) } }
        val viewModel = viewModel()

        viewModel.showEdit(living.id!!)
        val form = checkNotNull(viewModel.uiState.first { it.form != null }.form)
        assertEquals(TvProfilesPage.Edit(living.id!!), viewModel.uiState.value.page)
        viewModel.onFormChange(form.copy(host = "10.0.0.99"))
        viewModel.save()
        val state = viewModel.uiState.first { it.event != null }

        assertEquals(TvProfilesEvent.Save(saved = true, currentProfile = true), state.event)
        assertEquals("10.0.0.99", profiles.requireCurrent().host)
        assertEquals(listOf<String?>("10.0.0.99"), switched)
        scope.cancel()
    }

    @Test
    fun activatingReportsSuccess() = runTest {
        val bedroom = saved("Bedroom")
        val viewModel = viewModel()

        viewModel.activate(bedroom.id!!)
        val state = viewModel.uiState.first { it.event != null }

        assertEquals(TvProfilesEvent.Activate(success = true), state.event)
        assertEquals(bedroom.id, profiles.requireCurrent().id)
    }

    @Test
    fun deletingTheActiveProfileReportsIt() = runTest {
        saved("Living Room")
        val bedroom = saved("Bedroom")
        profiles.setCurrent(bedroom.id!!)
        val viewModel = viewModel()
        viewModel.uiState.first { it.profiles.size == 2 }

        viewModel.delete(bedroom.id!!)
        val state = viewModel.uiState.first { it.event != null }

        assertEquals(TvProfilesEvent.Delete(currentProfile = true), state.event)
        assertEquals(listOf("Living Room"), profiles.profiles().map { it.name })
        assertEquals("Living Room", profiles.requireCurrent().name)
    }

    private fun viewModel() = TvProfilesHostViewModel(profiles).also { viewModels += it }

    private fun saved(name: String): Profile = Profile.getDefault().apply {
        this.name = name
        host = "10.0.0.1"
    }.also { profiles.save(it) }
}
