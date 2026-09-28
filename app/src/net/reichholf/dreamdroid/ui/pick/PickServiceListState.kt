package net.reichholf.dreamdroid.ui.pick

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import net.reichholf.dreamdroid.enigma.Service

class PickServiceListState(initial: List<Service> = emptyList()) {
    val items: SnapshotStateList<Service> = initial.toMutableStateList()

    fun replaceAll(next: List<Service>) {
        items.clear()
        items.addAll(next)
    }
}
