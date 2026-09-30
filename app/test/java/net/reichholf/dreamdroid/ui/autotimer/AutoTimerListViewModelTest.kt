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
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.AutoTimerWriteResult
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun theSwitchWritesQuietlyAndListsAgain() = runBlocking<Unit> {
        receiver.respond(EDIT, simpleResult(true, "AutoTimer wurde erfolgreich geändert"))
        val viewModel = viewModel()
        val entry = viewModel.ready().single() as AutoTimerEntry.Readable

        viewModel.setEnabled(entry, true)
        assertTrue(viewModel.uiState.value.pending)
        viewModel.settled()

        assertEquals("1", receiver.requestsTo(EDIT).single().requestUrl!!.queryParameter("enabled"))
        // The first list, the guard's, and the one after the write.
        assertEquals(3, receiver.requestsTo(LIST).size)
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun aWriteFromAnotherScreenListsAgain() = runBlocking<Unit> {
        receiver.respond(EDIT, simpleResult(true, "AutoTimer wurde erfolgreich geändert"))
        val viewModel = viewModel()
        val entry = viewModel.ready().single() as AutoTimerEntry.Readable
        // The guard still sees the AutoTimer as loaded; the box then lists it enabled.
        receiver.respondOnce(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_disabled_full.xml")
                .replace("enabled=\"no\"", "enabled=\"yes\"")
        )

        // The preview's Enable, while the list waits below it.
        assertEquals(
            AutoTimerWriteResult.Done(UiText.Raw("AutoTimer wurde erfolgreich geändert")),
            autoTimers.setEnabled(entry.autoTimer, true)
        )

        val listed = withTimeout(TIMEOUT) {
            viewModel.uiState.first { state ->
                (state.content as? AutoTimerListContent.Ready)?.entries?.any {
                    (it as AutoTimerEntry.Readable).autoTimer.settings.enabled
                } == true
            }
        }
        assertFalse(listed.refreshing)
        // The first list, the guard's, and the one after the write.
        assertEquals(3, receiver.requestsTo(LIST).size)
    }

    @Test
    fun theListsOwnWriteListsOnce() = runBlocking<Unit> {
        receiver.respond(REMOVE, loadWebFixture("autotimer/result_remove.xml"))
        val viewModel = viewModel()
        val entry = viewModel.ready().single()
        receiver.respondOnce(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        receiver.respond(LIST, loadWebFixture("autotimer/list_empty.xml"))

        viewModel.onMenuAction(entry, AutoTimerRowAction.Delete)
        viewModel.confirmDelete()
        viewModel.settled()

        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(3, receiver.requestsTo(LIST).size)
    }

    @Test
    fun aRenumberedListIsReportedAndNothingIsWritten() = runBlocking<Unit> {
        val viewModel = viewModel()
        val entry = viewModel.ready().single() as AutoTimerEntry.Readable
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_enabled.xml").replace("id=\"2\"", "id=\"1\"")
        )

        viewModel.setEnabled(entry, true)
        viewModel.settled()

        assertTrue(receiver.requestsTo(EDIT).isEmpty())
        assertEquals(
            UiText.Resource(R.string.autotimer_changed),
            viewModel.uiState.value.userMessage
        )
        assertEquals(
            listOf(true),
            viewModel.entries().map {
                (it as AutoTimerEntry.Readable).autoTimer.settings.enabled
            }
        )
    }

    @Test
    fun deleteAsksFirstThenRemovesAndReportsTheReply() = runBlocking<Unit> {
        receiver.respond(REMOVE, loadWebFixture("autotimer/result_remove.xml"))
        val viewModel = viewModel()
        val entry = viewModel.ready().single()

        viewModel.onItemMenu(entry)
        viewModel.onMenuAction(entry, AutoTimerRowAction.Delete)
        assertEquals(entry, viewModel.uiState.value.deleting)
        assertTrue(receiver.requestsTo(REMOVE).isEmpty())
        // The guard still sees the AutoTimer; the list after the removal is empty.
        receiver.respondOnce(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        receiver.respond(LIST, loadWebFixture("autotimer/list_empty.xml"))

        viewModel.confirmDelete()
        viewModel.settled()

        assertEquals("1", receiver.requestsTo(REMOVE).single().requestUrl!!.queryParameter("id"))
        assertEquals(UiText.Raw("AutoTimer wurde entfernt"), viewModel.uiState.value.userMessage)
        assertEquals(emptyList<AutoTimerEntry>(), viewModel.entries())
    }

    @Test
    fun noWriteWhileTheSessionBlocksThem() = runBlocking<Unit> {
        val viewModel = viewModel()
        val entry = viewModel.ready().single() as AutoTimerEntry.Readable
        sessions.onFailure(EnigmaFailure.Http(500), hasCache = true)

        viewModel.setEnabled(entry, true)
        viewModel.onMenuAction(entry, AutoTimerRowAction.Delete)

        assertFalse(viewModel.uiState.value.pending)
        assertNull(viewModel.uiState.value.deleting)
        assertEquals(1, receiver.requestsTo(LIST).size)
    }

    @Test
    fun runNowAsksFirstBlocksWritesAndShowsTheSummary() = runBlocking<Unit> {
        receiver.respond(
            PARSE,
            simpleResult(true, "Found a total of 4 matching Events.")
        )
        val viewModel = viewModel()
        val entry = viewModel.ready().single() as AutoTimerEntry.Readable

        viewModel.requestRun()
        assertTrue(viewModel.uiState.value.confirmRun)
        assertTrue(receiver.requestsTo(PARSE).isEmpty())
        val hold = receiver.hold(PARSE)
        viewModel.confirmRun()
        assertTrue(hold.arrived.await(TIMEOUT, TimeUnit.MILLISECONDS))

        viewModel.setEnabled(entry, true)
        assertFalse(viewModel.uiState.value.pending)
        hold.release()
        val done = withTimeout(TIMEOUT) { viewModel.uiState.first { it.runResult != null } }

        assertEquals(UiText.Raw("Found a total of 4 matching Events."), done.runResult)
        assertFalse(done.running)
        assertTrue(receiver.requestsTo(EDIT).isEmpty())
    }

    private fun viewModel() = AutoTimerListViewModel(autoTimers, sessions).also { viewModels += it }

    private suspend fun AutoTimerListViewModel.ready(): List<AutoTimerEntry> =
        withTimeout(TIMEOUT) {
            uiState.first { it.content is AutoTimerListContent.Ready && !it.refreshing }
        }.let { (it.content as AutoTimerListContent.Ready).entries }

    /** Waits for the write started last, and the list after it, to finish. */
    private suspend fun AutoTimerListViewModel.settled() =
        withTimeout(TIMEOUT) { uiState.first { !it.pending } }

    private fun AutoTimerListViewModel.entries(): List<AutoTimerEntry> =
        (uiState.value.content as AutoTimerListContent.Ready).entries

    private companion object {
        const val TIMEOUT = 5_000L
        const val EDIT = "/autotimer/edit"
        const val REMOVE = "/autotimer/remove"
        const val PARSE = "/autotimer/parse"
        const val EXTERNALS = "/web/external"
        const val LIST = "/autotimer"
    }
}
