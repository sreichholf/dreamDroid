package net.reichholf.dreamdroid.enigma

import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

class EnigmaClient(private val http: EnigmaHttp = EnigmaHttp()) {
    constructor(profile: Profile) : this(EnigmaHttp(profile))

    suspend fun getServices(
        params: List<NameValuePair> = emptyList()
    ): EnigmaResponse<List<Service>> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.SERVICES, ArrayList(params)).mapParsed { xml ->
            ServiceParser.parse(xml)
        }
    }

    suspend fun getEvents(
        params: List<NameValuePair> = emptyList(),
        uri: String = URIStore.EPG_SERVICE
    ): EnigmaResponse<List<Event>> = withContext(Dispatchers.IO) {
        http.fetch(uri, ArrayList(params)).mapParsed { xml ->
            EventParser.parse(xml)
        }
    }

    suspend fun getEpgNowNext(
        params: List<NameValuePair> = emptyList(),
        uri: String = URIStore.EPG_NOWNEXT
    ): EnigmaResponse<List<ServiceNowNext>> = withContext(Dispatchers.IO) {
        http.fetch(uri, ArrayList(params)).mapParsed { xml ->
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

    suspend fun getCurrent(): EnigmaResponse<CurrentService> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.CURRENT).mapParsed { xml ->
            CurrentServiceParser.parse(xml)
        }
    }

    suspend fun getDeviceInfo(): EnigmaResponse<DeviceInfo> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.DEVICE_INFO).mapParsed { xml ->
            DeviceInfoParser.parse(xml)
        }
    }

    suspend fun getSignal(): EnigmaResponse<Signal> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.SIGNAL).mapParsed { xml ->
            SignalParser.parse(xml)
        }
    }

    suspend fun getTimers(): EnigmaResponse<List<Timer>> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.TIMER_LIST).mapParsed { xml ->
            TimerParser.parse(xml)
        }
    }

    suspend fun getMovies(params: List<NameValuePair> = emptyList()): EnigmaResponse<List<Movie>> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.MOVIES, ArrayList(params)).mapParsed { xml ->
                MovieParser.parse(xml)
            }
        }

    suspend fun setVolume(params: List<NameValuePair>): EnigmaResponse<Volume> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.VOLUME, params).mapParsed { xml ->
                VolumeParser.parse(xml)
            }
        }

    suspend fun setPowerState(params: List<NameValuePair>): EnigmaResponse<PowerState> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.POWERSTATE, params).mapParsed { xml ->
                PowerStateParser.parse(xml)
            }
        }

    suspend fun sleepTimer(params: List<NameValuePair>): EnigmaResponse<SleepTimer> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.SLEEPTIMER, params).mapParsed { xml ->
                SleepTimerParser.parse(xml)
            }
        }

    suspend fun zap(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.ZAP, params)

    suspend fun remoteCommand(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.REMOTECONTROL, params)

    suspend fun sendMessage(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.MESSAGE, params)

    suspend fun playMedia(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.MEDIA_PLAYER_PLAY, params)

    suspend fun deleteMovie(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.MOVIE_DELETE, params)

    suspend fun addTimerByEventId(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.TIMER_ADD_BY_EVENT_ID, params)

    suspend fun changeTimer(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.TIMER_CHANGE, params)

    suspend fun deleteTimer(params: List<NameValuePair>): SimpleResultResponse =
        simpleResult(URIStore.TIMER_DELETE, params)

    suspend fun cleanupTimers(): SimpleResultResponse = simpleResult(URIStore.TIMER_CLEANUP)

    private suspend fun simpleResult(
        uri: String,
        params: List<NameValuePair> = emptyList()
    ): SimpleResultResponse = withContext(Dispatchers.IO) {
        simpleResultFromFetch(http.fetch(uri, params), SimpleResultParser::parse)
    }

    private fun <T> EnigmaHttpResult.mapParsed(parse: (String) -> T?): EnigmaResponse<T> =
        when (this) {
            is EnigmaHttpResult.Success -> EnigmaResponse(parse(text))
            is EnigmaHttpResult.Failure -> EnigmaResponse(null, error)
        }
}
