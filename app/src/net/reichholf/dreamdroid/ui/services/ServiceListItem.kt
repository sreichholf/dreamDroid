package net.reichholf.dreamdroid.ui.services

enum class ServiceRowKind {
    CHANNEL,
    DIRECTORY,
    MARKER,
}

data class ServiceListItem(
    val index: Int,
    val reference: String,
    val name: String,
    val kind: ServiceRowKind,
    val nowTitle: String = "",
    val nowStart: String = "",
    val nowDuration: String = "",
    val nextTitle: String = "",
    val nextStart: String = "",
    val nextDuration: String = "",
    val progressMax: Int = 0,
    val progress: Int = 0,
)
