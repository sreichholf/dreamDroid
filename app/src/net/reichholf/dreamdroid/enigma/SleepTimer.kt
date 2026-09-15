package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/** Typed `/web/sleeptimer` payload. */
data class SleepTimer(
    val enabled: String? = null,
    val minutes: String? = null,
    val action: String? = null,
    val text: String? = null
) : Serializable
