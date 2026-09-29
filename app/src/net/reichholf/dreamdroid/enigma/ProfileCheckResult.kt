package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The outcome of a profile check. [errorText] is the failure's own message when it has one;
 * [errorTextId] names the failed check step.
 */
data class ProfileCheckResult(
    val hasError: Boolean = false,
    val isSoftError: Boolean = false,
    val errorTextId: Int = -1,
    val errorText: UiText? = null,
    val failure: EnigmaFailure? = null
)
