package net.reichholf.dreamdroid.enigma

import java.io.Serializable

data class Movie(
    val reference: String = "",
    val title: String = "",
    val description: String = "",
    val descriptionExtended: String = "",
    val serviceName: String = "",
    val time: String = "",
    val timeReadable: String = "",
    val length: String = "",
    val tags: String = "",
    val fileName: String = "",
    val fileSize: String = "",
    val fileSizeReadable: String = "",
) : Serializable
