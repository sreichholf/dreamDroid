package net.reichholf.dreamdroid.enigma

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerApi
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerListParser
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The requests [DreamboxWebIfApi] sends to `/web` and the WebBouquetEditor and AutoTimer
 * plugins, and the stream and file URLs it builds.
 */
class DreamboxWebIfApiTest {
    private val server = MockWebServer()

    private val api by lazy { api(WebIfCapabilities()) }

    private fun api(capabilities: WebIfCapabilities) = DreamboxWebIfApi(
        EnigmaHttp(
            Profile().apply {
                host = server.hostName
                port = server.port
            },
            EnigmaOkHttp(),
            WebIfCapabilitiesRepository()
        ),
        capabilities
    )

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun epgNowNextAsksEpgNowNextByBouquet() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        val rows = api.epgNowNext(BOUQUET).value!!

        val url = takeUrl()
        assertEquals("/web/epgnownext", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertNull(url.queryParameter("sRef"))
        assertEquals(1, rows.size)
    }

    @Test
    fun withoutNowNextEpgNowNextAsksEpgNowWithOneRowPerEvent() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        val rows = api(WebIfCapabilities(nowNext = false)).epgNowNext(BOUQUET).value!!

        val url = takeUrl()
        assertEquals("/web/epgnow", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.now != null && it.next == null })
    }

    @Test
    fun epgNowNextAsksAProviderByBRef() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        api(WebIfCapabilities(nowNext = false)).epgNowNext(PROVIDERS)

        val url = takeUrl()
        assertEquals("/web/epgnow", url.encodedPath)
        assertEquals(PROVIDERS, url.queryParameter("bRef"))
        assertNull(url.queryParameter("sRef"))
    }

    @Test
    fun epgMultiSendsTheWindowLengthInWholeMinutesRoundedDown() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.epgMulti(BOUQUET, START, START + 90 * 60 + 59)
        api.epgMulti(BOUQUET, START, START + 30)

        val url = takeUrl()
        assertEquals("/web/epgmulti", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(START.toString(), url.queryParameter("time"))
        assertEquals("90", url.queryParameter("endTime"))
        assertEquals("1", takeUrl().queryParameter("endTime"))
    }

    @Test
    fun serviceEpgWindowSendsItsLengthInWholeMinutesRoundedUp() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.serviceEpg(SERVICE, START, START + 61)
        api.serviceEpg(SERVICE, START, START)

        val url = takeUrl()
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(SERVICE, url.queryParameter("sRef"))
        assertEquals(START.toString(), url.queryParameter("time"))
        assertEquals("2", url.queryParameter("endTime"))
        assertEquals("1", takeUrl().queryParameter("endTime"))
    }

    @Test
    fun serviceEpgWithoutAWindowSendsOnlyTheService() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.serviceEpg(SERVICE)

        val url = takeUrl()
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(setOf("sRef"), url.queryParameterNames)
    }

    @Test
    fun addTimerSendsTimerChangeWithoutAnOldTimer() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.addTimer(TIMER)

        assertEquals(
            "/web/timerchange?$TIMER_QUERY&deleteOldOnSave=0",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun editTimerSendsTheNewTimerAndTheOldOnesKeys() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val old = TIMER.copy(reference = "1:0:1:old", begin = "1699990000", end = "1699993600")

        api.editTimer(old, TIMER)

        assertEquals(
            "/web/timerchange?$TIMER_QUERY&channelOld=1%3A0%3A1%3Aold&beginOld=1699990000" +
                "&endOld=1699993600&deleteOldOnSave=1",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun addTimerForEventSendsServiceAndEventId() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.addTimerForEvent(Event(serviceReference = SERVICE, eventId = "39150"))

        assertEquals(
            "/web/timeraddbyeventid?sRef=$SERVICE_ENCODED&eventid=39150",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun deleteTimerSendsServiceBeginAndEnd() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.deleteTimer(TIMER)

        assertEquals(
            "/web/timerdelete?sRef=$SERVICE_ENCODED&begin=1700000000&end=1700003600",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun timerListAndCleanupSendFixedQueries() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.timers()
        api.cleanupTimers()

        assertEquals("/web/timerlist?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals(
            "/web/timercleanup?cleanup=true",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun moviesSendsTheLocationAndTheTagsJoinedBySpaces() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.movies("/media/hdd/movie/", listOf("news", "kids"))
        api.movies("", emptyList())

        assertEquals(
            "/web/movielist?dirname=%2Fmedia%2Fhdd%2Fmovie%2F&tag=news%20kids",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
        assertEquals("/web/movielist?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun deleteMovieSendsItsReference() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.deleteMovie(Movie(reference = RECORDING_REF))

        assertEquals(
            "/web/moviedelete?sRef=1%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A$RECORDING_ENCODED",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun playMediaSendsTheReferenceAsFile() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.playMedia("4097:0:0:0:0:0:0:0:0:0:http%3a//x/y.mp4:Title")

        assertEquals(
            "/web/mediaplayerplay?file=4097%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A" +
                "http%253a%2F%2Fx%2Fy.mp4%3ATitle",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun locationsAndTagsReadTheirLists() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "<e2locations><e2location>/media/hdd/movie/</e2location></e2locations>"
            )
        )
        server.enqueue(
            MockResponse().setBody("<e2tags><e2tag>News</e2tag><e2tag>Kids</e2tag></e2tags>")
        )

        assertEquals(listOf("/media/hdd/movie/"), api.locations().value)
        assertEquals(listOf("News", "Kids"), api.tags().value)

        assertEquals("/web/getlocations?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals("/web/gettags?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun downloadRecordingCopiesFileIntoTheDestination() = runBlocking {
        server.enqueue(MockResponse().setBody("recording"))
        server.enqueue(MockResponse().setResponseCode(500))
        val destination = Files.createTempFile("recording", ".ts").toFile()

        try {
            assertNull(api.downloadRecording(RECORDING_PATH, destination))
            assertEquals("recording", destination.readText())
            assertNotNull(api.downloadRecording(RECORDING_PATH, destination))
        } finally {
            destination.delete()
        }

        assertEquals(
            "/file?file=$RECORDING_ENCODED",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun liveStreamOnTheStreamPortLeavesCredentialsOutOfPlainHttp() {
        assertEquals(
            "http://box.local:8001/$SERVICE_ENCODED",
            urlClient().liveStreamUrl(SERVICE)
        )
    }

    @Test
    fun recordingStreamUsesTheFilePortAndRecordingFileTheWebPort() {
        val client = urlClient()
        val movie = Movie(reference = RECORDING_REF, fileName = RECORDING_PATH)

        assertEquals(
            "http://box.local:8080/file?file=$RECORDING_ENCODED",
            client.recordingStreamUrl(movie)
        )
        assertEquals(
            "http://box.local:80/file?file=$RECORDING_ENCODED",
            client.recordingFileUrl(RECORDING_PATH)
        )
    }

    @Test
    fun recordingStreamOverHttpsCarriesTheFileLogin() {
        val client = urlClient { fileSsl = true }
        val movie = Movie(reference = RECORDING_REF, fileName = RECORDING_PATH)

        assertEquals(
            "https://root:secret@box.local:8080/file?file=$RECORDING_ENCODED",
            client.recordingStreamUrl(movie)
        )
    }

    @Test
    fun deviceInfoAsksDeviceInfo() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))

        assertNotNull(api.deviceInfo().value)

        assertEquals("/web/deviceinfo?", takePath())
    }

    @Test
    fun zapSendsTheReference() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.zap(SERVICE)

        assertEquals("/web/zap?sRef=$SERVICE_ENCODED", takePath())
    }

    @Test
    fun remoteCommandSendsKeyRemoteAndLongPress() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("")) }

        api.remoteCommand(412, simpleRemote = false, longPress = true)
        api.remoteCommand(113, simpleRemote = true, longPress = false)
        api.remoteCommand(352, simpleRemote = false, longPress = false)

        assertEquals("/web/remotecontrol?command=412&rcu=advanced&type=long", takePath())
        assertEquals("/web/remotecontrol?command=113&rcu=standard", takePath())
        assertEquals("/web/remotecontrol?command=352&rcu=advanced", takePath())
    }

    @Test
    fun setVolumeSendsTheCommand() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("")) }

        VolumeCommand.entries.forEach { api.setVolume(it) }

        assertEquals("/web/vol?set=up", takePath())
        assertEquals("/web/vol?set=down", takePath())
        assertEquals("/web/vol?set=mute", takePath())
    }

    @Test
    fun setPowerStateSendsEnigmaStateCodes() = runBlocking {
        repeat(4) { server.enqueue(MockResponse().setBody("")) }

        api.setPowerState(PowerCommand.ToggleStandby)
        api.setPowerState(PowerCommand.Shutdown)
        api.setPowerState(PowerCommand.Reboot)
        api.setPowerState(PowerCommand.RestartGui)

        assertEquals("/web/powerstate?newstate=0", takePath())
        assertEquals("/web/powerstate?newstate=1", takePath())
        assertEquals("/web/powerstate?newstate=2", takePath())
        assertEquals("/web/powerstate?newstate=3", takePath())
    }

    @Test
    fun sendMessageSendsTextTypeAndTimeout() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.sendMessage("Hello & bye", "2", "10")

        assertEquals("/web/message?text=Hello%20%26%20bye&type=2&timeout=10", takePath())
    }

    @Test
    fun sleepTimerReadsWithoutParametersAndWritesWithSet() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("")) }

        api.sleepTimer()
        api.setSleepTimer("30", "standby", enabled = true)
        api.setSleepTimer("0", "shutdown", enabled = false)

        assertEquals("/web/sleeptimer?", takePath())
        assertEquals("/web/sleeptimer?cmd=set&time=30&action=standby&enabled=True", takePath())
        assertEquals("/web/sleeptimer?cmd=set&time=0&action=shutdown&enabled=False", takePath())
    }

    @Test
    fun screenshotGrabsAJpegIntoATimestampedFile() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG)))

        assertTrue(api.screenshot().value!!.contentEquals(JPEG))

        assertTrue(GRAB.matches(takePath()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun screenshotWithoutAnImageFromGrabAsksScreenshot() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG)))

        assertTrue(api.screenshot().value!!.contentEquals(JPEG))

        assertTrue(GRAB.matches(takePath()))
        assertEquals("/screenshot?format=jpg&osd=1&video=1", takePath())
    }

    @Test
    fun pluginsAreLookedUpByTheirPathInWebExternals() = runBlocking {
        server.enqueue(MockResponse().setBody(EXTERNALS))
        server.enqueue(MockResponse().setBody(EXTERNALS))

        assertEquals(AutoTimerPlugin.Missing, api.autoTimerPlugin().value)
        assertEquals(true, api.hasBouquetEditor().value)

        assertEquals("/web/external?", takePath())
        assertEquals("/web/external?", takePath())
    }

    @Test
    fun theAutoTimerApiIsTheVersionItRegistered() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("bouqueteditor/web_external.xml")))
        server.enqueue(MockResponse().setBody(EXTERNALS_AUTOTIMER_17))

        assertEquals(AutoTimerPlugin.Installed(AutoTimerApi.V1_6), api.autoTimerPlugin().value)
        assertEquals(AutoTimerPlugin.Installed(AutoTimerApi.V1_7), api.autoTimerPlugin().value)
    }

    @Test
    fun bouquetEditorSatellitesSendTheMode() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody("")) }

        api.bouquetEditorSatellites(BouquetMode.Tv)
        api.bouquetEditorSatellites(BouquetMode.Radio)

        assertEquals("/bouqueteditor/web/satelliteslist?mode=0", takePath())
        assertEquals("/bouqueteditor/web/satelliteslist?mode=1", takePath())
    }

    @Test
    fun bouquetEditsSendThePluginParameters() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setBody("")) }

        api.addBouquet(BouquetMode.Tv, "News & Co")
        api.removeBouquet(BouquetMode.Radio, FAVOURITES)
        api.moveBouquet(BouquetMode.Tv, FAVOURITES, 2)
        api.renameBouquet(BouquetMode.Radio, FAVOURITES, "Mine")
        api.backupBouquets("dreamdroid_1700000000")

        assertEquals("/bouqueteditor/web/addbouquet?name=News%20%26%20Co&mode=0", takePath())
        assertEquals(
            "/bouqueteditor/web/removebouquet?sBouquetRef=$FAVOURITES_ENCODED&mode=1",
            takePath()
        )
        assertEquals(
            "/bouqueteditor/web/movebouquet?sBouquetRef=$FAVOURITES_ENCODED&mode=0&position=2",
            takePath()
        )
        assertEquals(
            "/bouqueteditor/web/renameservice?sRef=$FAVOURITES_ENCODED&mode=1&newName=Mine",
            takePath()
        )
        assertEquals("/bouqueteditor/web/backup?Filename=dreamdroid_1700000000", takePath())
    }

    @Test
    fun bouquetServiceEditsSendThePluginParameters() = runBlocking {
        repeat(6) { server.enqueue(MockResponse().setBody("")) }

        api.addServiceToBouquet(FAVOURITES, ERSTE)
        api.removeBouquetService(FAVOURITES, ERSTE)
        api.moveBouquetService(BouquetMode.Radio, FAVOURITES, ERSTE, 3)
        api.renameBouquetService(FAVOURITES, ERSTE, SERVICE, "ARD")
        api.addBouquetMarker(FAVOURITES, "News", "")
        api.addBouquetMarker(FAVOURITES, "News", SERVICE)

        val bouquet = "sBouquetRef=$FAVOURITES_ENCODED"
        assertEquals(
            "/bouqueteditor/web/addservicetobouquet?$bouquet&sRef=$ERSTE_ENCODED&sRefBefore=",
            takePath()
        )
        assertEquals(
            "/bouqueteditor/web/removeservice?$bouquet&sRef=$ERSTE_ENCODED",
            takePath()
        )
        assertEquals(
            "/bouqueteditor/web/moveservice?$bouquet&sRef=$ERSTE_ENCODED&position=3&mode=1",
            takePath()
        )
        assertEquals(
            "/bouqueteditor/web/renameservice?$bouquet&sRef=$ERSTE_ENCODED" +
                "&sRefBefore=$SERVICE_ENCODED&newName=ARD",
            takePath()
        )
        assertEquals(
            "/bouqueteditor/web/addmarkertobouquet?$bouquet&Name=News&sRefBefore=",
            takePath()
        )
        assertEquals(
            "/bouqueteditor/web/addmarkertobouquet?$bouquet&Name=News&sRefBefore=$SERVICE_ENCODED",
            takePath()
        )
    }

    @Test
    fun autoTimerCallsSendTheirIds() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("")) }

        api.testAutoTimer(AutoTimerId(2))
        api.removeAutoTimer(AutoTimerId(2))
        api.runAutoTimers()

        assertEquals("/autotimer/test?id=2", takePath())
        assertEquals("/autotimer/remove?id=2", takePath())
        assertEquals("/autotimer/parse?", takePath())
    }

    @Test
    fun saveAutoTimerSendsTheChangedGroupsToEdit() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("")) }
        val loaded = (
            AutoTimerListParser.parse(loadWebFixture("autotimer/list_enabled.xml"))!!
                .entries.single() as AutoTimerEntry.Readable
            ).autoTimer

        api.saveAutoTimer(AutoTimerWrite.Change(loaded, loaded.settings.copy(enabled = false)))
        api.saveAutoTimer(
            AutoTimerWrite.Change(loaded, loaded.settings.copy(match = "50%20 & x"))
        )
        api.saveAutoTimer(
            AutoTimerWrite.Create(
                AutoTimerSettings.NEW,
                AutoTimerSettings.NEW.copy(match = "Wilsberg", name = "W")
            )
        )

        val name = "name=dreamDroid%20test%20Wilsberg"
        assertEquals("/autotimer/edit?id=2&match=Wilsberg&$name&enabled=0", takePath())
        assertEquals("/autotimer/edit?id=2&match=50%252520%20%26%20x&$name", takePath())
        assertEquals("/autotimer/edit?match=Wilsberg&name=W", takePath())
    }

    private fun urlClient(configure: Profile.() -> Unit = {}) = DreamboxWebIfApi(
        EnigmaHttp(
            Profile().apply {
                host = "box.local"
                port = 80
                streamPort = 8001
                filePort = 8080
                login = true
                user = "root"
                pass = "secret"
                streamLogin = true
                fileLogin = true
                configure()
            },
            EnigmaOkHttp(),
            WebIfCapabilitiesRepository()
        ),
        WebIfCapabilities()
    )

    private fun takeUrl(): HttpUrl = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!

    private fun takePath(): String = server.takeRequest(5, TimeUnit.SECONDS)!!.path!!

    private companion object {
        const val BOUQUET = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\""
        const val PROVIDERS = "1:0:1:0:0:0:0:0:0:0:FROM PROVIDERS"
        const val SERVICE = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
        const val START = 1_700_000_000L
        const val SERVICE_ENCODED = "1%3A0%3A19%3A2B66%3A3F3%3A1%3AC00000%3A0%3A0%3A0%3A"
        const val RECORDING_PATH = "/media/hdd/movie/a b.ts"
        const val RECORDING_REF = "1:0:0:0:0:0:0:0:0:0:$RECORDING_PATH"
        const val RECORDING_ENCODED = "%2Fmedia%2Fhdd%2Fmovie%2Fa%20b.ts"
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val FAVOURITES_ENCODED = "1%3A7%3A1%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3AFROM%20BOUQUET" +
            "%20%22userbouquet.favourites.tv%22%20ORDER%20BY%20bouquet"
        const val ERSTE = "1:0:19:283D:3FB:1:C00000:0:0:0:"
        const val ERSTE_ENCODED = "1%3A0%3A19%3A283D%3A3FB%3A1%3AC00000%3A0%3A0%3A0%3A"

        /** Lists the AutoTimer's web page, which is not the plugin's API. */
        const val EXTERNALS = "<e2webifexternals>" +
            "<e2webifexternal><e2path>autotimereditor</e2path></e2webifexternal>" +
            "<e2webifexternal><e2path>bouqueteditor</e2path></e2webifexternal>" +
            "</e2webifexternals>"

        /** oe-alliance's AutoTimer under the Dreambox web interface. */
        const val EXTERNALS_AUTOTIMER_17 = "<e2webifexternals><e2webifexternal>" +
            "<e2path>autotimer</e2path><e2externalversion>1.7</e2externalversion>" +
            "</e2webifexternal></e2webifexternals>"

        /** `/grab` writes to `/tmp/dreamDroid-<unix seconds>`. */
        val GRAB = Regex("/grab\\?format=jpg&filename=%2Ftmp%2FdreamDroid-\\d+")
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        val TIMER = Timer(
            reference = SERVICE,
            begin = "1700000000",
            end = "1700003600",
            name = "Tagesschau & Wetter",
            description = "Nachrichten",
            location = "/hdd/movie/",
            tags = "news kids",
            eit = "4711",
            disabled = "0",
            justPlay = "0",
            afterEvent = "3",
            repeated = "0"
        )

        /** The timer fields `/web/timerchange` gets for [TIMER], in the order they are sent. */
        const val TIMER_QUERY = "sRef=$SERVICE_ENCODED&begin=1700000000&end=1700003600" +
            "&name=Tagesschau%20%26%20Wetter&description=Nachrichten&dirname=%2Fhdd%2Fmovie%2F" +
            "&tags=news%20kids&eit=4711&disabled=0&justplay=0&afterevent=3&repeated=0"
    }
}
