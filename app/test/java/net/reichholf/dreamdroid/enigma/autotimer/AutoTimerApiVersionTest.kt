package net.reichholf.dreamdroid.enigma.autotimer

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadOwifFixture
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The AutoTimer plugin's two APIs, opendreambox's 1.6 and oe-alliance's 1.7, through the one
 * [AutoTimerPluginApi] both web interfaces use. The 1.7 fixtures follow oe-alliance's
 * `AutoTimerResource.py`; see the README under `test/resources/owif`.
 */
class AutoTimerApiVersionTest {
    private val server = MockWebServer()

    private val api by lazy {
        val http = EnigmaHttp(
            Profile().apply {
                host = server.hostName
                port = server.port
            },
            EnigmaOkHttp(),
            WebIfCapabilitiesRepository()
        )
        AutoTimerPluginApi(http::fetch)
    }

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun apiVersionsFromSevenOnAreTheOeAllianceApi() {
        assertEquals(AutoTimerApi.V1_6, AutoTimerApi.of("1.6"))
        assertEquals(AutoTimerApi.V1_6, AutoTimerApi.of("1.0"))
        assertEquals(AutoTimerApi.V1_7, AutoTimerApi.of("1.7"))
        assertEquals(AutoTimerApi.V1_7, AutoTimerApi.of(" 1.10 "))
        assertEquals(AutoTimerApi.V1_7, AutoTimerApi.of("2"))
        assertEquals(AutoTimerApi.V1_6, AutoTimerApi.of(""))
        assertEquals(AutoTimerApi.V1_6, AutoTimerApi.of(null))
        assertEquals(AutoTimerApi.V1_6, AutoTimerApi.of("x.y"))
        assertTrue(AutoTimerApi.V1_6.runAnswersInTime)
        assertFalse(AutoTimerApi.V1_7.runAnswersInTime)
    }

    @Test
    fun settingsGiveTheApiAndA404AMissingPlugin() = runBlocking {
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/get_16.xml")))
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/get_17.xml")))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("<html>AutoTimer</html>"))

        assertEquals(AutoTimerPlugin.Installed(AutoTimerApi.V1_6), api.plugin().value)
        assertEquals(AutoTimerPlugin.Installed(AutoTimerApi.V1_7), api.plugin().value)
        assertEquals(AutoTimerPlugin.Missing, api.plugin().value)
        assertEquals(EnigmaFailure.Parse, api.plugin().error?.failure)
        assertEquals("/autotimer/get?", takePath())
    }

    @Test
    fun aV17ListReadsZapAndRecordAndTitleStart() {
        val entries = AutoTimerListParser.parse(loadOwifFixture("autotimer/list_17.xml"))!!
            .entries.map { (it as AutoTimerEntry.Readable).autoTimer }

        val tatort = entries[0]
        assertEquals(SearchType.Start, tatort.settings.searchType)
        assertEquals(RecordMode.Record, tatort.settings.recordMode)
        assertTrue(tatort.extras.alwaysZap)
        assertEquals(RecordMode.Zap(setEndTime = true), entries[1].settings.recordMode)
        assertFalse(entries[1].extras.alwaysZap)
    }

    /** 1.7 resets `always_zap` when it is missing; 1.6 lists never carry it. */
    @Test
    fun anEditKeepsZapAndRecordOnlyWhileItRecords() = runBlocking {
        repeat(4) { server.enqueue(MockResponse().setBody("")) }
        val tatort = v17(0)
        val wilsberg = (
            AutoTimerListParser.parse(loadWebFixture("autotimer/list_enabled.xml"))!!
                .entries.single() as AutoTimerEntry.Readable
            ).autoTimer

        api.edit(AutoTimerWrite.Change(tatort, tatort.settings.copy(enabled = false)))
        api.edit(
            AutoTimerWrite.Change(
                tatort,
                tatort.settings.copy(recordMode = RecordMode.Zap(setEndTime = true))
            )
        )
        api.edit(AutoTimerWrite.Change(wilsberg, wilsberg.settings.copy(enabled = false)))
        api.edit(
            AutoTimerWrite.Create(
                AutoTimerSettings.NEW,
                AutoTimerSettings.NEW.copy(match = "Tatort", searchType = SearchType.Start)
            )
        )

        assertEquals(
            "/autotimer/edit?id=1&match=Tatort&name=Tatort&enabled=0&always_zap=1",
            takePath()
        )
        assertEquals(
            "/autotimer/edit?id=1&match=Tatort&name=Tatort&justplay=1&setEndtime=1",
            takePath()
        )
        assertEquals(
            "/autotimer/edit?id=2&match=Wilsberg&name=dreamDroid%20test%20Wilsberg&enabled=0",
            takePath()
        )
        assertEquals(
            "/autotimer/edit?match=Tatort&name=&searchType=start&searchCase=insensitive",
            takePath()
        )
    }

    @Test
    fun aV17EditAnswersWithTheIdItWrote() = runBlocking {
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/edit_17.xml")))
        server.enqueue(MockResponse().setBody(loadWebFixture("autotimer/result_add.xml")))
        val create = AutoTimerWrite.Create(
            AutoTimerSettings.NEW,
            AutoTimerSettings.NEW.copy(match = "Tatort")
        )

        val v17 = api.edit(create).value!!
        val v16 = api.edit(create).value!!

        assertEquals("3", v17.id)
        assertEquals("AutoTimer was added successfully", v17.stateText)
        assertEquals(null, v16.id)
    }

    @Test
    fun theSwitchAloneGoesToChange() = runBlocking {
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/change_17.xml")))

        val changed = api.change(AutoTimerId(1), enabled = true).value!!

        assertEquals("/autotimer/change?id=1&enabled=1", takePath())
        assertEquals("AutoTimer was changed successfully", changed.stateText)
        assertEquals(null, changed.id)
    }

    private fun v17(index: Int): AutoTimer = (
        AutoTimerListParser.parse(loadOwifFixture("autotimer/list_17.xml"))!!
            .entries[index] as AutoTimerEntry.Readable
        ).autoTimer

    private fun takePath(): String = server.takeRequest(5, TimeUnit.SECONDS)!!.path!!
}
