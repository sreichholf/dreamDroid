package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DodgeShellChromeBehaviorTest {
    @Test
    fun restMarginWhenChromeGone() {
        assertEquals(
            70,
            dodgedBottomMarginPx(
                chromeVisible = false,
                chromeHeightPx = 200,
                gapPx = 16,
                restMarginPx = 70
            )
        )
    }

    @Test
    fun gapPlusChromeWhenStripShown() {
        assertEquals(
            153,
            dodgedBottomMarginPx(
                chromeVisible = true,
                chromeHeightPx = 137,
                gapPx = 16,
                restMarginPx = 70
            )
        )
    }

    @Test
    fun zeroHeightVisibleChromeUsesRestUntilLaidOut() {
        assertEquals(
            70,
            dodgedBottomMarginPx(
                chromeVisible = true,
                chromeHeightPx = 0,
                gapPx = 16,
                restMarginPx = 70
            )
        )
    }
}
