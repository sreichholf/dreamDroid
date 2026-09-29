package net.reichholf.dreamdroid.testutil

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * A [MockWebServer] receiver that is the current profile of [profiles]. It answers each
 * request path with what [respond] set for it, and 404 otherwise.
 */
class TestReceiver(val profiles: TestProfiles = TestProfiles()) {
    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, MockResponse>()
    private val recorded = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    val repository: ProfileRepository
        get() = profiles.repository

    /** Requests so far, oldest first. */
    val requests: List<RecordedRequest>
        get() = synchronized(recorded) { recorded.toList() }

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                return routes[request.requestUrl?.encodedPath]
                    ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        // EnigmaHttp still reads ProfileRepository.get() for the XML dump flag.
        ProfileRepository.install(repository)
        repository.setCurrent(
            Profile().apply {
                id = PROFILE_ID
                name = "test"
                host = server.hostName
                port = server.port
            }
        )
    }

    fun shutdown() {
        server.shutdown()
        repository.clearCurrent()
    }

    fun respond(path: String, body: String) {
        routes[path] = MockResponse().setBody(body)
    }

    fun respond(path: String, response: MockResponse) {
        routes[path] = response
    }

    fun fail(path: String, code: Int = 500) {
        routes[path] = MockResponse().setResponseCode(code)
    }

    /** The requests to [path]. */
    fun requestsTo(path: String): List<RecordedRequest> =
        requests.filter { it.requestUrl?.encodedPath == path }

    fun timerRepository(): TimerRepository =
        TimerRepository(EnigmaClientFactory(repository), repository, profiles.database)

    companion object {
        const val PROFILE_ID = 7

        const val TIMER_LIST = "/web/timerlist"
        const val TIMER_ADD_BY_EVENT_ID = "/web/timeraddbyeventid"
        const val TIMER_CHANGE = "/web/timerchange"
        const val TIMER_DELETE = "/web/timerdelete"
        const val TIMER_CLEANUP = "/web/timercleanup"
        const val LOCATIONS = "/web/getlocations"
        const val TAGS = "/web/gettags"

        fun simpleResult(state: Boolean, text: String): String =
            "<e2simplexmlresult><e2state>${if (state) "True" else "False"}</e2state>" +
                "<e2statetext>$text</e2statetext></e2simplexmlresult>"
    }
}
