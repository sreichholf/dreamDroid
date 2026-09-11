package net.reichholf.dreamdroid.helpers.enigma2

/** Open so Java helpers like [Remote] can still extend the legacy type. */
open class SimpleResult {
    companion object {
        const val KEY_STATE: String = "state"
        const val KEY_STATE_TEXT: String = "statetext"
    }
}
