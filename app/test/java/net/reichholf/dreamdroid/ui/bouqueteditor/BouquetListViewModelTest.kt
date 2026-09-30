package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetEditorRepository
import net.reichholf.dreamdroid.data.BouquetMode
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [BouquetListViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class BouquetListViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = receiver.profiles.sessions
    private val editor = BouquetEditorRepository(
        enigmaClients(receiver.repository),
        receiver.repository,
        receiver.profiles.services
    )
    private val viewModels = mutableListOf<BouquetListViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
        receiver.respond(EXTERNALS, fixture("web_external.xml"))
        receiver.respond(GET_SERVICES, fixture("getservices_roots_tv.xml"))
        receiver.respond(BACKUP, fixture("result_backup.xml"))
        EDITS.forEach { receiver.respond(it, simpleResult(true, "Done.")) }
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheTvBouquets() = runBlocking {
        val bouquets = viewModel().ready()

        assertEquals(listOf("Favourites (TV)"), bouquets.map { it.name })
        assertEquals(TV_ROOTS[0], receiver.requestsTo(GET_SERVICES).single().query("sRef"))
    }

    @Test
    fun notInstalledWithoutThePlugin() = runBlocking {
        receiver.respond(
            EXTERNALS,
            "<e2webifexternals><e2webifexternal><e2path>autotimer</e2path>" +
                "</e2webifexternal></e2webifexternals>"
        )

        val viewModel = viewModel()

        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == BouquetListContent.NotInstalled }
        }
        assertTrue(receiver.requestsTo(GET_SERVICES).isEmpty())
    }

    @Test
    fun failedPluginCheckIsAFailureNotAMissingPlugin() = runBlocking<Unit> {
        receiver.fail(EXTERNALS)

        val viewModel = viewModel()

        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content is BouquetListContent.Failed }
        }
    }

    @Test
    fun blankAndDuplicateNamesAreRejectedWithoutARequest() = runBlocking {
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.openAdd()

        viewModel.name.set("  ")
        viewModel.confirmAdd()
        assertEquals(
            UiText.Resource(R.string.bouquet_name_empty),
            viewModel.uiState.value.nameError
        )

        viewModel.name.set("Favourites")
        viewModel.confirmAdd()
        val state = viewModel.uiState.value
        assertEquals(UiText.Resource(R.string.bouquet_name_exists), state.nameError)
        assertEquals(BouquetListDialog.Add, state.dialog)
        assertTrue(receiver.requestsTo(ADD_BOUQUET).isEmpty())
        assertTrue(receiver.requestsTo(BACKUP).isEmpty())
    }

    @Test
    fun addSendsTheNameAndListsAgain() = runBlocking {
        val viewModel = viewModel()
        viewModel.ready()
        viewModel.openAdd()
        viewModel.name.set(" News ")

        viewModel.confirmAdd()
        viewModel.settled()

        val add = receiver.requestsTo(ADD_BOUQUET).single()
        assertEquals("News", add.query("name"))
        assertEquals("0", add.query("mode"))
        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun moveSendsOneMoveWithTheFinalPosition() = runBlocking {
        val viewModel = radioViewModel()
        val sky = viewModel.ready()[1]

        viewModel.move(sky.reference, 0)

        assertEquals(sky, viewModel.bouquets().first())
        viewModel.settled()
        val move = receiver.requestsTo(MOVE_BOUQUET).single()
        assertEquals(sky.reference, move.query("sBouquetRef"))
        assertEquals("0", move.query("position"))
        assertEquals("1", move.query("mode"))
        assertEquals(listOf("SKY (Radio)", "Favourites (Radio)"), viewModel.bouquets().names())
        assertEquals(1, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun failedMoveListsAgainAndSaysWhy() = runBlocking {
        receiver.respond(MOVE_BOUQUET, simpleResult(false, "Cannot move."))
        val viewModel = radioViewModel()
        val sky = viewModel.ready()[1]

        viewModel.move(sky.reference, 0)
        val state = viewModel.settled()

        assertEquals(UiText.Raw("Cannot move."), state.userMessage)
        assertEquals(listOf("Favourites (Radio)", "SKY (Radio)"), viewModel.bouquets().names())
        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun removeHidesTheBouquetAtOnce() = runBlocking {
        val viewModel = radioViewModel()
        val favourites = viewModel.ready()[0]
        viewModel.onMenuAction(favourites, BouquetRowAction.Remove)

        viewModel.confirmRemove()

        assertEquals(listOf("SKY (Radio)"), viewModel.bouquets().names())
        viewModel.settled()
        assertEquals(
            favourites.reference,
            receiver.requestsTo(REMOVE_BOUQUET).single().query("sBouquetRef")
        )
    }

    @Test
    fun blockedSessionTakesNoEdit() = runBlocking {
        val viewModel = radioViewModel()
        val sky = viewModel.ready()[1]

        sessions.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )
        withTimeout(TIMEOUT) { viewModel.uiState.first { it.blocked } }
        viewModel.move(sky.reference, 0)
        viewModel.openAdd()

        val state = viewModel.uiState.value
        assertEquals(listOf("Favourites (Radio)", "SKY (Radio)"), viewModel.bouquets().names())
        assertNull(state.dialog)
        assertTrue(receiver.requestsTo(MOVE_BOUQUET).isEmpty())
    }

    @Test
    fun backupMessageShowsOnce() = runBlocking {
        val viewModel = radioViewModel()
        val sky = viewModel.ready()[1]

        viewModel.move(sky.reference, 0)
        assertEquals(
            UiText.Resource(R.string.bouquet_backup_saved, listOf("dreamdroid_pretest.tar")),
            viewModel.settled().userMessage
        )
        viewModel.onMessageShown()
        viewModel.move(sky.reference, 1)

        assertNull(viewModel.settled().userMessage)
        assertEquals(1, receiver.requestsTo(BACKUP).size)
        assertEquals(2, receiver.requestsTo(MOVE_BOUQUET).size)
    }

    private fun viewModel() = BouquetListViewModel(SavedStateHandle(), editor, sessions)
        .also { viewModels += it }

    private suspend fun radioViewModel(): BouquetListViewModel {
        receiver.respond(GET_SERVICES, fixture("getservices_roots_radio.xml"))
        val viewModel = BouquetListViewModel(
            SavedStateHandle(mapOf("bouquet_list_mode" to BouquetMode.Radio.name)),
            editor,
            sessions
        ).also { viewModels += it }
        viewModel.ready()
        assertEquals(RADIO_ROOTS[0], receiver.requestsTo(GET_SERVICES).last().query("sRef"))
        return viewModel
    }

    private suspend fun BouquetListViewModel.ready() = withTimeout(TIMEOUT) {
        uiState.first { it.content is BouquetListContent.Ready && !it.refreshing }
    }.let { (it.content as BouquetListContent.Ready).bouquets }

    /** Waits for the edit started last, and the list after it, to finish. */
    private suspend fun BouquetListViewModel.settled(): BouquetListUiState =
        withTimeout(TIMEOUT) { uiState.first { !it.pending } }

    private fun BouquetListViewModel.bouquets() =
        (uiState.value.content as BouquetListContent.Ready).bouquets

    private fun List<BouquetEntry>.names() = map { it.name }

    private fun RecordedRequest.query(name: String): String? = requestUrl?.queryParameter(name)

    private fun fixture(name: String): String = loadWebFixture("bouqueteditor/$name")

    private companion object {
        const val TIMEOUT = 5_000L
        const val GET_SERVICES = "/web/getservices"
        const val EXTERNALS = "/web/external"
        const val BACKUP = "/bouqueteditor/web/backup"
        const val ADD_BOUQUET = "/bouqueteditor/web/addbouquet"
        const val REMOVE_BOUQUET = "/bouqueteditor/web/removebouquet"
        const val MOVE_BOUQUET = "/bouqueteditor/web/movebouquet"
        val EDITS = listOf(ADD_BOUQUET, REMOVE_BOUQUET, MOVE_BOUQUET)
    }
}
