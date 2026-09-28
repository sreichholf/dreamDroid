package net.reichholf.dreamdroid.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/** Device info as a NavHost destination: title and messages go to the shell. */
@Composable
fun DeviceInfoDestination(
    modifier: Modifier = Modifier,
    viewModel: DeviceInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    DreamDroidPullRefresh(
        refreshing = uiState.refreshing,
        onRefresh = viewModel::refresh,
        enabled = true,
        modifier = modifier
    ) {
        DeviceInfoScreen(state = uiState)
    }
}
