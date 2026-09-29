package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
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
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.SavedTextField
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
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test
    fun routeWithoutIdIsTheCreateForm() {
        val viewModel = viewModel(SavedStateHandle())
        val state = viewModel.uiState.value

        assertEquals(ProfileForm.from(Profile.getDefault()), state.form)
        assertEquals(Profile.getDefault().port.toString(), viewModel.fields.port.text)
        assertFalse(state.canDelete)
        assertEquals(UiText.Resource(R.string.edit_profile), state.title)
    }

    @Test
    fun discoveryRoutePrefillsTheFields() {
        val handle = SavedStateHandle(
            mapOf("name" to "dm920", "host" to "10.0.0.9", "port" to 8080, "user" to "admin")
        )

        val fields = viewModel(handle).fields

        assertEquals("dm920", fields.name.text)
        assertEquals("10.0.0.9", fields.host.text)
        assertEquals("10.0.0.9", fields.streamHost.text)
        assertEquals("8080", fields.port.text)
        assertEquals("admin", fields.user.text)
    }

    @Test
    fun routeWithIdLoadsTheSavedProfile() = runTest {
        val saved = receiver("Living Room", "10.0.0.1").also { profiles.save(it) }
        val viewModel = viewModel(SavedStateHandle(mapOf("profileId" to saved.id!!)))

        val state = viewModel.uiState.first { it.form != null }

        assertEquals("Living Room", viewModel.fields.name.text)
        assertEquals("Living Room", state.savedName)
        assertTrue(state.canDelete)
    }

    @Test
    fun emptyHostIsAnErrorUntilTheHostChanges() = runBlocking<Unit> {
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.fields.name, "box")
        type(viewModel.fields.host, " ")

        viewModel.save()

        assertEquals(UiText.Resource(R.string.host_empty), viewModel.uiState.value.hostError)
        type(viewModel.fields.name, "box 2")
        assertEquals(UiText.Resource(R.string.host_empty), viewModel.uiState.value.hostError)
        type(viewModel.fields.host, "10.0.0.3")
        assertNull(viewModel.uiState.value.hostError)
        assertTrue(profiles.profiles().isEmpty())
        assertNull(viewModel.uiState.value.finished)
    }

    @Test
    fun httpsMovesThePort() {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.onSslChange(true)

        assertTrue(viewModel.uiState.value.form?.ssl == true)
        assertEquals("443", viewModel.fields.port.text)
        viewModel.onSslChange(false)
        assertEquals("80", viewModel.fields.port.text)
    }

    @Test
    fun savingANewProfileAddsIt() = runTest {
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.fields.name, "Kitchen")
        type(viewModel.fields.host, "10.0.0.4")
        viewModel.onFormChange(checkNotNull(viewModel.uiState.value.form).copy(login = true))

        viewModel.save()
        val state = viewModel.uiState.first { it.finished != null }

        assertEquals(namedMessage(R.string.profile_added, "Kitchen"), state.finished)
        val added = profiles.profiles().single()
        assertEquals("10.0.0.4", added.host)
        assertTrue(added.login)
    }

    @Test
    fun savingTheActiveProfileUpdatesCurrent() = runTest {
        val saved = receiver("Living Room", "10.0.0.1").also { profiles.save(it) }
        profiles.setCurrent(saved.id!!)
        val viewModel = viewModel(SavedStateHandle(mapOf("profileId" to saved.id!!)))
        viewModel.uiState.first { it.form != null }

        type(viewModel.fields.name, "Lounge")
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
    fun typedTextAndSwitchesSurviveANewViewModel() = runTest {
        val saved = receiver("Living Room", "10.0.0.1").also { profiles.save(it) }
        val handle = SavedStateHandle(mapOf("profileId" to saved.id!!))
        val first = viewModel(handle)
        val form = checkNotNull(first.uiState.first { it.form != null }.form)
        type(first.fields.name, "Half typed")
        type(first.fields.port, "80")
        first.onFormChange(form.copy(login = true))
        profiles.delete(saved)

        val restored = viewModel(handle)

        assertEquals("Half typed", restored.fields.name.text)
        assertEquals("80", restored.fields.port.text)
        assertEquals("10.0.0.1", restored.fields.host.text)
        assertTrue(restored.uiState.value.form?.login == true)
        assertEquals("Living Room", restored.uiState.value.savedName)
        assertTrue(restored.uiState.value.canDelete)
    }

    private fun viewModel(handle: SavedStateHandle) =
        ProfileEditViewModel(handle, profiles).also { viewModels += it }

    /** Types into [field] the way the text field does, then lets observers see it. */
    private fun type(field: SavedTextField, text: String) {
        field.state.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }
}
