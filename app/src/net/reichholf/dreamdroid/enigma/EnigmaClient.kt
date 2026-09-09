package net.reichholf.dreamdroid.enigma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import java.util.ArrayList

class EnigmaClient(private val http: SimpleHttpClient) {
    suspend fun getServices(params: List<NameValuePair> = emptyList()): List<Service> {
        return withContext(Dispatchers.IO) {
            val requestParams = ArrayList(params)
            if (!http.fetchPageContent(URIStore.SERVICES, requestParams)) {
                emptyList()
            } else {
                ServiceParser.parse(http.pageContentString)
            }
        }
    }

    suspend fun getEvents(
        params: List<NameValuePair> = emptyList(),
        uri: String = URIStore.EPG_SERVICE
    ): List<Event> {
        return withContext(Dispatchers.IO) {
            val requestParams = ArrayList(params)
            if (!http.fetchPageContent(uri, requestParams)) {
                emptyList()
            } else {
                EventParser.parse(http.pageContentString)
            }
        }
    }

    suspend fun getEpgNowNext(
        params: List<NameValuePair> = emptyList(),
        uri: String = URIStore.EPG_NOWNEXT
    ): List<ServiceNowNext> {
        return withContext(Dispatchers.IO) {
            val requestParams = ArrayList(params)
            if (!http.fetchPageContent(uri, requestParams)) {
                emptyList()
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
                            next = null,
                        )
                    }
                }
            }
        }
    }

    suspend fun getCurrent(): CurrentService? {
        return withContext(Dispatchers.IO) {
            if (!http.fetchPageContent(URIStore.CURRENT, ArrayList())) {
                null
            } else {
                CurrentServiceParser.parse(http.pageContentString)
            }
        }
    }

    suspend fun getDeviceInfo(): DeviceInfo? {
        return withContext(Dispatchers.IO) {
            if (!http.fetchPageContent(URIStore.DEVICE_INFO, ArrayList())) {
                null
            } else {
                DeviceInfoParser.parse(http.pageContentString)
            }
        }
    }

    suspend fun getSignal(): Signal? {
        return withContext(Dispatchers.IO) {
            if (!http.fetchPageContent(URIStore.SIGNAL, ArrayList())) {
                null
            } else {
                SignalParser.parse(http.pageContentString)
            }
        }
    }

    suspend fun getTimers(): List<Timer>? {
        return withContext(Dispatchers.IO) {
            if (!http.fetchPageContent(URIStore.TIMER_LIST, ArrayList())) {
                null
            } else {
                TimerParser.parse(http.pageContentString)
            }
        }
    }

    suspend fun getMovies(params: List<NameValuePair> = emptyList()): List<Movie>? {
        return withContext(Dispatchers.IO) {
            val requestParams = ArrayList(params)
            if (!http.fetchPageContent(URIStore.MOVIES, requestParams)) {
                null
            } else {
                MovieParser.parse(http.pageContentString)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getServicesBlocking(http: SimpleHttpClient, params: List<NameValuePair>): List<Service> {
            return runBlocking {
                EnigmaClient(http).getServices(params)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun getEventsBlocking(
            http: SimpleHttpClient,
            params: List<NameValuePair>,
            uri: String = URIStore.EPG_SERVICE
        ): List<Event> {
            return runBlocking {
                EnigmaClient(http).getEvents(params, uri)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun getEpgNowNextBlocking(
            http: SimpleHttpClient,
            params: List<NameValuePair>,
            uri: String = URIStore.EPG_NOWNEXT
        ): List<ServiceNowNext> {
            return runBlocking {
                EnigmaClient(http).getEpgNowNext(params, uri)
            }
        }

        @JvmStatic
        fun getTimersBlocking(http: SimpleHttpClient): List<Timer>? {
            return runBlocking {
                EnigmaClient(http).getTimers()
            }
        }

        @JvmStatic
        fun getMoviesBlocking(http: SimpleHttpClient, params: List<NameValuePair>): List<Movie>? {
            return runBlocking {
                EnigmaClient(http).getMovies(params)
            }
        }
    }
}
