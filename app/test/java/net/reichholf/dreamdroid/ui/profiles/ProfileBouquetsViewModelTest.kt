package net.reichholf.dreamdroid.ui.profiles

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ProfileBouquetsViewModel] over the real profile store and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileBouquetsViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val viewModels = mutableListOf<ViewModel>()

    private val tvBouquets = mutableListOf(FAVOURITES to "Favourites", SPORTS to "Sports")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = ::routes
        receiver.start()
        runBlocking {
            receiver.profiles.database.profileDao()
                .addProfile(checkNotNull(profiles.current.value))
            profiles.setCurrent(EpgTestReceiver.PROFILE_ID)
        }
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsBothBouquetListsAndSavesAToggle() = runBlocking {
        val viewModel = viewModel()

        viewModel.load(EpgTestReceiver.PROFILE_ID)
        val state = viewModel.uiState.first { !it.loading }

        assertEquals(listOf("Favourites", "Sports"), state.tv.map { it.name })
        assertEquals(listOf("Radio"), state.radio.map { it.name })
        assertEquals(emptySet<String>(), state.hidden)

        val sports = state.tv.single { it.name == "Sports" }
        viewModel.setVisible(sports, false)
        val hidden = viewModel.uiState.first { sports.reference in it.hidden }

        assertEquals(setOf(sports.reference), hidden.hidden)
        withTimeout(5_000L) {
            profiles.current.first { it?.hiddenBouquets() == setOf(sports.reference) }
        }
        assertEquals(
            setOf(sports.reference),
            profiles.profile(EpgTestReceiver.PROFILE_ID)!!.hiddenBouquets()
        )

        viewModel.showAll()
        val shown = viewModel.uiState.first { it.hidden.isEmpty() }

        assertTrue(shown.hidden.isEmpty())
        withTimeout(5_000L) {
            profiles.current.first { it?.hiddenBouquets().orEmpty().isEmpty() }
        }
        assertTrue(profiles.profile(EpgTestReceiver.PROFILE_ID)!!.hiddenBouquets().isEmpty())
    }

    @Test
    fun aBouquetTheReceiverAddsLaterIsVisibleWhileTheHiddenOneStaysHidden() = runTest {
        val stored = checkNotNull(profiles.profile(EpgTestReceiver.PROFILE_ID))
        stored.setHiddenBouquets(listOf(SPORTS))
        profiles.save(stored)

        val viewModel = viewModel()
        viewModel.load(EpgTestReceiver.PROFILE_ID)
        val before = viewModel.uiState.first { !it.loading }

        assertEquals(setOf(SPORTS), before.hidden)
        assertEquals(listOf("Favourites", "Sports"), before.tv.map { it.name })

        tvBouquets += DOCUMENTARIES to "Documentaries"
        viewModel.reload()
        val after = viewModel.uiState.first { state ->
            !state.loading && state.tv.any { it.name == "Documentaries" }
        }

        assertEquals(listOf("Favourites", "Sports", "Documentaries"), after.tv.map { it.name })
        assertTrue(after.tv.single { it.name == "Documentaries" }.reference !in after.hidden)
        assertTrue(SPORTS in after.hidden)
    }

    @Test
    fun loadsAProfileThatIsNotActive() = runTest {
        val other = Profile.getDefault().apply {
            name = "Bedroom"
            host = receiver.server.hostName
            ssl = false
            port = receiver.server.port
        }
        profiles.save(other)

        val viewModel = viewModel()
        viewModel.load(other.id!!)
        val state = viewModel.uiState.first { !it.loading }

        assertEquals("Bedroom", state.profileName)
        assertEquals(listOf("Favourites", "Sports"), state.tv.map { it.name })
        assertEquals(EpgTestReceiver.PROFILE_ID, profiles.current.value!!.id)
    }

    private fun viewModel(): ProfileBouquetsViewModel =
        ProfileBouquetsViewModel(profiles, receiver.services).also { viewModels += it }

    private fun routes(request: RecordedRequest): MockResponse = when (request.sRef()) {
        TV_ROOTS[0] -> MockResponse().setBody(serviceList(*tvBouquets.toTypedArray()))
        RADIO_ROOTS[0] -> MockResponse().setBody(serviceList(RADIO to "Radio"))
        else -> MockResponse().setResponseCode(404)
    }

    private fun serviceList(vararg services: Pair<String, String>): String =
        services.joinToString("", "<e2servicelist>", "</e2servicelist>") { (ref, name) ->
            "<e2service><e2servicereference>${ref.xml()}</e2servicereference>" +
                "<e2servicename>$name</e2servicename></e2service>"
        }

    private fun String.xml(): String = replace("&", "&amp;").replace("\"", "&quot;")

    private fun RecordedRequest.sRef(): String? = requestUrl?.queryParameter("sRef")

    private companion object {
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val SPORTS =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet"
        const val DOCUMENTARIES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.documentaries.tv\" ORDER BY bouquet"
        const val RADIO =
            "1:7:2:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.radio\" ORDER BY bouquet"
    }
}
