package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

class MovieListState(initial: List<MovieListItem> = emptyList()) {
    val items: SnapshotStateList<MovieListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<MovieListItem>) {
        items.clear()
        items.addAll(next)
    }
}
