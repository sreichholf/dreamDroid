package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * Typed `/web/timerlist` row (`e2timer`).
 */
data class Timer(
    val reference: String = "",
    val serviceName: String = "",
    val eit: String = "",
    val name: String = "",
    val description: String = "",
    val descriptionExtended: String = "",
    val disabled: String = "",
    val begin: String = "",
    val end: String = "",
    val duration: String = "",
    val beginReadable: String = "",
    val endReadable: String = "",
    val durationReadable: String = "",
    val startPrepare: String = "",
    val justPlay: String = "",
    val afterEvent: String = "",
    val location: String = "",
    val tags: String = "",
    val logEntries: String = "",
    val fileName: String = "",
    val backOff: String = "",
    val nextActivation: String = "",
    val firstTryPrepare: String = "",
    val state: String = "",
    val repeated: String = "",
    val dontSave: String = "",
    val canceled: String = "",
    val toggleDisabled: String = "",
) : Serializable
