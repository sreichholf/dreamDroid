package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/** Typed web simple-result payload (`e2state` / `e2statetext`). */
data class SimpleResult(val state: String? = null, val stateText: String? = null) : Serializable
