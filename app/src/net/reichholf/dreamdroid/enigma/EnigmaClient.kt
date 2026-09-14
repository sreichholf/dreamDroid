package net.reichholf.dreamdroid.enigma

import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

class EnigmaClient(private val http: SimpleHttpClient) {
    suspend fun getServices(params: List<NameValuePair> = emptyList()): List<Service>? =
        withContext(Dispatchers.IO) {
            val requestParams = ArrayList(params)
            if (!http.fetchPageContent(URIStore.SERVICES, requestParams)) {
                null
            } else {
                ServiceParser.parse(http.pageContentString)
            }
        }

    suspend fun getEvents(
        params: List<NameValuePair> = emptyList(),
        uri: String = URIStore.EPG_SERVICE
    ): List<Event>? = withContext(Dispatchers.IO) {
        val requestParams = ArrayList(params)
        if (!http.fetchPageContent(uri, requestParams)) {
            null
        } else {
            EventParser.parse(http.pageContentString)
        }
    }

    suspend fun getEpgNowNext(
        params: List<NameValuePair> = emptyList(),
        uri: String = URIStore.EPG_NOWNEXT
    ): List<ServiceNowNext>? = withContext(Dispatchers.IO) {
        val requestParams = ArrayList(params)
        if (!http.fetchPageContent(uri, requestParams)) {
            null
        } else {
            val xml = http.pageContentString
            if (uri == URIStore.EPG_NOWNEXT) {
                EpgNowNextParser.parse(xml)
            } else {
                // /web/epgnow (and other flat event lists): one service row per event, no pairing.
                EventParser.parse(xml).map { event ->
                    ServiceNowNext(
                        serviceReference = event.serviceReference,
                        serviceName = event.serviceName,
                        now = event,
                        next = null
                    )
                }
            }
        }
    }

    suspend fun getCurrent(): CurrentService? = withContext(Dispatchers.IO) {
        if (!http.fetchPageContent(URIStore.CURRENT, ArrayList())) {
            null
        } else {
            CurrentServiceParser.parse(http.pageContentString)
        }
    }

    suspend fun getDeviceInfo(): DeviceInfo? = withContext(Dispatchers.IO) {
        if (!http.fetchPageContent(URIStore.DEVICE_INFO, ArrayList())) {
            null
        } else {
            DeviceInfoParser.parse(http.pageContentString)
        }
    }

    suspend fun getSignal(): Signal? = withContext(Dispatchers.IO) {
        if (!http.fetchPageContent(URIStore.SIGNAL, ArrayList())) {
            null
        } else {
            SignalParser.parse(http.pageContentString)
        }
    }

    suspend fun getTimers(): List<Timer>? = withContext(Dispatchers.IO) {
        if (!http.fetchPageContent(URIStore.TIMER_LIST, ArrayList())) {
            null
        } else {
            TimerParser.parse(http.pageContentString)
        }
    }

    suspend fun getMovies(params: List<NameValuePair> = emptyList()): List<Movie>? =
        withContext(Dispatchers.IO) {
            val requestParams = ArrayList(params)
            if (!http.fetchPageContent(URIStore.MOVIES, requestParams)) {
                null
            } else {
                MovieParser.parse(http.pageContentString)
            }
        }
}
