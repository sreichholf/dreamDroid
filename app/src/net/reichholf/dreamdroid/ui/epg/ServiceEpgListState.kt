package net.reichholf.dreamdroid.ui.epg

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class ServiceEpgListState(initial: List<Event> = emptyList()) {
    val items: SnapshotStateList<Event> = initial.toMutableStateList()

    fun replaceAll(next: List<Event>) {
        items.clear()
        items.addAll(next)
    }
}

fun ComposeView.bindServiceEpgScreen(
    state: ServiceEpgListState,
    onItemClick: (Event) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            ServiceEpgScreen(
                items = state.items,
                onItemClick = onItemClick,
            )
        }
    }
}
