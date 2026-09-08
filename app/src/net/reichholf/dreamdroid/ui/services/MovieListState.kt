package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
    onItemClick: (MovieListItem) -> Unit,
    onItemLongClick: (MovieListItem) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            MovieListScreen(
                items = state.items,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
    }
}
