package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class TvMoviesHubState {
    var selected by mutableStateOf(TvMoviesDestination.TV)
    var rows by mutableStateOf(listOf<String>())
    var selectedRow by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    /** Latest hub destination click handler; shell ComposeView reads this on each click. */
    var onDestinationSelected: (TvMoviesDestination) -> Unit = {}
}

fun ComposeView.bindTvMoviesHeader(state: TvMoviesHubState, onRowSelected: (Int) -> Unit) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            TvMoviesHeader(
                rows = state.rows,
                selectedRow = state.selectedRow,
                error = state.error,
                onRowSelected = onRowSelected,
            )
        }
    }
}

fun ComposeView.bindTvMoviesDestinationBar(state: TvMoviesHubState) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            TvMoviesDestinationBar(
                selected = state.selected,
                onDestinationSelected = { state.onDestinationSelected(it) },
            )
        }
    }
}
