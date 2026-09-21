package net.reichholf.dreamdroid.video

import net.reichholf.dreamdroid.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZapAndStreamTest {
    @Test
    fun profileLeavesZapAndStreamOff() {
        val profile = Profile()
        assertFalse(profile.zapAndStream)
        assertFalse(ZapAndStream.required(profile))
    }

    @Test
    fun requiredFollowsTheProfileFlag() {
        val profile = Profile().apply { zapAndStream = true }
        assertTrue(ZapAndStream.required(profile))
    }

    @Test
    fun sameSettingsIncludesTheFlag() {
        val left = Profile().apply { zapAndStream = false }
        val right = Profile().apply { zapAndStream = true }
        assertFalse(left.hasSameSettings(right))
        right.zapAndStream = false
        assertTrue(left.hasSameSettings(right))
    }

    @Test
    fun failureTextPrefersTheBoxMessage() {
        assertEquals(
            "tune failed",
            zapThenStreamFailureText("tune failed", "network", "fallback")
        )
        assertEquals(
            "tune failed",
            zapThenStreamFailureText("  tune failed  ", "network", "fallback")
        )
    }

    @Test
    fun failureTextUsesResolvedErrorThenFallback() {
        assertEquals("network", zapThenStreamFailureText(null, "network", "fallback"))
        assertEquals("network", zapThenStreamFailureText("", "network", "fallback"))
        assertEquals("network", zapThenStreamFailureText("  ", " network ", "fallback"))
        assertEquals("fallback", zapThenStreamFailureText(null, null, "fallback"))
        assertEquals("fallback", zapThenStreamFailureText(" ", " ", "fallback"))
    }
}
