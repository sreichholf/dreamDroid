package net.reichholf.dreamdroid.ui.epg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ReplaceShellTopBar
import net.reichholf.dreamdroid.ui.nav.ShellTitle

/**
 * EPG search as a NavHost destination. Its search field replaces the shell top bar. The
 * route [query] and the host's remount epoch (for "similar" from a result) seed the field;
 * typing searches from there. The results, the field, and recent searches live on
 * [EpgSearchViewModel].
 */
@Composable
fun EpgSearchDestination(
    handle: PhoneNavHandle,
    query: String,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: EpgSearchViewModel = hiltViewModel(),
    detailViewModel: EpgEventDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ReplaceShellTopBar()
    LaunchedEffect(query, remountEpoch) {
        viewModel.syncRoute(query, remountEpoch)
    }

    DreamDroidPullRefresh(
        refreshing = false,
        onRefresh = viewModel::reload,
        enabled = !uiState.showRecent,
        modifier = modifier
    ) {
        EpgSearchScreen(
            queryState = viewModel.queryState,
            state = uiState,
            onBack = { handle.popNavBackStack() },
            onSearch = viewModel::submit,
            onRecentClick = viewModel::searchRecent,
            onRecentRemove = viewModel::forgetRecent,
            onItemClick = { event ->
                viewModel.onResultOpened()
                detailViewModel.showDetail(event)
            },
            focusOnStart = query.isEmpty()
        )
    }

    EpgEventDetailHost(handle, detailViewModel)
}
