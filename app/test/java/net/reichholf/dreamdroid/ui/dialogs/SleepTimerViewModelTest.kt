package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SleepTimerViewModelTest {
    private val viewModels = mutableListOf<SleepTimerViewModel>()

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
    fun startsFromTheRouteArguments() {
        val viewModel = viewModel(route(45, true, SleepTimer.ACTION_SHUTDOWN))

        assertEquals(
            SleepTimerUiState(minutes = 45, enabled = true, action = SleepTimer.ACTION_SHUTDOWN),
            viewModel.uiState.value
        )
        assertEquals("45", viewModel.minutesText.text)
    }

    @Test
    fun anUnknownActionIsStandbyAndMinutesAreClamped() {
        val viewModel = viewModel(route(5000, false, ""))

        assertEquals(
            SleepTimerUiState(minutes = 999, enabled = false, action = SleepTimer.ACTION_STANDBY),
            viewModel.uiState.value
        )
        assertEquals("999", viewModel.minutesText.text)
    }

    @Test
    fun editsSurviveProcessDeathOverTheRouteArguments() {
        val handle = route(45, false, SleepTimer.ACTION_STANDBY)
        val first = viewModel(handle)
        first.adjustMinutes(1)
        first.onEnabledChanged(true)
        first.onActionSelected(SleepTimer.ACTION_SHUTDOWN)

        val restored = viewModel(handle)

        assertEquals(
            SleepTimerUiState(minutes = 46, enabled = true, action = SleepTimer.ACTION_SHUTDOWN),
            restored.uiState.value
        )
        assertEquals("46", restored.minutesText.text)
    }

    @Test
    fun typedMinutesBecomeTheTimerValue() {
        val viewModel = viewModel(route(45, false, SleepTimer.ACTION_STANDBY))

        viewModel.minutesText.state.setTextAndPlaceCursorAtEnd("123")
        Snapshot.sendApplyNotifications()
        assertEquals(123, viewModel.uiState.value.minutes)

        viewModel.minutesText.state.setTextAndPlaceCursorAtEnd("")
        Snapshot.sendApplyNotifications()
        assertEquals(0, viewModel.uiState.value.minutes)
    }

    @Test
    fun adjustingMinutesMovesTheTypedText() {
        val viewModel = viewModel(route(45, false, SleepTimer.ACTION_STANDBY))

        viewModel.adjustMinutes(1)

        assertEquals(46, viewModel.uiState.value.minutes)
        assertEquals("46", viewModel.minutesText.text)
    }

    private fun viewModel(handle: SavedStateHandle) =
        SleepTimerViewModel(handle).also { viewModels += it }

    private fun route(minutes: Int, enabled: Boolean, action: String) =
        SavedStateHandle(mapOf("minutes" to minutes, "enabled" to enabled, "action" to action))
}
