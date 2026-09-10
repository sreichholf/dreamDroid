package net.reichholf.dreamdroid.ui.compose

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Bridge so [net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper] can drive a
 * Compose pull-to-refresh indicator the same way it used to drive [androidx.swiperefreshlayout.widget.SwipeRefreshLayout].
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
