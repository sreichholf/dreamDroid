package net.reichholf.dreamdroid.ui.zap

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import net.reichholf.dreamdroid.enigma.Service

class ZapListState(initial: List<Service> = emptyList()) {
    val items: SnapshotStateList<Service> = initial.toMutableStateList()
    val gridState = LazyGridState()
    var scrollEpoch by mutableIntStateOf(0)
        private set

    fun replaceAll(next: List<Service>) {
        items.clear()
        items.addAll(next)
    }

    fun scrollToTop() {
        scrollEpoch++
    }
}
