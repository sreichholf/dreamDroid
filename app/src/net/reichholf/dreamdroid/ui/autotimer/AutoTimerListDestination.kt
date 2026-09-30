package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/**
 * The drawer's AutoTimer destination. While the session blocks writes, a write explains that
 * it needs the receiver.
 */
@Composable
fun AutoTimerListDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: AutoTimerListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    fun online(action: () -> Unit) {
        if (uiState.blocked) handle.requestNeedsReceiver() else action()
    }

    AutoTimerListScreen(
        state = uiState,
        onRefresh = viewModel::reload,
        onEnabledChange = { entry, enabled -> online { viewModel.setEnabled(entry, enabled) } },
        onMenu = viewModel::onItemMenu,
        onMenuAction = { entry, action -> online { viewModel.onMenuAction(entry, action) } },
        onMenuDismiss = viewModel::onMenuDismiss,
        modifier = modifier
    )
    AutoTimerListDialogs(
        state = uiState,
        onConfirmDelete = viewModel::confirmDelete,
        onDismiss = viewModel::dismissDelete
    )
}
