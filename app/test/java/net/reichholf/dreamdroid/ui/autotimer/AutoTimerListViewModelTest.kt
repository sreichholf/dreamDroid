package net.reichholf.dreamdroid.ui.autotimer

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [AutoTimerListViewModel] over the real repository and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoTimerListViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = receiver.profiles.sessions
    private val autoTimers =
        AutoTimerRepository(enigmaClients(receiver.repository), receiver.repository)
    private val viewModels = mutableListOf<AutoTimerListViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
        receiver.respond(EXTERNALS, loadWebFixture("bouqueteditor/web_external.xml"))
        receiver.respond(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun listsTheAutoTimers() = runBlocking<Unit> {
        val entries = viewModel().ready()

        assertEquals(listOf("dreamDroid test Wilsberg"), entries.map { it.name })
    }

    @Test
    fun missingPluginWithoutAList() = runBlocking<Unit> {
        receiver.respond(EXTERNALS, "<e2webifexternals></e2webifexternals>")

        val viewModel = viewModel()

        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerListContent.PluginMissing }
        }
        assertTrue(receiver.requestsTo(LIST).isEmpty())
    }

    @Test
    fun aFailedListLoadsAgainOnceTheSessionTakesRequests() = runBlocking<Unit> {
        receiver.fail(LIST)
        sessions.onFailure(EnigmaFailure.Http(500), hasCache = true)
        val viewModel = viewModel()
        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content is AutoTimerListContent.Failed && it.blocked }
        }
        receiver.respond(LIST, loadWebFixture("autotimer/list_enabled.xml"))

        sessions.onSuccess()

        assertEquals(listOf("dreamDroid test Wilsberg"), viewModel.ready().map { it.name })
        assertEquals(2, receiver.requestsTo(LIST).size)
    }

    @Test
    fun reloadKeepsTheListWhileRefreshing() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.ready()
        val hold = receiver.hold(LIST)

        viewModel.reload()
        assertTrue(hold.arrived.await(TIMEOUT, TimeUnit.MILLISECONDS))

        val state = viewModel.uiState.value
        assertTrue(state.refreshing)
        assertTrue(state.content is AutoTimerListContent.Ready)
        hold.release()
        viewModel.ready()
    }

    private fun viewModel() = AutoTimerListViewModel(autoTimers, sessions).also { viewModels += it }

    private suspend fun AutoTimerListViewModel.ready(): List<AutoTimerEntry> =
        withTimeout(TIMEOUT) {
            uiState.first { it.content is AutoTimerListContent.Ready && !it.refreshing }
        }.let { (it.content as AutoTimerListContent.Ready).entries }

    private companion object {
        const val TIMEOUT = 5_000L
        const val EXTERNALS = "/web/external"
        const val LIST = "/autotimer"
    }
}
