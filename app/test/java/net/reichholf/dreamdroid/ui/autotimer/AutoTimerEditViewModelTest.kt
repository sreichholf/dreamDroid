package net.reichholf.dreamdroid.ui.autotimer

import androidx.lifecycle.SavedStateHandle
import java.time.DayOfWeek
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
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
import net.reichholf.dreamdroid.enigma.autotimer.RecordMode
import net.reichholf.dreamdroid.enigma.autotimer.SearchType
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.nav.AutoTimerEdit
import net.reichholf.dreamdroid.ui.nav.AutoTimerPreview
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [AutoTimerEditViewModel] over the real repository and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoTimerEditViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = receiver.profiles.sessions
    private val autoTimers =
        AutoTimerRepository(enigmaClients(receiver.repository), receiver.repository)
    private val viewModels = mutableListOf<AutoTimerEditViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
        receiver.respond(EXTERNALS, loadWebFixture("bouqueteditor/web_external.xml"))
        receiver.respond(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        receiver.respond(EDIT, simpleResult(true, "AutoTimer wurde erfolgreich geändert"))
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun editLoadsTheAutoTimerAndSendsOnlyWhatChanged() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        assertEquals("Wilsberg", viewModel.match.text)
        assertEquals("dreamDroid test Wilsberg", viewModel.name.text)

        viewModel.toggleDay(DayFilter.On(DayOfWeek.SATURDAY))
        viewModel.save()
        val saved = viewModel.saved()

        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), saved)
        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals(
            listOf("id", "match", "name", "title", "shortdescription", "description", "dayofweek"),
            query.queryParameterNames.toList()
        )
        assertEquals(listOf("6"), query.queryParameterValues("dayofweek"))
    }

    @Test
    fun anUnchangedEditShowsTheAutoTimerWithoutWriting() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()

        viewModel.save()

        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), viewModel.saved())
        assertTrue(receiver.requestsTo(EDIT).isEmpty())
    }

    @Test
    fun createStartsFromTheDefaultsAndFindsTheNewAutoTimer() = runBlocking<Unit> {
        val viewModel = create()
        viewModel.editing()
        assertEquals("", viewModel.match.text)
        receiver.respond(EDIT, loadWebFixture("autotimer/result_add.xml"))

        viewModel.save()
        assertEquals(
            UiText.Resource(R.string.autotimer_match_empty),
            viewModel.uiState.value.matchError
        )
        assertTrue(receiver.requestsTo(EDIT).isEmpty())

        viewModel.match.set("Wilsberg")
        viewModel.name.set(" dreamDroid test Wilsberg ")
        viewModel.setSearchType(SearchType.Exact)
        viewModel.save()

        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), viewModel.saved())
        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertNull(query.queryParameter("id"))
        assertEquals("dreamDroid test Wilsberg", query.queryParameter("name"))
        assertEquals("exact", query.queryParameter("searchType"))
    }

    @Test
    fun anIdNamingAnotherAutoTimerIsGone() = runBlocking<Unit> {
        val viewModel = edit(name = "Tatort")

        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerEditContent.Gone }
        }
    }

    @Test
    fun aRenumberedListStopsTheSaveAndReloadStartsOver() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        viewModel.setEnabled(true)
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_enabled.xml").replace("id=\"2\"", "id=\"1\"")
        )

        viewModel.save()
        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerEditContent.Changed }
        }
        assertTrue(receiver.requestsTo(EDIT).isEmpty())

        receiver.respond(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        viewModel.reload()
        viewModel.editing()
        assertEquals(false, viewModel.uiState.value.draft.enabled)
    }

    @Test
    fun pickedTargetsAreAddedOnce() = runBlocking<Unit> {
        val viewModel = edit()
        val before = viewModel.editing().draft.targets
        val zdf = before.first()
        val extra = Target.Channel("1:0:19:283D:3FB:1:C00000:0:0:0:", "Das Erste HD")

        viewModel.addTargets(listOf(zdf, extra))

        assertEquals(before + extra, viewModel.uiState.value.draft.targets)
    }

    @Test
    fun zappingAgainKeepsTheLoadedEndTimeSetting() = runBlocking<Unit> {
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_disabled_full.xml")
                .replace("enabled=\"no\"", "enabled=\"no\" justplay=\"1\" setEndtime=\"0\"")
        )
        val viewModel = edit()
        viewModel.editing()

        viewModel.setZap(false)
        viewModel.setZap(true)

        assertEquals(RecordMode.Zap(setEndTime = false), viewModel.uiState.value.draft.recordMode)
    }

    @Test
    fun processDeathKeepsTheDraftWithoutAskingTheBox() = runBlocking<Unit> {
        val handle = handle(id = 1, name = "dreamDroid test Wilsberg")
        val viewModel = viewModel(handle)
        viewModel.editing()
        viewModel.setCaseSensitive(true)
        viewModel.match.set("Wilsberg!")
        val requests = receiver.requests.size

        val restored = viewModel(handle)

        assertEquals(AutoTimerEditContent.Editing, restored.uiState.value.content)
        assertTrue(restored.uiState.value.draft.caseSensitive)
        assertEquals("Wilsberg!", restored.match.text)
        assertEquals(requests, receiver.requests.size)
    }

    private fun edit(name: String = "dreamDroid test Wilsberg") = viewModel(handle(1, name))

    private fun create() = viewModel(handle(AutoTimerEditViewModel.NEW_ID, ""))

    private fun handle(id: Int, name: String) = SavedStateHandle(
        mapOf(AutoTimerEdit::id.name to id, AutoTimerEdit::name.name to name)
    )

    private fun viewModel(handle: SavedStateHandle) =
        AutoTimerEditViewModel(handle, autoTimers, sessions).also { viewModels += it }

    private suspend fun AutoTimerEditViewModel.editing(): AutoTimerEditUiState =
        withTimeout(TIMEOUT) { uiState.first { it.content == AutoTimerEditContent.Editing } }

    private suspend fun AutoTimerEditViewModel.saved(): AutoTimerPreview =
        withTimeout(TIMEOUT) { uiState.first { it.saved != null } }.saved!!

    private companion object {
        const val TIMEOUT = 5_000L
        const val EXTERNALS = "/web/external"
        const val LIST = "/autotimer"
        const val EDIT = "/autotimer/edit"
    }
}
