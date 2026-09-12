package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Material 3 pull-to-refresh host for Compose lists formerly wrapped in View
 * [androidx.swiperefreshlayout.widget.SwipeRefreshLayout]. That View parent treated a
 * [androidx.compose.ui.platform.ComposeView] as never scrolled, so scrolling back up
 * always fired reload.
 *
 * [clipToBounds] is required: [PullToRefreshContainer] sits at [Alignment.TopCenter] and
 * animates in from above its host. Without clipping, its dark circular surface paints over
 * siblings above the list (hub bouquet [ScrollableTabRow] — e.g. the Provider tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreamDroidPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState(enabled = { enabled })

    LaunchedEffect(refreshing) {
        if (refreshing) {
            state.startRefresh()
        } else if (state.isRefreshing) {
            state.endRefresh()
        }
    }

    LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing && !refreshing) {
            onRefresh()
        }
    }

    Box(
        modifier
            .nestedScroll(state.nestedScrollConnection)
            .clipToBounds()
            .fillMaxSize(),
    ) {
        content()
        PullToRefreshContainer(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
