package net.reichholf.dreamdroid.tv.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HubTopBarLayoutTest {
    @Test
    fun tabsAreCentredOnTheBarWhenTheyClearTheTrailingGroup() {
        // Centred tabs end at 900, the trailing group starts at 1000.
        assertEquals(
            300,
            hubTabsStart(barWidth = 1200, tabsWidth = 600, trailingWidth = 200, minGap = 32)
        )
    }

    @Test
    fun tabsCentreWithExactlyTheMinimumGap() {
        assertEquals(
            300,
            hubTabsStart(barWidth = 1200, tabsWidth = 600, trailingWidth = 268, minGap = 32)
        )
    }

    @Test
    fun tabsStartAtTheStartWhenCentringWouldCrowdTheTrailingGroup() {
        assertEquals(
            0,
            hubTabsStart(barWidth = 1200, tabsWidth = 600, trailingWidth = 269, minGap = 32)
        )
        assertEquals(
            0,
            hubTabsStart(barWidth = 1200, tabsWidth = 1300, trailingWidth = 0, minGap = 32)
        )
    }
}
