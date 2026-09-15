package net.reichholf.dreamdroid.ssl

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DreamDroidTrustManagerTest {
    @Test
    fun trustAllIsBoundAtConstructionNotLiveProfile() {
        val trusting = DreamDroidTrustManager(null, trustAll = true)
        val strict = DreamDroidTrustManager(null, trustAll = false)
        assertTrue(trusting.trustAllCertificates())
        assertFalse(strict.trustAllCertificates())
    }

    @Test
    fun acceptedIssuersEmptyWhenTrustAll() {
        val trusting = DreamDroidTrustManager(null, trustAll = true)
        assertEquals(0, trusting.acceptedIssuers.size)
    }

    @Test
    fun acceptedIssuersDelegatesWhenStrict() {
        val strict = DreamDroidTrustManager(null, trustAll = false)
        val defaultTm = strict.getDefaultTrustManager()
        if (defaultTm != null) {
            assertArrayEquals(defaultTm.acceptedIssuers, strict.acceptedIssuers)
        } else {
            assertEquals(0, strict.acceptedIssuers.size)
        }
    }
}
