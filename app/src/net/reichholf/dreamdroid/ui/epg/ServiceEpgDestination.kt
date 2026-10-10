package net.reichholf.dreamdroid.ui.epg

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.text.asString

/** Per-service EPG as a NavHost destination. The list and its load live on [ServiceEpgViewModel]. */
@Composable
fun ServiceEpgDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: ServiceEpgViewModel = hiltViewModel(),
    detailViewModel: EpgEventDetailViewModel = hiltViewModel()
) {
    // Prefer Compose BackHandler so system Back pops to hub before MainActivity leave-confirm.
    BackHandler {
        handle.popNavBackStack()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)

    LaunchedEffect(uiState.serviceRef) {
        if (uiState.serviceRef.isEmpty()) {
            handle.popNavBackStack()
        }
    }

    EpgEventListDetailHost(handle, detailViewModel, modifier) {
        DreamDroidPullRefresh(
            refreshing = uiState.refreshing,
            onRefresh = { viewModel.reload(forceRefresh = true) },
            enabled = true
        ) {
            ServiceEpgScreen(
                sections = uiState.sections,
                emptyMessage = uiState.emptyMessage?.asString(),
                onItemClick = detailViewModel::showDetail
            )
        }
    }
}
