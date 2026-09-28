package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnigmaFailureTextTest {
    @Test
    fun localizedFailuresMapToResources() {
        assertEquals(UiText.Resource(R.string.auth_error), EnigmaFailure.Auth.userMessageText())
        assertEquals(
            UiText.Resource(R.string.host_not_found),
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns).userMessageText()
        )
        assertEquals(
            UiText.Resource(R.string.host_unreach),
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout).userMessageText()
        )
    }

    @Test
    fun receiverTextStaysRaw() {
        assertEquals(
            UiText.Raw("Internal Server Error"),
            EnigmaFailure.Http(500, "Internal Server Error").userMessageText()
        )
        assertEquals(UiText.Raw("Standby"), EnigmaFailure.BoxRejected("Standby").userMessageText())
    }

    @Test
    fun contentErrorPrefixesFailureText() {
        assertEquals(
            UiText.Resource(
                R.string.content_error_detail,
                listOf(
                    UiText.Resource(R.string.get_content_error),
                    UiText.Resource(R.string.auth_error)
                )
            ),
            EnigmaHttpError(EnigmaFailure.Auth).contentErrorText()
        )
    }
}
