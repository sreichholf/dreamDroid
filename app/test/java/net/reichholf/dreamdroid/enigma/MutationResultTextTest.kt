package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.ui.text.UiText
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

    @Test
    fun userMessageTextFollowsTheSameOrder() {
        val rejected = EnigmaResponse(
            SimpleResult(state = Python.FALSE, stateText = "Timer already exists"),
            EnigmaHttpError(EnigmaFailure.BoxRejected("Timer already exists"))
        )
        assertEquals(UiText.Raw("Timer already exists"), rejected.userMessageText())
        assertEquals(
            UiText.Resource(R.string.auth_error),
            EnigmaResponse<SimpleResult>(null, EnigmaHttpError(EnigmaFailure.Auth))
                .userMessageText()
        )
        assertEquals(
            UiText.Resource(R.string.get_content_error),
            EnigmaResponse<SimpleResult>(null, EnigmaHttpError(EnigmaFailure.Cancelled))
                .userMessageText()
        )
        assertEquals(
            UiText.Resource(R.string.get_content_error),
            EnigmaResponse<SimpleResult>(null).userMessageText()
        )
    }
}
