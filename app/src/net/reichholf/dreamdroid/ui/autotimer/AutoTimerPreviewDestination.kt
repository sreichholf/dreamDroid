package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/** One AutoTimer's preview. Enabling it needs the receiver. */
@Composable
fun AutoTimerPreviewDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: AutoTimerPreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    AutoTimerPreviewScreen(
        state = uiState,
        onRefresh = viewModel::reload,
        onEnable = { if (uiState.blocked) handle.requestNeedsReceiver() else viewModel.enable() },
        onToggleLog = viewModel::toggleLog,
        modifier = modifier
    )
}
