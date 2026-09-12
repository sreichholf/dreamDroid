package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Material 3 pull-to-refresh host for Compose lists formerly wrapped in View
 * [androidx.swiperefreshlayout.widget.SwipeRefreshLayout]. That View parent treated a
 * [androidx.compose.ui.platform.ComposeView] as never scrolled, so scrolling back up
 * always fired reload.
 *
 * Uses [PullToRefreshDefaults.Indicator] only — not [androidx.compose.material3.pulltorefresh.PullToRefreshContainer].
 * The container's elevated `surfaceContainerHigh` disk sits at `TopCenter`; on programmatic
 * reload `verticalOffset` jumps to the positional threshold so the disk is drawn fully
 * *inside* the list host (translationY ≈ +40.dp). [Modifier.clipToBounds] therefore cannot
 * stop it from reading as a dark circle under hub bouquet tabs (e.g. Provider on TV & Movies).
 * Clipping still applies so a mid-pull glyph cannot paint into siblings above the host.
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
        CompositionLocalProvider(
            LocalContentColor provides PullToRefreshDefaults.contentColor,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(IndicatorContainerSize)
                    .graphicsLayer {
                        translationY = state.verticalOffset - size.height
                    }
                    .testTag(PULL_REFRESH_INDICATOR_TAG),
                contentAlignment = Alignment.Center,
            ) {
                PullToRefreshDefaults.Indicator(state = state)
            }
        }
    }
}

/** Matches Material3 `SpinnerContainerSize` (PullToRefresh.kt). */
private val IndicatorContainerSize = 40.dp

const val PULL_REFRESH_INDICATOR_TAG = "pull_refresh_indicator"
