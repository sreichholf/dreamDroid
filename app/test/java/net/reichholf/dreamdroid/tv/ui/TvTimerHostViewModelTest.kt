package net.reichholf.dreamdroid.tv.ui

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
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_CHANGE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_DELETE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_LIST
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TvTimerHostViewModel] over the real [TimerRepository] and a [TestReceiver]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvTimerHostViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = SessionConnectionHolder()
    private val viewModels = mutableListOf<TvTimerHostViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun reloadKeepsTheShownListWhileRefreshing() = runTest {
        sessions.onSuccess()
        val viewModel = viewModel()
        assertEquals(UiText.Resource(R.string.loading), viewModel.uiState.value.emptyMessage)
        viewModel.reload()
        val loaded = viewModel.uiState.first { it.timers.isNotEmpty() }
        assertNull(loaded.emptyMessage)

        viewModel.reload()

        assertEquals(loaded.timers, viewModel.uiState.value.timers)
        assertNull(viewModel.uiState.value.emptyMessage)
    }

    @Test
    fun offlineSessionPaintsTheSnapshotWithoutTheReceiver() = runTest {
        sessions.onSuccess()
        val online = viewModel()
        online.reload()
        online.uiState.first { it.timers.isNotEmpty() }
        sessions.onFailure(EnigmaFailure.Auth, hasCache = true)

        val offline = viewModel()
        offline.reload()

        assertEquals(2, offline.uiState.first { it.timers.isNotEmpty() }.timers.size)
        assertEquals(1, receiver.requestsTo(TIMER_LIST).size)
    }

    @Test
    fun addEditorKeepsItsDraft() {
        val viewModel = viewModel()

        viewModel.showAdd()
        val draft = viewModel.uiState.value.editorTimer
        viewModel.showAdd()

        assertEquals(TvTimerPage.Add, viewModel.uiState.value.page)
        assertSame(draft, viewModel.uiState.value.editorTimer)
        viewModel.showList()
        assertEquals(TvTimerPage.List, viewModel.uiState.value.page)
        assertNull(viewModel.uiState.value.editorTimer)
    }

    @Test
    fun editOpensTheListedTimer() = runTest {
        sessions.onSuccess()
        val viewModel = viewModel()
        viewModel.reload()
        val timers = viewModel.uiState.first { it.timers.isNotEmpty() }.timers

        viewModel.showEdit(1)
        assertEquals(TvTimerPage.Edit(1), viewModel.uiState.value.page)
        assertEquals(timers[1], viewModel.uiState.value.editorTimer)
        viewModel.showEdit(9)
        assertEquals(TvTimerPage.List, viewModel.uiState.value.page)
    }

    @Test
    fun toggleAndDeleteReportTheReceiverAnswer() = runTest {
        sessions.onSuccess()
        receiver.respond(TIMER_CHANGE, simpleResult(true, "Timer changed"))
        receiver.respond(TIMER_DELETE, simpleResult(false, "Timer is recording"))
        val viewModel = viewModel()
        viewModel.reload()
        viewModel.uiState.first { it.timers.isNotEmpty() }

        viewModel.toggleEnabled(0)
        val toggled = viewModel.uiState.first { it.userMessage != null }
        assertEquals(UiText.Raw("Timer changed"), toggled.userMessage)
        assertEquals(
            "1",
            receiver.requestsTo(TIMER_CHANGE).single().requestUrl!!.queryParameter("disabled")
        )
        viewModel.onMessageShown()

        viewModel.deleteTimer(1)
        val deleted = viewModel.uiState.first { it.userMessage != null }
        assertEquals(UiText.Raw("Timer is recording"), deleted.userMessage)
        assertNull(deleted.progress)
    }

    @Test
    fun blockedSessionDoesNotMutate() = runTest {
        val viewModel = viewModel()
        viewModel.reload()
        viewModel.uiState.first { it.timers.isNotEmpty() }
        assertTrue(viewModel.uiState.value.mutationsBlocked)

        viewModel.deleteTimer(0)

        assertNull(viewModel.uiState.value.progress)
        assertTrue(receiver.requestsTo(TIMER_DELETE).isEmpty())
    }

    private fun viewModel() = TvTimerHostViewModel(
        receiver.timerRepository(),
        receiver.repository,
        sessions
    ).also { viewModels += it }
}
