package net.reichholf.dreamdroid.helpers

import org.junit.jupiter.api.Assertions.assertFalse
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
}
