package net.reichholf.dreamdroid.ui.pick

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class PickServiceListState(initial: List<Service> = emptyList()) {
    val items: SnapshotStateList<Service> = initial.toMutableStateList()

    fun replaceAll(next: List<Service>) {
        items.clear()
        items.addAll(next)
    }
}

fun ComposeView.bindPickServiceScreen(
    state: PickServiceListState,
    refresh: ComposeRefreshState,
    onRefresh: () -> Unit,
    onItemClick: (Service) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            DreamDroidPullRefresh(
                refreshing = refresh.isRefreshing,
                onRefresh = onRefresh,
                enabled = refresh.enabled,
            ) {
                PickServiceScreen(
                    items = state.items,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}
