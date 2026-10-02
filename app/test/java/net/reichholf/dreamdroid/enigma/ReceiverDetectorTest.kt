package net.reichholf.dreamdroid.enigma

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadOwifFixture
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ReceiverDetector] over the `/web/deviceinfo` answers of both web interfaces. */
class ReceiverDetectorTest {
    private val server = MockWebServer()

    private val http by lazy {
        EnigmaHttp(
            Profile().apply {
                host = server.hostName
                port = server.port
            },
            EnigmaOkHttp(),
            WebIfCapabilitiesRepository()
        )
    }

    private val detector by lazy { ReceiverDetector(http) }

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun aDreamboxIsTheDreamboxWebIfWithoutAskingTheApi() = runBlocking {
        server.enqueue(MockResponse().setBody("marker"))

        val flavor = detector.detect(parse(loadWebFixture("deviceinfo.xml"))).value
        // The next request the receiver sees is ours, so the detector sent none.
        http.fetch(MARKER)

        assertEquals(ReceiverFlavor.DreamboxWebIf, flavor)
        assertEquals(MARKER, server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun owifDeviceInfoConfirmedByJsonStatusInfoIsOpenWebif() = runBlocking {
        server.enqueue(MockResponse().setBody(STATUS_INFO))

        val flavor = detector.detect(parse(loadOwifFixture("deviceinfo.xml"))).value

        assertEquals(ReceiverFlavor.OpenWebif, flavor)
        assertEquals(
            "/api/statusinfo",
            server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath
        )
    }

    @Test
    fun owifDeviceInfoWithoutStatusInfoIsUnknown() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>No such resource</html>"))

        assertEquals(EnigmaResponse<ReceiverFlavor>(null), detectOwif())
    }

    @Test
    fun owifDeviceInfoWithANonJsonStatusInfoIsUnknown() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><body>statusinfo</body></html>"))

        assertEquals(EnigmaResponse<ReceiverFlavor>(null), detectOwif())
    }

    @Test
    fun owifDeviceInfoWithAJsonArrayStatusInfoIsUnknown() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        assertEquals(EnigmaResponse<ReceiverFlavor>(null), detectOwif())
    }

    @Test
    fun owifDeviceInfoWithoutAnyStatusInfoAnswerIsAnError() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val detected = detectOwif()

        assertNull(detected.value)
        assertNotNull(detected.error)
    }

    private suspend fun detectOwif() = detector.detect(parse(loadOwifFixture("deviceinfo.xml")))

    private fun parse(xml: String): DeviceInfo = DeviceInfoParser.parse(xml)!!

    private companion object {
        const val MARKER = "/web/about"

        /**
         * An OpenWebif `/api/statusinfo` answer, synthetic: the keys `getStatusInfo` in
         * plugin/controllers/models/info.py of E2OpenPlugins/e2openplugin-OpenWebif at commit
         * e46534f fills for a running service.
         */
        const val STATUS_INFO = """{"volume": 50, "muted": false, "transcoding": false,
            "currservice_filename": "", "currservice_id": 4711,
            "currservice_name": "Tagesschau", "currservice_serviceref":
            "1:0:19:2B66:3F3:1:C00000:0:0:0:", "currservice_begin": "20:00",
            "currservice_begin_timestamp": 1700000000, "currservice_end": "20:15",
            "currservice_end_timestamp": 1700000900, "currservice_description": "",
            "currservice_station": "Das Erste HD", "currservice_fulldescription": "Tagesschau",
            "inStandby": "false", "isRecording": "false", "Streaming_list": ""}"""
    }
}
