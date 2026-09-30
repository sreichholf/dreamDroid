package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.nav.ShellTitle

/** The drawer's AutoTimer destination. */
@Composable
fun AutoTimerListDestination(
    modifier: Modifier = Modifier,
    viewModel: AutoTimerListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    AutoTimerListScreen(state = uiState, onRefresh = viewModel::reload, modifier = modifier)
}
