package net.reichholf.dreamdroid.ui.screenshot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenshotGrabGateTest {
    @Test
    fun emptyBytesGrab() {
        assertTrue(shouldGrabScreenshot(0))
    }

    @Test
    fun nonEmptyBytesDoNotGrab() {
        assertFalse(shouldGrabScreenshot(1))
        assertFalse(shouldGrabScreenshot(128))
    }
}
