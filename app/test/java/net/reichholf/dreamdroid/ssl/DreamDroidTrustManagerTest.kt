package net.reichholf.dreamdroid.ssl

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
}
