package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnigmaClientHttpFailTest {
    private lateinit var server: MockWebServer
    private var previousProfile: Profile? = null

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        previousProfile = DreamDroid.getCurrentProfile()
        DreamDroid.setCurrentProfile(profileForServer())
    }

    @After
    fun stopServer() {
        previousProfile?.let { DreamDroid.setCurrentProfile(it) }
        server.shutdown()
    }

    @Test
    fun getServices_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val services = EnigmaClient(clientForServer()).getServices()
        assertEquals(null, services)
    }

    @Test
    fun getServices_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val services = EnigmaClient(clientForServer()).getServices()
        assertEquals(emptyList<Service>(), services)
    }

    @Test
    fun getServices_200ReturnsFixtureNames() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("getservices.xml")))
        val services = EnigmaClient(clientForServer()).getServices()!!
        assertEquals(3, services.size)
        assertEquals("Favourites (TV)", services[0].name)
        assertEquals("Das Erste HD", services[1].name)
        assertEquals("--------", services[2].name)
    }

    @Test
    fun getEvents_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val events = EnigmaClient(clientForServer()).getEvents()
        assertEquals(null, events)
    }

    @Test
    fun getEvents_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val events = EnigmaClient(clientForServer()).getEvents()
        assertEquals(emptyList<Event>(), events)
    }

    @Test
    fun getEpgNowNext_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val rows = EnigmaClient(clientForServer()).getEpgNowNext()
        assertEquals(null, rows)
    }

    @Test
    fun getEpgNowNext_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val rows = EnigmaClient(clientForServer()).getEpgNowNext()
        assertEquals(emptyList<ServiceNowNext>(), rows)
    }

    @Test
    fun loadServiceList_httpFailIsNotEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val result = loadServiceList(appContext(), listOf(NameValuePair("sRef", "1:0:1")))
        assertEquals(false, result.success)
        assertEquals(emptyList<Service>(), result.services)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadServiceList_empty200IsSuccess() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val result = loadServiceList(appContext(), listOf(NameValuePair("sRef", "1:0:1")))
        assertEquals(true, result.success)
        assertEquals(emptyList<Service>(), result.services)
        assertEquals(null, result.errorText)
    }

    @Test
    fun loadEventList_httpFailIsNotEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val result = loadEventList(appContext(), emptyList(), URIStore.EPG_SERVICE)
        assertEquals(false, result.success)
        assertEquals(emptyList<Event>(), result.events)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadEpgNowNext_httpFailIsNotEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val result = loadEpgNowNext(appContext(), listOf(NameValuePair("bRef", "1:7:1")))
        assertEquals(false, result.success)
        assertEquals(emptyList<ServiceNowNext>(), result.rows)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadBouquetList_tvHttpFailIsFailureEvenIfRadioSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        server.enqueue(MockResponse().setBody(loadWebFixture("getservices.xml")))
        val result = loadBouquetList(appContext())
        assertEquals(false, result.success)
        assertEquals(0, result.bouquets.tv.size)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadBouquetList_emptyRootsAreSuccess() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))
        val result = loadBouquetList(appContext())
        assertEquals(true, result.success)
        assertEquals(0, result.bouquets.tv.size)
        assertEquals(0, result.bouquets.radio.size)
        assertEquals(null, result.errorText)
    }

    private fun appContext(): Context {
        return ApplicationProvider.getApplicationContext()
    }

    private fun contentError(httpMessage: String): String {
        return appContext().getString(R.string.get_content_error) + "\n" + httpMessage
    }

    private fun profileForServer(): Profile {
        return Profile().apply {
            host = "127.0.0.1"
            port = server.port
            ssl = false
            login = false
        }
    }

    private fun clientForServer(): SimpleHttpClient {
        return SimpleHttpClient.getInstance(profileForServer())
    }
}
