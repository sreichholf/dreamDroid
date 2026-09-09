package net.reichholf.dreamdroid.enigma

import java.io.Serializable

data class Service(
    val reference: String,
    val name: String,
    val provider: String = "",
    val videoWidth: String = "",
    val videoHeight: String = "",
    val videoSize: String = "",
    val widescreen: String = "",
    val apid: String = "",
    val vpid: String = "",
    val pcrPid: String = "",
    val pmtPid: String = "",
    val txtPid: String = "",
    val tsid: String = "",
    val onid: String = "",
    val sid: String = ""
) : Serializable
