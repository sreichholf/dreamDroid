package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * Typed web simple-result payload (`e2state` / `e2statetext`). [id] is the `e2id` the AutoTimer
 * plugin (api_version 1.7) gives the AutoTimer an edit wrote; null elsewhere.
 */
data class SimpleResult(
    val state: String? = null,
    val stateText: String? = null,
    val id: String? = null
) : Serializable
