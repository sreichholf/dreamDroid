package net.reichholf.dreamdroid.tv.ui

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
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TvTimerEditViewModel] over the real [TimerRepository] and a [TestReceiver]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvTimerEditViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = SessionConnectionHolder()
    private lateinit var viewModel: TvTimerEditViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        receiver.respond(
            LOCATIONS,
            "<e2locations><e2location>/hdd/movie/</e2location></e2locations>"
        )
        receiver.respond(TAGS, "<e2tags><e2tag>News</e2tag></e2tags>")
        sessions.onSuccess()
        viewModel = editor(SavedStateHandle())
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModel.cancelAndJoin() }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun bindingTheSameTimerAgainKeepsTheEdits() = runTest {
        val timer = timer("Before recreate")
        viewModel.bind(timer, isCreate = false)
        ready()
        type("After recreate")

        // A configuration change composes the host again with the same launch timer.
        viewModel.bind(timer, isCreate = false)

        assertEquals("After recreate", viewModel.name.text)
    }

    @Test
    fun releaseDropsTheEditsSoTheNextBindStartsOver() = runTest {
        val timer = timer("Launch name")
        viewModel.bind(timer, isCreate = false)
        ready()
        type("Discarded edit")

        viewModel.release(timer, isCreate = false)
        assertNull(viewModel.uiState.value.timer)
        viewModel.bind(timer, isCreate = false)

        assertEquals("Launch name", viewModel.name.text)
    }

    @Test
    fun staleReleaseKeepsTheNewerEditor() = runTest {
        val first = timer("First")
        val second = timer("Second")
        viewModel.bind(first, isCreate = true)
        viewModel.bind(second, isCreate = true)

        viewModel.release(first, isCreate = true)

        assertEquals("Second", viewModel.name.text)
        assertEquals(second, ready().timer)
    }

    @Test
    fun saveAddsTheTimerAndFinishes() = runTest {
        receiver.respond(TIMER_CHANGE, simpleResult(true, "Timer added"))
        viewModel.bind(timer("New"), isCreate = true)
        ready()

        viewModel.save()
        viewModel.uiState.first { it.finished }

        val url = receiver.requestsTo(TIMER_CHANGE).single().requestUrl!!
        assertEquals("New", url.queryParameter("name"))
        assertEquals("0", url.queryParameter("deleteOldOnSave"))
        viewModel.onFinishHandled()
        assertFalse(viewModel.uiState.value.finished)
    }

    @Test
    fun servicePickKeyFollowsTheActiveProfile() {
        val id = receiver.repository.requireCurrent().id

        assertEquals("tv-timer-service-pick:$id", viewModel.servicePickKey())
    }

    @Test
    fun blockedSessionDoesNotSave() = runTest {
        val blocked = TvTimerEditViewModel(
            SavedStateHandle(),
            receiver.timerRepository(),
            receiver.repository,
            SessionConnectionHolder()
        )
        blocked.bind(timer("New"), isCreate = true)
        assertTrue(blocked.uiState.value.mutationsBlocked)

        blocked.save()

        assertTrue(receiver.requestsTo(TIMER_CHANGE).isEmpty())
        blocked.cancelAndJoin()
    }

    @Test
    fun editsSurviveProcessDeathWhenTheSameTimerIsBoundAgain() = runTest {
        val handle = SavedStateHandle()
        val timer = timer("Launch name")
        val first = editor(handle)
        first.bind(timer, isCreate = false)
        first.uiState.first { it.progress == null && it.locations.isNotEmpty() }
        first.name.state.setTextAndPlaceCursorAtEnd("Typed before death")
        first.onZapChange(true)
        Snapshot.sendApplyNotifications()
        first.cancelAndJoin()

        val restored = editor(handle)
        restored.bind(timer, isCreate = false)

        assertEquals("Typed before death", restored.name.text)
        assertEquals("1", restored.uiState.value.timer?.justPlay)
        restored.cancelAndJoin()
    }

    @Test
    fun aReleasedEditorIsNotRestored() = runTest {
        val handle = SavedStateHandle()
        val timer = timer("Launch name")
        val first = editor(handle)
        first.bind(timer, isCreate = false)
        first.name.state.setTextAndPlaceCursorAtEnd("Discarded")
        Snapshot.sendApplyNotifications()
        first.release(timer, isCreate = false)
        first.cancelAndJoin()

        val restored = editor(handle)
        restored.bind(timer, isCreate = false)

        assertEquals("Launch name", restored.name.text)
        restored.cancelAndJoin()
    }

    private fun editor(handle: SavedStateHandle) =
        TvTimerEditViewModel(handle, receiver.timerRepository(), receiver.repository, sessions)

    private suspend fun ready(): TimerEditUiState =
        viewModel.uiState.first { it.progress == null && it.locations.isNotEmpty() }

    private fun type(text: String) {
        viewModel.name.state.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun timer(name: String) = Timer(
        reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
        serviceName = "Das Erste HD",
        name = name,
        begin = "1893456000",
        end = "1893459600",
        disabled = "0",
        justPlay = "0",
        afterEvent = "3",
        location = "/hdd/movie/",
        repeated = "0"
    )
}
