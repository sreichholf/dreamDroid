package net.reichholf.dreamdroid.ui.services

data class TimerListItem(
    val index: Int,
    val name: String,
    val serviceName: String,
    val begin: String,
    val end: String,
    val action: String,
    val state: String,
    val stateColor: Int,
)
