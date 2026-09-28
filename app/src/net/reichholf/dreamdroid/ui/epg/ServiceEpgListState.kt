package net.reichholf.dreamdroid.ui.epg

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import net.reichholf.dreamdroid.enigma.Event

class ServiceEpgListState(initial: List<Event> = emptyList()) {
    val items: SnapshotStateList<Event> = initial.toMutableStateList()

    fun replaceAll(next: List<Event>) {
        items.clear()
        items.addAll(next)
    }
}
