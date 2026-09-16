package net.reichholf.dreamdroid.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
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
}
