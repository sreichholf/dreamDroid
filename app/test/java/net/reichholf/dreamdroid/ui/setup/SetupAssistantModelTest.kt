package net.reichholf.dreamdroid.ui.setup

import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetupAssistantModelTest {
    @Test
    fun seededDemoMatchesEveryStoredField() {
        assertTrue(seededDemo().matchesSeededDemo())
    }

    @Test
    fun seededDemoRejectsADifferentHost() {
        val profile = seededDemo()
        profile.host = "192.168.1.2"
        assertFalse(profile.matchesSeededDemo())
    }

    @Test
    fun soleSeededDemoRequiresExactlyOneMatchingRow() {
        val demo = seededDemo()
        assertEquals(demo, soleSeededDemo(listOf(demo)))
        assertNull(soleSeededDemo(emptyList()))
        assertNull(soleSeededDemo(listOf(demo, seededDemo())))
        val real = seededDemo()
        real.pass = "secret"
        assertNull(soleSeededDemo(listOf(real)))
    }

    @Test
    fun wizardProfileKeepsOpenWebifDefaults() {
        val profile = wizardProfile(
            name = "",
            host = " 192.168.1.2 ",
            port = 80,
            useHttps = false,
            login = true,
            user = "root",
            pass = "dreambox",
            trustAllCerts = true
        )
        assertEquals("192.168.1.2", profile.name)
        assertEquals("192.168.1.2", profile.host)
        assertEquals(80, profile.port)
        assertEquals(8001, profile.streamPort)
        assertFalse(profile.ssl)
        assertTrue(profile.login)
        assertFalse(profile.allCertsTrusted)
        assertFalse(profile.zapAndStream)
    }

    @Test
    fun wizardProfileTrustsCertificatesOnlyForHttps() {
        val profile = wizardProfile(
            name = "Living room",
            host = "box.local",
            port = 443,
            useHttps = true,
            login = false,
            user = "root",
            pass = "dreambox",
            trustAllCerts = true
        )
        assertTrue(profile.ssl)
        assertTrue(profile.allCertsTrusted)
        assertFalse(profile.login)
    }

    @Test
    fun certificateFailureIsOnlyAnSslUnreachableResult() {
        val ssl = ProfileCheckResult(
            hasError = true,
            failure = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Ssl)
        )
        val dns = ProfileCheckResult(
            hasError = true,
            failure = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
        )
        assertTrue(ssl.isCertificateFailure())
        assertFalse(dns.isCertificateFailure())
        assertFalse(ProfileCheckResult().isCertificateFailure())
    }

    private fun seededDemo(): Profile = Profile(
        null,
        "Demo",
        "dreamdroid.org",
        "",
        443,
        8001,
        80,
        false,
        "root",
        "dreambox",
        true,
        false,
        false,
        false,
        false,
        "",
        "",
        "",
        ""
    )
}
