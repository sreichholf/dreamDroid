package net.reichholf.dreamdroid.ui.services

data class MovieListItem(
    val index: Int,
    val title: String,
    val serviceName: String,
    val fileSize: String,
    val time: String,
    val length: String,
    /** The recording's service reference: which row the shown detail belongs to. */
    val reference: String = ""
)
