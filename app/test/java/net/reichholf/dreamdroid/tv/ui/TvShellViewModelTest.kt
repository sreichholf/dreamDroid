package net.reichholf.dreamdroid.tv.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ReceiverProfileCheckRepository
import net.reichholf.dreamdroid.room.BouquetTabEntity
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [TvShellViewModel]: the TV startup profile check and reachability probe over the real
 * [ReceiverProfileCheckRepository], a MockWebServer receiver, and Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TvShellViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val sessions = receiver.sessions
    private val viewModels = mutableListOf<TvShellViewModel>()

    /** `/web/deviceinfo` answers a receiver while true, a non-receiver page otherwise. */
    @Volatile
    private var isReceiver = true

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = {
            MockResponse().setBody(
                if (isReceiver) loadWebFixture("deviceinfo.xml") else "<html>no receiver</html>"
            )
        }
        receiver.start()
        sessions.resetForProfileChange()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun startChecksTheProfileAndGoesOnline() = runBlocking<Unit> {
        viewModel().start()

        val status = awaitStatus { it.session == ConnectionStatus.Session.Online }

        assertFalse(status.checking)
        assertEquals(1, deviceInfoRequests())
        assertNotNull(profiles.deviceInfo(profiles.requireCurrent()))
    }

    @Test
    fun aFailedCheckWithoutCacheStaysOnTheGate() = runBlocking<Unit> {
        isReceiver = false
        viewModel().start()

        val status = awaitStatus { !it.checking }

        assertNull(status.session)
        assertNotNull(status.lastFailure)
    }

    @Test
    fun startingAgainForTheSameProfileDoesNotCheckAgain() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.start()
        awaitStatus { it.session == ConnectionStatus.Session.Online }

        viewModel.start()

        assertEquals(1, deviceInfoRequests())
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
    }

    @Test
    fun aProfileWithCachedDeviceInfoIsNotAskedAgain() = runBlocking<Unit> {
        val viewModel = viewModel()
        profiles.setDeviceInfo(profiles.requireCurrent(), loadWebFixture("deviceinfo.xml"))

        viewModel.start()
        awaitStatus { it.session == ConnectionStatus.Session.Online }

        assertEquals(0, deviceInfoRequests())
    }

    @Test
    fun anEditedProfileWithCachedDeviceInfoStaysOnlineWithoutChecking() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.start()
        awaitStatus { it.session == ConnectionStatus.Session.Online }
        val checked = profiles.requireCurrent()
        // An edit that keeps the connection settings replaces the instance, not the id.
        profiles.setCurrent(
            Profile().apply {
                id = checked.id
                name = "renamed"
                host = checked.host
                port = checked.port
            }
        )

        viewModel.start()

        assertFalse(sessions.status.value.checking)
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
        assertEquals(1, deviceInfoRequests())
    }

    @Test
    fun recheckAsksTheReceiverAgain() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.start()
        awaitStatus { it.session == ConnectionStatus.Session.Online }

        isReceiver = false
        viewModel.recheck()
        val status = awaitStatus { !it.checking && it.lastFailure != null }

        assertEquals(2, deviceInfoRequests())
        assertNull(status.session)
    }

    @Test
    fun aProfileSwitchResetsTheSessionAndChecksTheNewProfile() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.start()
        awaitStatus { it.session == ConnectionStatus.Session.Online }
        val first = profiles.requireCurrent()
        val other = Profile().apply {
            name = "other"
            host = first.host
            port = first.port
        }
        profiles.save(other)

        isReceiver = false
        profiles.setCurrent(other.id!!, forceEvent = true)
        val status = awaitStatus { !it.checking }

        assertNull(status.session)
        assertEquals(2, deviceInfoRequests())
    }

    @Test
    fun probeKeepsAnOnlineSessionChecked() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.start()
        awaitStatus { it.session == ConnectionStatus.Session.Online }

        viewModel.probeReachability()

        assertEquals(2, deviceInfoRequests())
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
    }

    @Test
    fun probeSkipsWithoutASession() = runBlocking<Unit> {
        isReceiver = false
        val viewModel = viewModel()
        viewModel.start()
        awaitStatus { !it.checking }

        viewModel.probeReachability()

        assertEquals(1, deviceInfoRequests())
    }

    @Test
    fun aFailedCheckWithCacheMayGoOfflineOnlyForReachability() = runBlocking<Unit> {
        receiver.profiles.database.rosterDao().replaceTabStrip(
            PROFILE_ID,
            "TV",
            listOf(BouquetTabEntity(PROFILE_ID, "TV", 0, FAVOURITES, "Favourites"))
        )
        isReceiver = false
        viewModel().start()

        // A page that is not a receiver is no reachability failure: the gate stays.
        val status = awaitStatus { !it.checking }

        assertNull(status.session)
        assertNotNull(status.lastFailure)
    }

    private fun viewModel(): TvShellViewModel = TvShellViewModel(
        profiles,
        ReceiverProfileCheckRepository(
            receiver.profiles.context,
            profiles,
            enigmaClients(profiles, receiver.profiles.context)
        ),
        receiver.services,
        sessions
    ).also { viewModels += it }

    private fun deviceInfoRequests(): Int = receiver.requestsTo("/web/deviceinfo").size

    private suspend fun awaitStatus(condition: (ConnectionStatus) -> Boolean): ConnectionStatus =
        withTimeout(5_000L) { sessions.status.first(condition) }

    companion object {
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
    }
}
