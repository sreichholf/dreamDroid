package net.reichholf.dreamdroid.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.awaitIdle
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ProfileEditViewModel] over the real repository; route arguments as Navigation stores them. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {
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
    fun routeWithoutIdIsTheCreateForm() {
        val state = viewModel(SavedStateHandle()).uiState.value

        assertEquals(ProfileForm.from(Profile.getDefault()), state.form)
        assertFalse(state.canDelete)
        assertEquals(UiText.Resource(R.string.edit_profile), state.title)
    }

    @Test
    fun discoveryRoutePrefillsTheForm() {
        val handle = SavedStateHandle(
            mapOf("name" to "dm920", "host" to "10.0.0.9", "port" to 8080, "user" to "admin")
        )

        val form = viewModel(handle).uiState.value.form

        assertEquals("dm920", form?.name)
        assertEquals("10.0.0.9", form?.host)
        assertEquals("10.0.0.9", form?.streamHost)
        assertEquals("8080", form?.port)
        assertEquals("admin", form?.user)
    }

    @Test
    fun routeWithIdLoadsTheSavedProfile() = runTest {
        val saved = receiver("Living Room", "10.0.0.1").also { profiles.save(it) }

        val state = viewModel(SavedStateHandle(mapOf("profileId" to saved.id!!))).uiState
            .first { it.form != null }

        assertEquals("Living Room", state.form?.name)
        assertEquals("Living Room", state.savedName)
        assertTrue(state.canDelete)
    }

    @Test
    fun emptyHostIsAnErrorUntilTheHostChanges() {
        val viewModel = viewModel(SavedStateHandle())
        val form = checkNotNull(viewModel.uiState.value.form).copy(name = "box", host = " ")
        viewModel.onFormChange(form)

        viewModel.save()

        assertEquals(UiText.Resource(R.string.host_empty), viewModel.uiState.value.hostError)
        viewModel.onFormChange(form.copy(name = "box 2"))
        assertEquals(UiText.Resource(R.string.host_empty), viewModel.uiState.value.hostError)
        viewModel.onFormChange(form.copy(host = "10.0.0.3"))
        assertNull(viewModel.uiState.value.hostError)
        assertTrue(profiles.profiles().isEmpty())
        assertNull(viewModel.uiState.value.finished)
    }

    @Test
    fun savingANewProfileAddsIt() = runTest {
        val viewModel = viewModel(SavedStateHandle())
        viewModel.onFormChange(
            checkNotNull(viewModel.uiState.value.form).copy(name = "Kitchen", host = "10.0.0.4")
        )

        viewModel.save()
        val state = viewModel.uiState.first { it.finished != null }

        assertEquals(namedMessage(R.string.profile_added, "Kitchen"), state.finished)
        assertEquals(listOf("10.0.0.4"), profiles.profiles().map { it.host })
    }

    @Test
    fun savingTheActiveProfileUpdatesCurrent() = runTest {
        val saved = receiver("Living Room", "10.0.0.1").also { profiles.save(it) }
        profiles.setCurrent(saved.id!!)
        val viewModel = viewModel(SavedStateHandle(mapOf("profileId" to saved.id!!)))
        val form = checkNotNull(viewModel.uiState.first { it.form != null }.form)

        viewModel.onFormChange(form.copy(name = "Lounge"))
        viewModel.save()
        val state = viewModel.uiState.first { it.finished != null }

        assertEquals(namedMessage(R.string.profile_updated, "Lounge"), state.finished)
        assertEquals("Lounge", profiles.requireCurrent().name)
        assertEquals("Lounge", profiles.profile(saved.id!!)?.name)
    }

    @Test
    fun deletingRemovesTheProfile() = runTest {
        val saved = receiver("Living Room", "10.0.0.1").also { profiles.save(it) }
        val viewModel = viewModel(SavedStateHandle(mapOf("profileId" to saved.id!!)))
        viewModel.uiState.first { it.form != null }

        viewModel.delete()
        val state = viewModel.uiState.first { it.finished != null }

        assertEquals(namedMessage(R.string.profile_deleted, "Living Room"), state.finished)
        assertTrue(profiles.profiles().isEmpty())
    }

    @Test
    fun typedFieldsSurviveAsSavedState() = runTest {
        val saved = receiver("Living Room", "10.0.0.1").also { profiles.save(it) }
        val handle = SavedStateHandle(mapOf("profileId" to saved.id!!))
        val first = viewModel(handle)
        val form = checkNotNull(first.uiState.first { it.form != null }.form)
        first.onFormChange(form.copy(name = "Half typed", port = "80"))
        profiles.delete(saved)

        val restored = viewModel(handle).uiState.value

        assertEquals("Half typed", restored.form?.name)
        assertEquals("Living Room", restored.savedName)
        assertTrue(restored.canDelete)
    }

    private fun viewModel(handle: SavedStateHandle) =
        ProfileEditViewModel(handle, profiles).also { viewModels += it }
}
