package net.reichholf.dreamdroid.enigma

import java.io.Serializable

data class Event(
    val eventId: String = "",
    val title: String = "",
    val start: String = "",
    val duration: String = "",
    val currentTime: String = "",
    val description: String = "",
    val descriptionExtended: String = "",
    val serviceReference: String = "",
    val serviceName: String = "",
    val startReadable: String = "",
    val startTimeReadable: String = "",
    val durationReadable: String = ""
) : Serializable
