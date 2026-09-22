package net.reichholf.dreamdroid.helpers

import net.reichholf.dreamdroid.Profile
import okhttp3.Credentials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnigmaOkHttpTest {
    @Test
    fun client_sameKeyIsCached() {
        val first = EnigmaOkHttp.client(15_000, trustAll = false)
        val second = EnigmaOkHttp.client(15_000, trustAll = false)
        assertSame(first, second)
    }

    @Test
    fun client_timeoutAndTrustAllAreSeparateKeys() {
        val fifteen = EnigmaOkHttp.client(15_000, trustAll = false)
        val five = EnigmaOkHttp.client(5_000, trustAll = false)
        val trustAll = EnigmaOkHttp.client(15_000, trustAll = true)
        assertNotSame(fifteen, five)
        assertNotSame(fifteen, trustAll)
        assertNotSame(five, trustAll)
    }

    @Test
    fun client_appliesTimeoutsAndDisablesRedirects() {
        val client = EnigmaOkHttp.client(15_000, trustAll = false)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun client_sameTrustAllSharesPoolAndDispatcher() {
        val fifteen = EnigmaOkHttp.client(15_000, trustAll = false)
        val five = EnigmaOkHttp.client(5_000, trustAll = false)
        assertSame(fifteen.connectionPool, five.connectionPool)
        assertSame(fifteen.dispatcher, five.dispatcher)
    }

    @Test
    fun client_trustAllDoesNotSharePoolWithStrict() {
        val strict = EnigmaOkHttp.client(15_000, trustAll = false)
        val trustAll = EnigmaOkHttp.client(15_000, trustAll = true)
        assertNotSame(strict.connectionPool, trustAll.connectionPool)
        assertNotSame(strict.dispatcher, trustAll.dispatcher)
    }

    @Test
    fun piconClient_sharesPoolAndDispatcherWithMatchingApiClient() {
        val api = EnigmaOkHttp.client(15_000, trustAll = false)
        val picon = EnigmaOkHttp.piconClient(15_000, trustAll = false)
        assertSame(api.connectionPool, picon.connectionPool)
        assertSame(api.dispatcher, picon.dispatcher)
        assertNotSame(api, picon)
    }

    @Test
    fun piconClient_doesNotPolluteCachedApiClient() {
        EnigmaOkHttp.piconClient(15_000, trustAll = false)
        val api = EnigmaOkHttp.client(15_000, trustAll = false)
        assertTrue(api.interceptors.isEmpty())
    }

    @Test
    fun piconClient_addsAuthInterceptor() {
        val api = EnigmaOkHttp.client(15_000, trustAll = false)
        val picon = EnigmaOkHttp.piconClient(15_000, trustAll = false)
        assertTrue(picon.interceptors.size > api.interceptors.size)
    }

    @Test
    fun piconAuthHeader_matchesBasicCredentialsWhenLoginEnabled() {
        val profile = Profile().apply {
            login = true
            user = "root"
            pass = "secret"
        }
        assertEquals(
            Credentials.basic("root", "secret"),
            EnigmaOkHttp.piconAuthHeader(profile)
        )
    }

    @Test
    fun piconAuthHeader_nullWhenLoginDisabledOrMissingProfile() {
        val profile = Profile().apply {
            login = false
            user = "root"
            pass = "secret"
        }
        assertNull(EnigmaOkHttp.piconAuthHeader(profile))
        assertNull(EnigmaOkHttp.piconAuthHeader(null))
    }
}
