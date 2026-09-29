package net.reichholf.dreamdroid.helpers.enigma2

import java.util.concurrent.TimeUnit
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.TestProfiles
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PiconCallsTest {
    private val profiles = TestProfiles()
    private val okHttp = EnigmaOkHttp()
    private val calls = PiconCalls(profiles.repository, okHttp)

    @Test
    fun aProfileSwitchChangesTheTrustAllSetup() {
        val strict = profile("strict", trustAll = false)
        val trusting = profile("trusting", trustAll = true)

        profiles.repository.setCurrent(strict)
        val strictClient = calls.client()
        assertSame(sslOf(trustAll = false), strictClient.sslSocketFactory)

        profiles.repository.setCurrent(trusting)
        assertSame(sslOf(trustAll = true), calls.client().sslSocketFactory)

        profiles.repository.setCurrent(strict)
        assertSame(strictClient, calls.client())
    }

    @Test
    fun basicAuthFollowsTheCurrentProfile() {
        val server = MockWebServer()
        server.start()
        try {
            val login = profile("login", trustAll = false).apply {
                this.login = true
                user = "root"
                pass = "secret"
            }
            val open = profile("open", trustAll = false)
            val request = Request.Builder().url(server.url("/file")).build()
            repeat(2) { server.enqueue(MockResponse()) }

            profiles.repository.setCurrent(login)
            calls.newCall(request).execute().close()
            profiles.repository.setCurrent(open)
            calls.newCall(request).execute().close()

            assertEquals(
                Credentials.basic("root", "secret"),
                server.takeRequest(5, TimeUnit.SECONDS)?.getHeader("Authorization")
            )
            assertNull(server.takeRequest(5, TimeUnit.SECONDS)?.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    private fun sslOf(trustAll: Boolean) =
        okHttp.client(EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS, trustAll).sslSocketFactory

    private fun profile(name: String, trustAll: Boolean): Profile = Profile().apply {
        this.name = name
        host = "box.local"
        port = 443
        ssl = true
        allCertsTrusted = trustAll
    }.also { profiles.repository.save(it) }
}
