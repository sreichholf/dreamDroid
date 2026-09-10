package net.reichholf.dreamdroid.ui.zap

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

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

fun ComposeView.bindZapScreen(
    state: ZapListState,
    refresh: ComposeRefreshState,
    onRefresh: () -> Unit,
    onItemClick: (Service) -> Unit,
    onItemLongClick: (Service) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            DreamDroidPullRefresh(
                refreshing = refresh.isRefreshing,
                onRefresh = onRefresh,
                enabled = refresh.enabled,
            ) {
                ZapScreen(
                    items = state.items,
                    gridState = state.gridState,
                    scrollEpoch = state.scrollEpoch,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                )
            }
        }
    }
}
