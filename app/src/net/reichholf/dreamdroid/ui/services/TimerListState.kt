package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class TimerListState(initial: List<TimerListItem> = emptyList()) {
    val items: SnapshotStateList<TimerListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<TimerListItem>) {
        items.clear()
        items.addAll(next)
    }
}

fun ComposeView.bindTimerListScreen(
    state: TimerListState,
    onItemClick: (TimerListItem) -> Unit,
    onItemLongClick: (TimerListItem) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            TimerListScreen(
                items = state.items,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
    }
}
