package net.reichholf.dreamdroid.tv.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FittedLineCountFromHeightTest {
    @Test
    fun wholeLinesFitWithoutRemainder() {
        assertEquals(5, fittedLineCountFromHeight(maxHeightPx = 80f, lineHeightPx = 16f))
    }

    @Test
    fun partialLineIsDropped() {
        assertEquals(5, fittedLineCountFromHeight(maxHeightPx = 84f, lineHeightPx = 16f))
    }

    @Test
    fun noRoomForAFullLine() {
        assertEquals(0, fittedLineCountFromHeight(maxHeightPx = 15f, lineHeightPx = 16f))
    }

    @Test
    fun nonPositiveDimensionsYieldZero() {
        assertEquals(0, fittedLineCountFromHeight(maxHeightPx = 0f, lineHeightPx = 16f))
        assertEquals(0, fittedLineCountFromHeight(maxHeightPx = 80f, lineHeightPx = 0f))
        assertEquals(0, fittedLineCountFromHeight(maxHeightPx = -1f, lineHeightPx = 16f))
    }
}
