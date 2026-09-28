package net.reichholf.dreamdroid.ui.epg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.text.asString

/**
 * EPG search results as a NavHost destination with a Material 3 SearchBar. The route
 * [query] and the host's remount epoch (for same-query resubmits) drive the search. The
 * list, the search field, and the load live on [EpgSearchViewModel]; the expanded flag
 * stays here.
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
    var expanded by rememberSaveable(query, remountEpoch) {
        mutableStateOf(query.isEmpty())
    }
    ShellTitle(uiState.title)
    LaunchedEffect(query, remountEpoch) {
        viewModel.syncRoute(query, remountEpoch)
    }

    DreamDroidPullRefresh(
        refreshing = uiState.refreshing,
        onRefresh = viewModel::reload,
        enabled = query.isNotEmpty() && !expanded,
        modifier = modifier
    ) {
        EpgSearchScreen(
            queryState = viewModel.queryState,
            onSearch = { submitted ->
                val q = submitted.trim()
                if (q.isEmpty()) {
                    return@EpgSearchScreen
                }
                expanded = false
                handle.navigateToEpgSearch(q)
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            items = uiState.events,
            emptyMessage = uiState.emptyMessage?.asString(),
            onItemClick = detailViewModel::showDetail
        )
    }

    EpgEventDetailHost(handle, detailViewModel)
}
