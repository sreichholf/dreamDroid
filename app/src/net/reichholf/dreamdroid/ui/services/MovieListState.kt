package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class MovieListState(initial: List<MovieListItem> = emptyList()) {
    val items: SnapshotStateList<MovieListItem> = initial.toMutableStateList()

    fun replaceAll(next: List<MovieListItem>) {
        items.clear()
        items.addAll(next)
    }
}

fun ComposeView.bindMovieListScreen(
    state: MovieListState,
    refresh: ComposeRefreshState,
    onRefresh: () -> Unit,
    onItemClick: MovieListTap,
    onItemLongClick: MovieListTap,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            DreamDroidPullRefresh(
                refreshing = refresh.isRefreshing,
                onRefresh = onRefresh,
                enabled = refresh.enabled,
            ) {
                MovieListScreen(
                    items = state.items,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                )
            }
        }
    }
}
