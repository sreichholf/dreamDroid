package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

class ProfilesListState(initial: List<ProfileListItem> = emptyList()) {
    val items: SnapshotStateList<ProfileListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<ProfileListItem>) {
        items.clear()
        items.addAll(next)
    }
}
