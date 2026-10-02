package net.reichholf.dreamdroid.data

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ReceiverRepository]'s current service, live streams, and profile-check wait against a
 * MockWebServer.
 */
class ReceiverRepositoryTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val repository = ReceiverRepository(receiverApis(profiles), profiles)

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
    fun profileCheckWaitEndsWhenDeviceInfoArrives() = runTest {
        val waiting = async { repository.awaitProfileCheck(timeoutMs = 5_000L) }
        advanceTimeBy(1_000L)
        assertFalse(waiting.isCompleted)

        profiles.setDeviceInfo(profiles.requireCurrent(), "<e2deviceinfo/>")
        waiting.await()

        assertTrue(currentTime < 1_200L, "waited $currentTime ms")
    }

    @Test
    fun profileCheckWaitGivesUpAfterTheTimeout() = runTest {
        repository.awaitProfileCheck(timeoutMs = 200L)

        assertEquals(200L, currentTime)
    }

    @Test
    fun setVolumeSendsTheCommandAndParsesTheLevel() = runBlocking<Unit> {
        receiver.answer = { request ->
            if (request.volumePath()) {
                MockResponse().setBody(
                    "<e2volume><e2result>True</e2result><e2current>40</e2current>" +
                        "<e2ismuted>False</e2ismuted></e2volume>"
                )
            } else {
                MockResponse().setResponseCode(404)
            }
        }

        val volume = repository.setVolume("up").value

        assertEquals("40", volume?.current)
        assertEquals("up", receiver.requests.single().requestUrl?.queryParameter("set"))
    }

    @Test
    fun failedVolumeCarriesTheError() = runBlocking<Unit> {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val response = repository.setVolume("up")

        assertNull(response.value)
        assertNotNull(response.error)
    }

    @Test
    fun setPowerStateReportsTheNewState() = runBlocking<Unit> {
        receiver.answer = { request ->
            if (request.powerPath()) {
                MockResponse().setBody(
                    "<e2powerstate><e2instandby>false</e2instandby></e2powerstate>"
                )
            } else {
                MockResponse().setResponseCode(404)
            }
        }

        val state = repository.setPowerState("0")

        assertEquals(true, state.value?.isRunning)
        assertEquals("0", receiver.requests.single().requestUrl?.queryParameter("newstate"))
    }

    @Test
    fun sleepTimerReadsAndWrites() = runBlocking<Unit> {
        receiver.answer = {
            MockResponse().setBody(
                "<e2sleeptimer><e2enabled>True</e2enabled><e2minutes>30</e2minutes>" +
                    "<e2action>standby</e2action><e2text>In 30 minutes</e2text></e2sleeptimer>"
            )
        }

        val read = repository.sleepTimer().value
        assertEquals("True", read?.enabled)

        val written = repository.setSleepTimer("30", "standby", true).value
        assertEquals("standby", written?.action)
        val request = receiver.requests.last().requestUrl!!
        assertEquals("set", request.queryParameter("cmd"))
        assertEquals("30", request.queryParameter("time"))
        assertEquals("standby", request.queryParameter("action"))
        assertEquals("True", request.queryParameter("enabled"))
    }

    @Test
    fun sendMessagePostsTextTypeAndTimeout() = runBlocking<Unit> {
        receiver.answer = { MockResponse().setBody(simpleResult(true, "Sent")) }

        val result = repository.sendMessage("Hello", "2", "10").value

        assertEquals("Sent", result?.stateText)
        val request = receiver.requests.single().requestUrl!!
        assertEquals("Hello", request.queryParameter("text"))
        assertEquals("2", request.queryParameter("type"))
        assertEquals("10", request.queryParameter("timeout"))
    }

    @Test
    fun remoteCommandSendsKeyRcuAndLongType() = runBlocking<Unit> {
        receiver.answer = { MockResponse().setBody(simpleResult(true, "Ok")) }

        repository.remoteCommand(412, simpleRemote = false, longClick = true)
        repository.remoteCommand(113, simpleRemote = true, longClick = false)

        val long = receiver.requests[0].requestUrl!!
        assertEquals("412", long.queryParameter("command"))
        assertEquals("advanced", long.queryParameter("rcu"))
        assertEquals("long", long.queryParameter("type"))
        val short = receiver.requests[1].requestUrl!!
        assertEquals("113", short.queryParameter("command"))
        assertEquals("standard", short.queryParameter("rcu"))
        assertNull(short.queryParameter("type"))
    }

    @Test
    fun liveStreamWithoutZapAndStreamPlaysRightAway() = runBlocking<Unit> {
        val stream = repository.liveStream(SERVICE)

        assertEquals(
            LiveStream.Ready(SERVICE, EnigmaUrls.stream(profiles.requireCurrent(), SERVICE)),
            stream
        )
        assertTrue(receiver.requests.isEmpty())
    }

    @Test
    fun zapAndStreamZapsBeforeTheStream() = runBlocking<Unit> {
        profiles.requireCurrent().zapAndStream = true
        receiver.answer = { MockResponse().setBody(simpleResult(true, "Active service changed")) }

        val stream = repository.liveStream(SERVICE)

        assertEquals(
            LiveStream.Ready(SERVICE, EnigmaUrls.stream(profiles.requireCurrent(), SERVICE)),
            stream
        )
        val zap = receiver.requestsTo("/web/zap").single()
        assertEquals(SERVICE, zap.requestUrl?.queryParameter("sRef"))
    }

    @Test
    fun rejectedZapStreamsNothingAndSaysWhy() = runBlocking<Unit> {
        profiles.requireCurrent().zapAndStream = true
        receiver.answer = { MockResponse().setBody(simpleResult(false, "No free tuner")) }

        assertEquals(LiveStream.Failed(UiText.Raw("No free tuner")), repository.liveStream(SERVICE))
    }

    @Test
    fun zapAndStreamWithoutAServiceIsAContentError() = runBlocking<Unit> {
        profiles.requireCurrent().zapAndStream = true

        assertEquals(
            LiveStream.Failed(UiText.Resource(R.string.get_content_error)),
            repository.liveStream("")
        )
        assertTrue(receiver.requests.isEmpty())
    }

    private fun RecordedRequest.volumePath(): Boolean = requestUrl?.encodedPath == "/web/vol"

    private fun RecordedRequest.powerPath(): Boolean = requestUrl?.encodedPath == "/web/powerstate"

    private companion object {
        const val SERVICE = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
    }
}
