package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/** Typed `/web/vol` payload. */
data class Volume(
    val result: String? = null,
    val current: String? = null,
    val muted: String? = null
) : Serializable
