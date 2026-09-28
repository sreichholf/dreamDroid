package net.reichholf.dreamdroid.testutil

import java.util.concurrent.TimeUnit
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * A [MockWebServer] receiver as the active profile, an in-memory database, and the real
 * [MovieRepository] and [ReceiverRepository] over both. The session starts Online.
 */
class MovieTestReceiver {
    val server = MockWebServer()
    val profiles = TestProfiles()
    val sessions = SessionConnectionHolder()
    private val clients = EnigmaClientFactory(profiles.repository)
    val movies = MovieRepository(
        profiles.context,
        clients,
        profiles.repository,
        profiles.database
    )
    val receiver = ReceiverRepository(clients, profiles.repository)

    /**
     * Answer for every request. Defaults to the two-movie `movielist.xml` for the movie list
     * and a `True` simple result for everything else.
     */
    @Volatile
    var answer: (RecordedRequest) -> MockResponse = { request ->
        if (request.requestUrl?.encodedPath == "/web/movielist") {
            MockResponse().setBody(loadWebFixture("movielist.xml"))
        } else {
            simpleResult("True", "Done")
        }
    }

    fun start(login: Boolean = false) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answer(request)
        }
        server.start()
        // EnigmaHttp still reads ProfileRepository.get() for the XML dump flag.
        ProfileRepository.install(profiles.repository)
        useProfile(PROFILE_ID, login)
        sessions.onSuccess()
    }

    /** Makes a profile with [id] on this server the active one. */
    fun useProfile(id: Int, login: Boolean = false) {
        profiles.repository.setCurrent(
            Profile().apply {
                this.id = id
                name = "test"
                host = server.hostName
                port = server.port
                this.login = login
                if (login) {
                    user = "root"
                    pass = "secret"
                }
            }
        )
    }

    fun stop() {
        server.shutdown()
        profiles.repository.clearCurrent()
    }

    fun goOffline() {
        sessions.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )
    }

    /** The next [count] requests, oldest first, waiting for each. */
    fun takeRequests(count: Int): List<RecordedRequest> = List(count) {
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request ${it + 1}" }
    }

    companion object {
        const val PROFILE_ID = 7
        const val HDD = "/media/hdd/movie"
        const val USB = "/media/usb/movie"

        fun simpleResult(state: String, text: String): MockResponse = MockResponse().setBody(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2simplexmlresult>
                <e2state>$state</e2state>
                <e2statetext>$text</e2statetext>
            </e2simplexmlresult>
            """.trimIndent()
        )

        fun movieList(vararg titles: String): MockResponse = MockResponse().setBody(
            buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<e2movielist>\n")
                for (title in titles) {
                    append("<e2movie><e2servicereference>1:0:0:0:0:0:0:0:0:0:/media/hdd/movie/")
                    append(title).append(".ts</e2servicereference><e2title>").append(title)
                    append("</e2title><e2filename>/media/hdd/movie/").append(title)
                    append(".ts</e2filename></e2movie>\n")
                }
                append("</e2movielist>")
            }
        )
    }
}
