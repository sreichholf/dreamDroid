package net.reichholf.dreamdroid.ui.timers

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.LOCATIONS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TAGS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_CHANGE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_DELETE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TimerPaneViewModel] over the real [TimerRepository] and a [TestReceiver]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerPaneViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = SessionConnectionHolder()
    private val viewModels = mutableListOf<TimerPaneViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        receiver.respond(
            LOCATIONS,
            "<e2locations><e2location>/hdd/movie/</e2location></e2locations>"
        )
        receiver.respond(TAGS, "<e2tags></e2tags>")
        sessions.onSuccess()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun nothingIsOpenAtFirst() {
        val viewModel = viewModel(SavedStateHandle())

        assertNull(viewModel.opened.value)
        assertNull(viewModel.uiState.value.timer)
    }

    @Test
    fun openingAnotherTimerGivesThePaneANewIdentity() {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.open(TIMER, isCreate = false)
        val first = viewModel.opened.value
        viewModel.open(TIMER.copy(name = "Other"), isCreate = false)

        assertNotEquals(null, first)
        assertNotEquals(first, viewModel.opened.value)
        assertEquals("Other", viewModel.name.text)
    }

    @Test
    fun anOpenFormSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.open(TIMER, isCreate = false)
        viewModel.uiState.first { it.locations.isNotEmpty() }
        viewModel.name.state.setTextAndPlaceCursorAtEnd("Typed")
        Snapshot.sendApplyNotifications()
        viewModel.cancelAndJoin()

        val restored = viewModel(handle)

        assertEquals(viewModel.opened.value, restored.opened.value)
        assertEquals("Typed", restored.name.text)
        assertEquals(REFERENCE, restored.uiState.value.timer?.reference)
        assertFalse(restored.uiState.value.isCreate)
    }

    @Test
    fun dismissingForgetsTheFormAcrossProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.open(TIMER, isCreate = false)

        viewModel.dismiss()
        viewModel.cancelAndJoin()
        val restored = viewModel(handle)

        assertNull(restored.opened.value)
        assertNull(restored.uiState.value.timer)
    }

    @Test
    fun savingSendsTheTypedFieldsOfTheOpenTimer() = runTest {
        receiver.respond(TIMER_CHANGE, simpleResult(true, "Timer changed"))
        val viewModel = viewModel(SavedStateHandle())
        viewModel.open(TIMER, isCreate = false)
        viewModel.uiState.first { it.progress == null && it.locations.isNotEmpty() }
        viewModel.description.state.setTextAndPlaceCursorAtEnd("Typed description")
        Snapshot.sendApplyNotifications()

        viewModel.save()
        viewModel.uiState.first { it.finished }

        val url = receiver.requestsTo(TIMER_CHANGE).single().requestUrl!!
        assertEquals("Typed description", url.queryParameter("description"))
        assertEquals(REFERENCE, url.queryParameter("channelOld"))
    }

    @Test
    fun deleteRemovesTheOpenTimer() = runTest {
        receiver.respond(TIMER_DELETE, simpleResult(true, "Timer deleted"))
        val viewModel = viewModel(SavedStateHandle())
        viewModel.open(TIMER, isCreate = false)
        viewModel.uiState.first { it.progress == null && it.locations.isNotEmpty() }

        viewModel.delete()
        viewModel.uiState.first { it.finished }

        val url = receiver.requestsTo(TIMER_DELETE).single().requestUrl!!
        assertEquals(REFERENCE, url.queryParameter("sRef"))
    }

    private fun viewModel(handle: SavedStateHandle) =
        TimerPaneViewModel(handle, receiver.timerRepository(), sessions).also { viewModels += it }

    private companion object {
        const val REFERENCE = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
        val TIMER = Timer(
            reference = REFERENCE,
            serviceName = "Das Erste HD",
            name = "Sample",
            description = "Desc",
            disabled = "0",
            begin = "1893456000",
            end = "1893459600",
            justPlay = "0",
            afterEvent = "3",
            location = "/hdd/movie/",
            repeated = "0",
            tags = ""
        )
    }
}
