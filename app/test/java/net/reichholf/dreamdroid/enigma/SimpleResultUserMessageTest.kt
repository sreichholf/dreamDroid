package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SimpleResultUserMessageTest {
    @Test
    fun boxStateTextWinsOverTheFailure() {
        val rejected = EnigmaResponse(
            SimpleResult(state = Python.FALSE, stateText = "Timer already exists"),
            EnigmaHttpError(EnigmaFailure.BoxRejected("Timer already exists"))
        )
        assertEquals(UiText.Raw("Timer already exists"), rejected.userMessageText())
    }

    @Test
    fun emptyStateTextUsesTheFailure() {
        assertEquals(
            UiText.Resource(R.string.auth_error),
            EnigmaResponse(
                SimpleResult(state = Python.FALSE, stateText = ""),
                EnigmaHttpError(EnigmaFailure.Auth)
            ).userMessageText()
        )
        assertEquals(
            UiText.Resource(R.string.auth_error),
            EnigmaResponse<SimpleResult>(null, EnigmaHttpError(EnigmaFailure.Auth))
                .userMessageText()
        )
    }

    @Test
    fun noMessageFallsBackToTheContentError() {
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
