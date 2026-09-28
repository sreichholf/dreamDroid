package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import net.reichholf.dreamdroid.enigma.Event

class EpgBouquetListState(initial: List<Event> = emptyList()) {
    val items: SnapshotStateList<Event> = initial.toMutableStateList()
    val listState = LazyListState()
    var scrollEpoch by mutableIntStateOf(0)
        private set

    fun replaceAll(next: List<Event>) {
        items.clear()
        items.addAll(next)
    }

    fun scrollToTop() {
        scrollEpoch++
    }
}
