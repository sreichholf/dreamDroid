package net.reichholf.dreamdroid.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.ReceiverFlavor
import net.reichholf.dreamdroid.enigma.WebIfCapabilities
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.loadOwifFixture
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ReceiverProfileCheckRepository] over a MockWebServer receiver: the flavor it detects and
 * the [WebIfCapabilities] it keeps per profile.
 */
class ProfileCheckRepositoryTest {
    private val receiver = TestReceiver()
    private val profiles = receiver.repository
    private val capabilities = receiver.profiles.capabilities
    private val clients = receiverApis(profiles, capabilities = capabilities)
    private val checks = ReceiverProfileCheckRepository(profiles, clients, capabilities)

    @BeforeEach
    fun setUp() {
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        receiver.shutdown()
    }

    @Test
    fun aDreamboxKeepsItsVersionTable() = runBlocking {
        receiver.respond(DEVICE_INFO, loadWebFixture("deviceinfo.xml"))
        val profile = profiles.requireCurrent()

        assertEquals(ProfileCheckResult(), checks.check(profile))

        assertEquals(ReceiverFlavor.DreamboxWebIf, profiles.flavor(profile))
        assertEquals(WebIfCapabilities(), capabilities.of(profile))
        assertEquals(emptyList<String>(), receiver.requestsTo(STATUS_INFO).map { it.path })
    }

    @Test
    fun anOpenWebifBoxIsNotTooOldAndHasEverything() = runBlocking {
        receiver.respond(DEVICE_INFO, loadOwifFixture("deviceinfo.xml"))
        receiver.respond(STATUS_INFO, """{"volume": 50, "inStandby": "false"}""")
        val profile = profiles.requireCurrent()
        capabilities.set(profile, NOTHING)

        assertEquals(ProfileCheckResult(), checks.check(profile))

        assertEquals(ReceiverFlavor.OpenWebif, profiles.flavor(profile))
        assertEquals(WebIfCapabilities(), capabilities.of(profile))
    }

    @Test
    fun aCachedCheckKeepsTheOpenWebifFlavorWithoutAsking() = runBlocking {
        receiver.respond(DEVICE_INFO, loadOwifFixture("deviceinfo.xml"))
        receiver.respond(STATUS_INFO, "{}")
        val profile = profiles.requireCurrent()
        checks.check(profile)

        assertEquals(ProfileCheckResult(), checks.checkReusingDeviceInfo(profile))

        assertEquals(ReceiverFlavor.OpenWebif, profiles.flavor(profile))
        assertEquals(1, receiver.requestsTo(DEVICE_INFO).size)
        assertEquals(1, receiver.requestsTo(STATUS_INFO).size)
    }

    @Test
    fun anOpenWebifVersionTheApiDoesNotConfirmStaysOnTheDreamboxPathWithoutAWarning() =
        runBlocking {
            receiver.respond(DEVICE_INFO, loadOwifFixture("deviceinfo.xml"))
            val profile = profiles.requireCurrent()
            capabilities.set(profile, NOTHING)

            assertEquals(ProfileCheckResult(), checks.check(profile))

            assertNull(profiles.flavor(profile))
            assertEquals(WebIfCapabilities(), capabilities.of(profile))
            assertEquals(1, receiver.requestsTo(STATUS_INFO).size)
        }

    @Test
    fun aStatusInfoWithoutAnswerFailsTheCheckAndCachesNothing() = runBlocking {
        receiver.respond(DEVICE_INFO, loadOwifFixture("deviceinfo.xml"))
        receiver.respond(
            STATUS_INFO,
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        )
        val profile = profiles.requireCurrent()

        val result = checks.check(profile)

        assertTrue(result.hasError)
        assertFalse(result.isSoftError)
        assertEquals(R.string.connection_error, result.errorTextId)
        assertNotNull(result.failure)
        assertNull(profiles.deviceInfo(profile))
        assertNull(profiles.flavor(profile))
    }

    @Test
    fun aFirstCheckRejectedWith403IsTheIpRejection() = runBlocking {
        receiver.respond(
            DEVICE_INFO,
            MockResponse().setResponseCode(403).setBody(loadOwifFixture("error403.html"))
        )
        val profile = profiles.requireCurrent()

        val result = checks.check(profile)

        assertEquals(EnigmaFailure.IpRejected, result.failure)
        assertEquals(UiText.Resource(R.string.ip_rejected_error), result.errorText)
        assertNull(profiles.flavor(profile))
    }

    @Test
    fun anOldDreamboxTurnsItsFeaturesOffForItsProfileOnly() = runBlocking {
        val old = profiles.requireCurrent()
        val current = otherProfile()
        receiver.respondOnce(DEVICE_INFO, deviceInfo(webIfVersion = "1.6.4"))
        receiver.respondOnce(DEVICE_INFO, deviceInfo(webIfVersion = "1.7.4"))

        assertEquals(TOO_OLD, checks.check(old))
        assertEquals(ProfileCheckResult(), checks.check(current))

        assertEquals(NOTHING, capabilities.of(old))
        assertEquals(WebIfCapabilities(), capabilities.of(current))
    }

    @Test
    fun aDreamboxBetweenSleepTimerAndNowNextGetsTheSleepTimerOnly() = runBlocking {
        receiver.respond(DEVICE_INFO, deviceInfo(webIfVersion = "1.6.8"))
        val profile = profiles.requireCurrent()

        assertEquals(ProfileCheckResult(), checks.check(profile))

        assertEquals(NOTHING.copy(sleepTimer = true), capabilities.of(profile))
    }

    @Test
    fun a405SwitchesToGetForThatProfileOnly() = runBlocking {
        val refusing = profiles.requireCurrent()
        val other = otherProfile()
        receiver.respondOnce(DEVICE_INFO, MockResponse().setResponseCode(405))
        receiver.respond(DEVICE_INFO, loadWebFixture("deviceinfo.xml"))

        clients.forProfile(refusing).deviceInfo()
        clients.forProfile(refusing).deviceInfo()
        clients.forProfile(other).deviceInfo()

        assertEquals(
            listOf("POST", "GET", "GET", "POST"),
            receiver.requestsTo(DEVICE_INFO).map { it.method }
        )
        assertEquals(false, capabilities.of(refusing).postRequest)
        assertEquals(true, capabilities.of(other).postRequest)
    }

    @Test
    fun a405SwitchesAwayFromTheMethodThatRequestUsed() = runBlocking {
        val profile = profiles.requireCurrent()
        val held = receiver.hold(DEVICE_INFO)
        receiver.respondOnce(DEVICE_INFO, MockResponse().setResponseCode(405))
        receiver.respond(DEVICE_INFO, loadWebFixture("deviceinfo.xml"))

        val fetch = async(Dispatchers.IO) { clients.forProfile(profile).deviceInfo() }
        assertTrue(held.arrived.await(5, TimeUnit.SECONDS))
        // Another client's 405 switched the profile to GET while this POST was out.
        capabilities.setPostRequest(profile, false)
        held.release()
        val response = withTimeout(5_000L) { fetch.await() }

        assertNull(response.error)
        assertEquals(listOf("POST", "GET"), receiver.requestsTo(DEVICE_INFO).map { it.method })
        assertEquals(false, capabilities.of(profile).postRequest)
    }

    /** A second saved profile on the same receiver. */
    private fun otherProfile(): Profile = profiles.requireCurrent().let { first ->
        Profile().apply {
            id = first.id!! + 1
            name = "other"
            host = first.host
            port = first.port
        }
    }

    private fun deviceInfo(webIfVersion: String): String = loadWebFixture("deviceinfo.xml").replace(
        "<e2webifversion>1.7.4</e2webifversion>",
        "<e2webifversion>$webIfVersion</e2webifversion>"
    )

    private companion object {
        const val DEVICE_INFO = "/web/deviceinfo"
        const val STATUS_INFO = "/api/statusinfo"

        val NOTHING = WebIfCapabilities(nowNext = false, sleepTimer = false, postRequest = false)

        val TOO_OLD = ProfileCheckResult(
            hasError = true,
            isSoftError = true,
            errorTextId = R.string.version_too_low
        )
    }
}
