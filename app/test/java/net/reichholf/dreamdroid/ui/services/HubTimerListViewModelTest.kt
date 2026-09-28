package net.reichholf.dreamdroid.ui.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_CLEANUP
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_LIST
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [HubTimerListViewModel] over the real [TimerRepository] and a [TestReceiver]. */
@OptIn(ExperimentalCoroutinesApi::class)
class HubTimerListViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = SessionConnectionHolder()
    private val viewModels = mutableListOf<HubTimerListViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun remountLoadsOncePerEpoch() = runTest {
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))
        val viewModel = viewModel()
        assertEquals(UiText.Resource(R.string.timer), viewModel.uiState.value.title)

        viewModel.onRemount(0)
        val state = viewModel.settled()
        viewModel.onRemount(0)

        assertEquals(listOf("Navy CIS: L.A.", "Tagesschau"), state.timers.map { it.name })
        assertNull(state.emptyMessage)
        assertEquals(1, receiver.requestsTo(TIMER_LIST).size)
        viewModel.onRemount(1)
        viewModel.settled()
        assertEquals(2, receiver.requestsTo(TIMER_LIST).size)
    }

    @Test
    fun emptyListSaysSo() = runTest {
        receiver.respond(TIMER_LIST, "<e2timerlist></e2timerlist>")
        val viewModel = viewModel()

        viewModel.reload()

        assertEquals(UiText.Resource(R.string.no_list_item), viewModel.settled().emptyMessage)
    }

    @Test
    fun failedLoadClearsTheListAndShowsTheError() = runTest {
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))
        val viewModel = viewModel()
        viewModel.reload()
        viewModel.settled()
        receiver.profiles.database.timerDao().deleteAll()
        receiver.fail(TIMER_LIST)

        viewModel.reload()
        val state = viewModel.settled()

        assertTrue(state.timers.isEmpty())
        val error = EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error"))
        assertEquals(error.contentErrorText(), state.emptyMessage)
    }

    @Test
    fun failedLoadPaintsTheSnapshot() = runTest {
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))
        val viewModel = viewModel()
        viewModel.reload()
        viewModel.settled()
        receiver.fail(TIMER_LIST)

        viewModel.reload()

        assertEquals(2, viewModel.settled().timers.size)
    }

    @Test
    fun cleanupShowsTheReceiverAnswerAndReloads() = runTest {
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))
        receiver.respond(TIMER_CLEANUP, simpleResult(true, "Timer list cleaned"))
        val viewModel = viewModel()

        viewModel.cleanup()
        val state = viewModel.uiState.first { !it.cleaning && it.userMessage != null }

        assertEquals(UiText.Raw("Timer list cleaned"), state.userMessage)
        assertEquals(2, viewModel.uiState.first { it.timers.isNotEmpty() }.timers.size)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun blockedSessionDoesNotCleanUp() {
        val viewModel = viewModel(SessionConnectionHolder())

        assertTrue(viewModel.uiState.value.mutationsBlocked)
        viewModel.cleanup()

        assertFalse(viewModel.uiState.value.cleaning)
        assertTrue(receiver.requestsTo(TIMER_CLEANUP).isEmpty())
    }

    @Test
    fun blockedFollowsSession() {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.mutationsBlocked)

        sessions.onFailure(EnigmaFailure.Auth, hasCache = true)

        assertTrue(viewModel.uiState.value.mutationsBlocked)
    }

    private fun viewModel(sessions: SessionConnectionHolder = this.sessions) =
        HubTimerListViewModel(receiver.timerRepository(), sessions).also { viewModels += it }

    private suspend fun HubTimerListViewModel.settled(): HubTimerListUiState =
        uiState.first { !it.refreshing }
}
