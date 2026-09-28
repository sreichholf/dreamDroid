package net.reichholf.dreamdroid.data

import kotlin.system.measureTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ReceiverRepository]'s current service and profile-check wait against a MockWebServer. */
class ReceiverRepositoryTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val repository = ReceiverRepository(EnigmaClientFactory(profiles), profiles)

    @BeforeEach
    fun setUp() {
        receiver.answer = { MockResponse().setBody(loadWebFixture("getcurrent.xml")) }
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        receiver.stop()
    }

    @Test
    fun currentServiceParsesTheServiceWithNowAndNext() = runBlocking<Unit> {
        val current = repository.currentService().value

        assertEquals("Das Erste HD", current?.service?.name)
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", current?.service?.reference)
        assertEquals("Tagesschau", current?.now?.title)
        assertNotNull(current?.next)
        assertEquals("/web/getcurrent", receiver.requests.single().requestUrl?.encodedPath)
    }

    @Test
    fun failedCurrentServiceCarriesTheError() = runBlocking<Unit> {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val response = repository.currentService()

        assertNull(response.value)
        assertNotNull(response.error)
    }

    @Test
    fun profileCheckWaitEndsWhenDeviceInfoArrives() = runBlocking<Unit> {
        val waiting = async { repository.awaitProfileCheck(timeoutMs = 5_000L) }
        delay(150)
        assertFalse(waiting.isCompleted)

        profiles.setDeviceInfo(profiles.requireCurrent(), "<e2deviceinfo/>")
        val waited = measureTimeMillis { waiting.await() }

        assertTrue(waited < 1_000L, "waited $waited ms")
    }

    @Test
    fun profileCheckWaitGivesUpAfterTheTimeout() = runBlocking<Unit> {
        val waited = measureTimeMillis { repository.awaitProfileCheck(timeoutMs = 200L) }

        assertTrue(waited in 200L until 2_000L, "waited $waited ms")
    }
}
