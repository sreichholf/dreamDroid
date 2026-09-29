package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SendMessageViewModelTest {
    private val viewModels = mutableListOf<SendMessageViewModel>()

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
    fun startsWithTheDefaults() {
        val viewModel = viewModel()

        assertEquals("", viewModel.message.text)
        assertEquals(SendMessageViewModel.DEFAULT_TIMEOUT, viewModel.timeout.text)
        assertEquals(SendMessageViewModel.DEFAULT_TYPE_INDEX, viewModel.uiState.value.typeIndex)
    }

    @Test
    fun theFormSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.message.state.setTextAndPlaceCursorAtEnd("Dinner is ready")
        first.timeout.state.setTextAndPlaceCursorAtEnd("5")
        Snapshot.sendApplyNotifications()
        first.onTypeSelected(0)

        val restored = viewModel(handle)

        assertEquals("Dinner is ready", restored.message.text)
        assertEquals("5", restored.timeout.text)
        assertEquals(0, restored.uiState.value.typeIndex)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        SendMessageViewModel(handle).also { viewModels += it }
}
