package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * Typed `/web/powerstate` payload.
 *
 * [isRunning] matches the inverted `e2instandby` mapping used by the parser:
 * XML `false` (not in standby) → `true`.
 */
data class PowerState(val isRunning: Boolean? = null) : Serializable
