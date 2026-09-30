package net.reichholf.dreamdroid.ui.autotimer

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.enigma.autotimer.Verdict
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.nav.AutoTimerPreview
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [AutoTimerPreviewViewModel] over the real repository and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoTimerPreviewViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = receiver.profiles.sessions
    private val autoTimers =
        AutoTimerRepository(enigmaClients(receiver.repository), receiver.repository)
    private val epg = EpgRepository(
        enigmaClients(receiver.repository),
        receiver.repository,
        receiver.profiles.database,
        sessions,
        receiver.profiles.services
    )
    private val viewModels = mutableListOf<AutoTimerPreviewViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
        receiver.respond(EXTERNALS, loadWebFixture("bouqueteditor/web_external.xml"))
        receiver.respond(LIST, loadWebFixture("autotimer/list_enabled.xml"))
        receiver.respond(
            TEST,
            loadWebFixture("autotimer/test.xml").replaceFirst(
                "<e2state>OK</e2state>",
                "<e2state>Skip</e2state>"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun splitsUpcomingAndSkippedSoonestFirst() = runBlocking<Unit> {
        val viewModel = viewModel(id = 2)

        val ready = viewModel.ready()

        assertEquals(3, ready.upcoming.size)
        assertEquals(ready.upcoming.sortedBy { it.begin }, ready.upcoming)
        assertEquals(listOf(Verdict.Skip), ready.skipped.map { it.verdict })
        assertEquals("dreamDroid test Wilsberg", viewModel.uiState.value.autoTimer?.settings?.name)
    }

    @Test
    fun aDisabledAutoTimerOffersToEnableItThenPreviews() = runBlocking<Unit> {
        receiver.respond(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        receiver.respond(EDIT, simpleResult(true, "AutoTimer wurde erfolgreich geändert"))
        val viewModel = viewModel(id = 1)
        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerPreviewContent.Disabled }
        }
        assertTrue(receiver.requestsTo(TEST).isEmpty())
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_disabled_full.xml").replace(
                "enabled=\"no\"",
                "enabled=\"yes\""
            )
        )
        // The guard still sees the disabled AutoTimer.
        receiver.respondOnce(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))

        viewModel.enable()
        viewModel.ready()

        assertEquals("1", receiver.requestsTo(EDIT).single().requestUrl!!.queryParameter("enabled"))
        assertEquals("1", receiver.requestsTo(TEST).single().requestUrl!!.queryParameter("id"))
    }

    @Test
    fun aProfileSwitchLeavesTheAutoTimerBehind() = runBlocking<Unit> {
        val viewModel = viewModel(id = 2)
        viewModel.ready()
        val current = receiver.repository.requireCurrent()

        receiver.repository.setCurrent(
            Profile().apply {
                id = 8
                name = "other"
                host = current.host
                port = current.port
            }
        )

        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerPreviewContent.Gone }
        }
    }

    @Test
    fun aTapTogglesTheLogOfASkippedEvent() = runBlocking<Unit> {
        val viewModel = viewModel(id = 2)
        val skipped = viewModel.ready().skipped.single()

        viewModel.toggleLog(skipped)
        assertEquals(setOf(skipped.key), viewModel.uiState.value.expanded)
        viewModel.toggleLog(skipped)
        assertEquals(emptySet<String>(), viewModel.uiState.value.expanded)
    }

    @Test
    fun anUpcomingEventOpensWithItsProgrammeFromTheEpg() = runBlocking<Unit> {
        val viewModel = viewModel(id = 2)
        val match = viewModel.ready().upcoming.first()
        receiver.respond(
            EPG_SERVICE,
            loadWebFixture("autotimer/epgservice_one_event.xml")
                .replace("1790792100", match.begin.epochSecond.toString())
        )

        viewModel.openMatch(match)
        assertEquals(match, viewModel.uiState.value.detail?.match)
        val detail = withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.detail?.epg != MatchEpg.Loading }
        }.detail

        val found = detail?.epg as MatchEpg.Found
        assertEquals("Die Jagd nach dem Hammermörder", found.event.description)
        val url = receiver.requestsTo(EPG_SERVICE).single().requestUrl!!
        assertEquals(match.serviceRef, url.queryParameter("sRef"))
        assertEquals(match.begin.epochSecond.toString(), url.queryParameter("time"))

        viewModel.dismissMatch()
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun anEventTheEpgDoesNotHaveIsMissing() = runBlocking<Unit> {
        receiver.respond(EPG_SERVICE, loadWebFixture("autotimer/epgservice_one_event.xml"))
        val viewModel = viewModel(id = 2)
        val match = viewModel.ready().upcoming.first()

        viewModel.openMatch(match)

        val detail = withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.detail?.epg != MatchEpg.Loading }
        }.detail
        assertEquals(AutoTimerMatchDetail(match, MatchEpg.Missing), detail)
    }

    private fun viewModel(id: Int) = AutoTimerPreviewViewModel(
        SavedStateHandle(
            mapOf(
                AutoTimerPreview::id.name to id,
                AutoTimerPreview::name.name to "dreamDroid test Wilsberg"
            )
        ),
        autoTimers,
        epg,
        sessions
    ).also { viewModels += it }

    private suspend fun AutoTimerPreviewViewModel.ready(): AutoTimerPreviewContent.Ready =
        withTimeout(TIMEOUT) {
            uiState.first {
                it.content is AutoTimerPreviewContent.Ready && !it.refreshing && !it.pending
            }
        }.content as AutoTimerPreviewContent.Ready

    private companion object {
        const val TIMEOUT = 5_000L
        const val EXTERNALS = "/web/external"
        const val LIST = "/autotimer"
        const val EDIT = "/autotimer/edit"
        const val TEST = "/autotimer/test"
        const val EPG_SERVICE = "/web/epgservice"
    }
}
