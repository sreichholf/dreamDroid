package net.reichholf.dreamdroid.testutil

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Puts the device back in touch mode and waits until [inputModeManager] reports it.
 *
 * Below API 33 touch mode is global, so a test that requested [InputMode.Keyboard] leaves the
 * next test on the device out of it; ui-test 1.12 does not reset it. Compose cannot request
 * [InputMode.Touch], and the rule's own touch input goes straight to Compose, not through the
 * system, so this injects a real tap at the screen's corner.
 */
fun ComposeTestRule.enterTouchMode(inputModeManager: InputModeManager) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val time = SystemClock.uptimeMillis()
    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
        val event = MotionEvent.obtain(time, time, action, 1f, 1f, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        automation.injectInputEvent(event, true)
        event.recycle()
    }
    waitUntil { inputModeManager.inputMode == InputMode.Touch }
}
