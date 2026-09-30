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
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.enigma.EnigmaFailure
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [BouquetAddServicesViewModel] over the real repositories and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class BouquetAddServicesViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = receiver.profiles.sessions
    private val editor = BouquetEditorRepository(
        enigmaClients(receiver.repository),
        receiver.repository,
        receiver.profiles.services
    )
    private val viewModels = mutableListOf<BouquetAddServicesViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
        receiver.respond(GET_SERVICES, fixture("getservices_with_alternative.xml"))
        receiver.respond(SATELLITES, fixture("satelliteslist_tv.xml"))
        receiver.respond(BACKUP, fixture("result_backup.xml"))
        receiver.respond(ADD_SERVICE, simpleResult(true, "Added."))
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun satellitesAreFoldersOfServices() = runBlocking {
        val viewModel = viewModel()
        assertEquals(UiText.Resource(R.string.bouquet_add_services), viewModel.uiState.value.title)

        viewModel.openSource(ServiceSource.Satellites)
        val satellites = viewModel.ready()
        assertEquals("0", receiver.requestsTo(SATELLITES).single().query("mode"))
        assertEquals(6, satellites.size)
        assertTrue(satellites.all { it.kind == BouquetEntryKind.Directory })

        receiver.respond(GET_SERVICES, serviceList(DAS_ERSTE, ARTE, PHOENIX))
        viewModel.openFolder(satellites[0])
        viewModel.ready()
        assertEquals(
            satellites[0].reference,
            receiver.requestsTo(GET_SERVICES).last().query("sRef")
        )
        assertEquals(UiText.Raw("19.2 O - Kanäle"), viewModel.uiState.value.title)

        assertTrue(viewModel.navigateUp())
        assertEquals(satellites, viewModel.ready())
        assertTrue(viewModel.navigateUp())
        assertNull(viewModel.uiState.value.source)
        assertFalse(viewModel.navigateUp())
    }

    @Test
    fun servicesTheBouquetHasAreNotOffered() = runBlocking {
        val viewModel = presentLoaded()
        receiver.respond(GET_SERVICES, serviceList(DAS_ERSTE, ZDF, ARTE))

        viewModel.openSource(ServiceSource.All)
        val (dasErste, zdf, arte) = viewModel.ready()
        assertEquals(TV_ROOTS[2], receiver.requestsTo(GET_SERVICES).last().query("sRef"))

        val state = viewModel.uiState.value
        // The bouquet has ZDF renamed; the name the rename appended does not matter.
        assertFalse(state.selectable(zdf))
        assertTrue(state.selectable(dasErste))
        viewModel.toggle(zdf)
        viewModel.toggle(arte)
        assertEquals(setOf(arte.reference), viewModel.uiState.value.selected)
    }

    @Test
    fun addSendsThePickedServicesInListOrderAndFinishes() = runBlocking {
        val viewModel = presentLoaded()
        receiver.respond(GET_SERVICES, serviceList(DAS_ERSTE, ARTE, PHOENIX))
        viewModel.openSource(ServiceSource.All)
        val (dasErste, arte, phoenix) = viewModel.ready()

        viewModel.toggle(phoenix)
        viewModel.toggle(dasErste)
        viewModel.toggle(arte)
        viewModel.toggle(arte)
        viewModel.add()
        val finished = withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.finished != null }
        }.finished

        val adds = receiver.requestsTo(ADD_SERVICE)
        assertEquals(listOf(DAS_ERSTE.first, PHOENIX.first), adds.map { it.query("sRef") })
        assertTrue(adds.all { it.query("sBouquetRef") == BOUQUET })
        assertTrue(adds.all { it.query("sRefBefore") == "" })
        assertTrue(adds.all { it.query("Name") == null })
        assertEquals(
            UiText.Resource(R.string.bouquet_backup_saved, listOf("dreamdroid_pretest.tar")),
            finished
        )
    }

    @Test
    fun addAfterTheBackupSaysHowMany() = runBlocking {
        val viewModel = presentLoaded()
        receiver.respond(GET_SERVICES, serviceList(ARTE))
        viewModel.openSource(ServiceSource.All)
        viewModel.toggle(viewModel.ready().single())
        receiver.respond(ADD_MARKER, simpleResult(true, "Added."))
        assertTrue(editor.addMarker(BOUQUET, "Backed up first", "").succeeded)

        viewModel.add()

        assertEquals(
            UiText.Resource(R.string.bouquet_services_added, listOf(1)),
            withTimeout(TIMEOUT) { viewModel.uiState.first { it.finished != null } }.finished
        )
    }

    @Test
    fun rejectionFinishesWithTheReceiverText() = runBlocking {
        receiver.respond(ADD_SERVICE, fixture("result_addservice_duplicate.xml"))
        val viewModel = presentLoaded()
        receiver.respond(GET_SERVICES, serviceList(ARTE))
        viewModel.openSource(ServiceSource.All)
        viewModel.toggle(viewModel.ready().single())

        viewModel.add()

        assertEquals(
            UiText.Raw("Service Das Erste HD already exists in bouquet dreamdroid-test (TV)."),
            withTimeout(TIMEOUT) { viewModel.uiState.first { it.finished != null } }.finished
        )
    }

    @Test
    fun unansweredAddStaysWithTheError() = runBlocking {
        receiver.fail(ADD_SERVICE)
        val viewModel = presentLoaded()
        receiver.respond(GET_SERVICES, serviceList(ARTE))
        viewModel.openSource(ServiceSource.All)
        viewModel.toggle(viewModel.ready().single())

        viewModel.add()
        val state = withTimeout(TIMEOUT) { viewModel.uiState.first { !it.pending } }

        assertNull(state.finished)
        assertNotNull(state.userMessage)
        assertEquals(ServiceSource.All, state.source)
    }

    @Test
    fun blockedSessionAddsNothing() = runBlocking {
        val viewModel = presentLoaded()
        receiver.respond(GET_SERVICES, serviceList(ARTE))
        viewModel.openSource(ServiceSource.All)
        viewModel.toggle(viewModel.ready().single())

        sessions.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )
        withTimeout(TIMEOUT) { viewModel.uiState.first { it.blocked } }
        viewModel.add()

        assertFalse(viewModel.uiState.value.pending)
        assertTrue(receiver.requestsTo(ADD_SERVICE).isEmpty())
    }

    private fun viewModel() = BouquetAddServicesViewModel(
        SavedStateHandle(mapOf("bouquetRef" to BOUQUET, "mode" to "Tv")),
        editor,
        sessions
    ).also { viewModels += it }

    /** A picker that knows the bouquet's entries (`getservices_with_alternative.xml`). */
    private suspend fun presentLoaded(): BouquetAddServicesViewModel {
        val viewModel = viewModel()
        withTimeout(TIMEOUT) { viewModel.uiState.first { it.present.isNotEmpty() } }
        assertEquals(BOUQUET, receiver.requestsTo(GET_SERVICES).single().query("sRef"))
        return viewModel
    }

    private suspend fun BouquetAddServicesViewModel.ready(): List<BouquetEntry> =
        withTimeout(TIMEOUT) {
            uiState.first { it.content is AddServicesList.Ready }
        }.let { (it.content as AddServicesList.Ready).entries }

    private fun serviceList(vararg services: Pair<String, String>): String = services.joinToString(
        prefix = "<e2servicelist>",
        postfix = "</e2servicelist>",
        separator = ""
    ) { (ref, name) ->
        "<e2service><e2servicereference>$ref</e2servicereference>" +
            "<e2servicename>$name</e2servicename></e2service>"
    }

    private fun RecordedRequest.query(name: String): String? = requestUrl?.queryParameter(name)

    private fun fixture(name: String): String = loadWebFixture("bouqueteditor/$name")

    private companion object {
        const val TIMEOUT = 5_000L
        const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val GET_SERVICES = "/web/getservices"
        const val SATELLITES = "/bouqueteditor/web/satelliteslist"
        const val BACKUP = "/bouqueteditor/web/backup"
        const val ADD_SERVICE = "/bouqueteditor/web/addservicetobouquet"
        const val ADD_MARKER = "/bouqueteditor/web/addmarkertobouquet"

        val DAS_ERSTE = "1:0:19:283D:3FB:1:C00000:0:0:0:" to "Das Erste HD"
        val ZDF = "1:0:19:2B66:3F3:1:C00000:0:0:0:" to "ZDF HD"
        val ARTE = "1:0:19:283E:3FB:1:C00000:0:0:0:" to "arte HD"
        val PHOENIX = "1:0:19:2887:40F:1:C00000:0:0:0:" to "phoenix HD"
    }
}
