package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

class TimerListState(initial: List<TimerListItem> = emptyList()) {
    val items: SnapshotStateList<TimerListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<TimerListItem>) {
        items.clear()
        items.addAll(next)
    }
}
