package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNavInsetsTest {
    @Test
    fun hubAndToolsShowShellBar() {
        assertTrue(PhoneNavRoutes.showsShellDestinationBar(PhoneNavRoutes.HUB))
        assertTrue(PhoneNavRoutes.showsShellDestinationBar(PhoneNavRoutes.TOOLS))
    }

    @Test
    fun timerAndProfileEditHideShellBar() {
        assertFalse(PhoneNavRoutes.showsShellDestinationBar(PhoneNavRoutes.TIMER_EDIT))
        assertFalse(PhoneNavRoutes.showsShellDestinationBar(PhoneNavRoutes.PROFILE_EDIT))
        assertFalse(PhoneNavRoutes.showsShellDestinationBar(null))
    }

    @Test
    fun overflowIncludesNavBarAndScrollingBehaviorExtension() {
        // View starts below the app bar and extends past the window by 56px, nav 48px.
        assertEquals(
            204,
            phoneNavBottomOverflowPx(
                viewTopInWindow = 100,
                viewHeight = 856,
                windowHeight = 800,
                safeBottomInset = 48
            )
        )
    }

    @Test
    fun noOverflowWhenViewEndsAboveSafeArea() {
        assertEquals(
            0,
            phoneNavBottomOverflowPx(
                viewTopInWindow = 0,
                viewHeight = 700,
                windowHeight = 800,
                safeBottomInset = 48
            )
        )
    }
}
