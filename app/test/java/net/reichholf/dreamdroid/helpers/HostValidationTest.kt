package net.reichholf.dreamdroid.helpers

import net.reichholf.dreamdroid.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostValidationTest {
    @Test
    fun plainHostsAreValid() {
        assertTrue(isValidHost("10.0.0.1"))
        assertTrue(isValidHost("receiver"))
        assertTrue(isValidHost("box.local"))
        assertTrue(isValidHost("[fe80::1]"))
    }

    @Test
    fun userInfoAndUrlDelimitersAreRejected() {
        listOf(
            "receiver.local@attacker.example",
            "receiver/attacker",
            "receiver?probe=1",
            "receiver#fragment",
            "receiver\\attacker"
        ).forEach { assertFalse(isValidHost(it), it) }
    }

    @Test
    fun blankAndWhitespaceAreRejected() {
        assertFalse(isValidHost(null))
        assertFalse(isValidHost(""))
        assertFalse(isValidHost("   "))
        assertFalse(isValidHost(" 10.0.0.1"))
        assertFalse(isValidHost("10.0.0.1 "))
        assertFalse(isValidHost("10.0 0.1"))
    }

    @Test
    fun aProfileIsValidWhileBothHostsAreUsable() {
        val profile = Profile.getDefault().apply { host = "10.0.0.2" }

        assertNull(profile.invalidHost())

        profile.streamHost = "10.0.0.3"
        assertNull(profile.invalidHost())
    }

    @Test
    fun theHostComesBeforeTheStreamHost() {
        val profile = Profile.getDefault().apply {
            host = "receiver.local@attacker.example"
            streamHost = "stream.local/path"
        }

        assertEquals(
            InvalidHost(HostField.Host, "receiver.local@attacker.example"),
            profile.invalidHost()
        )
    }

    @Test
    fun aBlankHostIsReportedAsTheHostField() {
        val profile = Profile.getDefault().apply { host = " " }

        assertEquals(InvalidHost(HostField.Host, " "), profile.invalidHost())
    }

    @Test
    fun anIllegalStreamHostIsReportedAsTheStreamField() {
        val profile = Profile.getDefault().apply {
            host = "10.0.0.2"
            streamHost = "stream.local/path"
        }

        assertEquals(InvalidHost(HostField.StreamHost, "stream.local/path"), profile.invalidHost())
    }

    @Test
    fun aLegacyUserInfoHostBecomesTheUserAndAHost() {
        val profile = Profile.getDefault().apply {
            host = "upuser@10.0.0.2"
            user = ""
        }

        assertTrue(profile.splitLegacyUserInfo())

        assertEquals("10.0.0.2", profile.host)
        assertEquals("upuser", profile.user)
        assertTrue(isValidHost(profile.host))
    }

    @Test
    fun aLegacyUserInfoStreamHostIsSplitToo() {
        val profile = Profile.getDefault().apply {
            host = "10.0.0.2"
            streamHost = "upuser@10.0.0.3"
            user = ""
        }

        assertTrue(profile.splitLegacyUserInfo())

        assertEquals("10.0.0.2", profile.host)
        assertEquals("10.0.0.3", profile.streamHost)
        assertEquals("upuser", profile.user)
    }

    @Test
    fun anExplicitUserWinsOverTheLegacyPrefix() {
        val profile = Profile.getDefault().apply { host = "someone@10.0.0.2" }

        assertTrue(profile.splitLegacyUserInfo())

        assertEquals("10.0.0.2", profile.host)
        assertEquals("root", profile.user)
    }

    @Test
    fun aHostWithoutUserInfoIsLeftAlone() {
        val profile = Profile.getDefault().apply { host = "10.0.0.2" }

        assertFalse(profile.splitLegacyUserInfo())

        assertEquals("10.0.0.2", profile.host)
        assertEquals("", profile.streamHost)
    }

    @Test
    fun credentialsInTheLegacyPrefixAreLeftRejected() {
        val profile = Profile.getDefault().apply { host = "user:pass@10.0.0.2" }

        assertFalse(profile.splitLegacyUserInfo())

        assertEquals("user:pass@10.0.0.2", profile.host)
        assertFalse(isValidHost(profile.host))
    }
}
