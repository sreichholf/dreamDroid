package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MutationResultTextTest {
    @Test
    fun boxRejectedStateTextWinsOverErrorAndFallback() {
        assertEquals(
            "Timer already exists",
            mutationResultText(
                stateText = "Timer already exists",
                errorText = "network down",
                fallback = "Could not load content"
            )
        )
    }

    @Test
    fun emptyStateTextUsesErrorThenFallback() {
        assertEquals(
            "network down",
            mutationResultText(stateText = null, errorText = "network down", fallback = "fallback")
        )
        assertEquals(
            "network down",
            mutationResultText(stateText = "", errorText = "network down", fallback = "fallback")
        )
        assertEquals(
            "fallback",
            mutationResultText(stateText = null, errorText = null, fallback = "fallback")
        )
        assertEquals(
            "fallback",
            mutationResultText(stateText = "", errorText = "", fallback = "fallback")
        )
    }
}
