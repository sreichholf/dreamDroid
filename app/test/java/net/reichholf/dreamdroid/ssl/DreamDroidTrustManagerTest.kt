package net.reichholf.dreamdroid.ssl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
