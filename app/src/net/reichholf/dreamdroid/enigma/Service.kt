package net.reichholf.dreamdroid.enigma

import java.io.Serializable

data class Service(val reference: String, val name: String, val provider: String = "") :
    Serializable
