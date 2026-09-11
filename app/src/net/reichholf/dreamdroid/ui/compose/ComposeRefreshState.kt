package net.reichholf.dreamdroid.ui.compose

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Holds pull-to-refresh indicator state for Compose list screens (replaces the old
 * [androidx.swiperefreshlayout.widget.SwipeRefreshLayout] + HTTP helper bridge).
 */
class ComposeRefreshState(
    /** When false, gesture pull-to-refresh is off; programmatic [setRefreshing] still works. */
    var enabled: Boolean = true,
) {
    private val refreshingState: MutableState<Boolean> = mutableStateOf(false)

    val isRefreshing: Boolean
        get() = refreshingState.value

    fun setRefreshing(value: Boolean) {
        refreshingState.value = value
    }
}
