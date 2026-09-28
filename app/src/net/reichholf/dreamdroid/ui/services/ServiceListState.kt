package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

class ServiceListState(initial: List<ServiceListItem> = emptyList()) {
    val items: SnapshotStateList<ServiceListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<ServiceListItem>) {
        items.clear()
        items.addAll(next)
    }
}
