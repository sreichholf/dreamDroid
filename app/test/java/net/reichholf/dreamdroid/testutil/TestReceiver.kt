package net.reichholf.dreamdroid.testutil

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.TimerRepository
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
    private val once = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()
    private val holds = ConcurrentHashMap<String, Hold>()
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
                val path = request.requestUrl?.encodedPath
                holds[path]?.let { hold ->
                    hold.arrived.countDown()
                    hold.released.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
                return once[path]?.poll() ?: routes[path] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
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
        holds.values.forEach(Hold::release)
        server.shutdown()
        repository.clearCurrent()
    }

    fun respond(path: String, body: String) {
        routes[path] = MockResponse().setBody(body)
    }

    fun respond(path: String, response: MockResponse) {
        routes[path] = response
    }

    /** Answers the next request to [path] with [body]; later ones get what [respond] set. */
    fun respondOnce(path: String, body: String) {
        once.getOrPut(path) { ConcurrentLinkedQueue() }.add(MockResponse().setBody(body))
    }

    /**
     * Holds the next answers to [path] until [Hold.release]. [Hold.arrived] opens once a
     * request to [path] reached the receiver.
     */
    fun hold(path: String): Hold = Hold().also { holds[path] = it }

    fun fail(path: String, code: Int = 500) {
        routes[path] = MockResponse().setResponseCode(code)
    }

    /** The requests to [path]. */
    fun requestsTo(path: String): List<RecordedRequest> =
        requests.filter { it.requestUrl?.encodedPath == path }

    fun timerRepository(): TimerRepository =
        TimerRepository(receiverApis(repository), repository, profiles.database)

    class Hold {
        val arrived = CountDownLatch(1)
        internal val released = CountDownLatch(1)

        fun release() {
            released.countDown()
        }
    }

    companion object {
        private const val HOLD_TIMEOUT_SECONDS = 30L

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
