package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class ServiceListState(initial: List<ServiceListItem> = emptyList()) {
    val items: SnapshotStateList<ServiceListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<ServiceListItem>) {
        items.clear()
        items.addAll(next)
    }
}

fun ComposeView.bindServiceListScreen(
    state: ServiceListState,
    refresh: ComposeRefreshState,
    onRefresh: () -> Unit,
    onItemClick: ServiceListTap,
    onItemLongClick: ServiceListTap,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            DreamDroidPullRefresh(
                refreshing = refresh.isRefreshing,
                onRefresh = onRefresh,
                enabled = refresh.enabled,
            ) {
                ServiceListScreen(
                    items = state.items,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                )
            }
        }
    }
}
