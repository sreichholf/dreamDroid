package net.reichholf.dreamdroid.enigma

import java.io.Serializable

data class Service @JvmOverloads constructor(
    val reference: String,
    val name: String,
    val provider: String = ""
) : Serializable
