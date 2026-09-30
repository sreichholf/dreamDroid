package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.AutoTimerEdit
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

private const val ACTION_ADD = 1
private const val ACTION_RUN = 2

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

    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = ACTION_RUN,
                label = stringResource(R.string.autotimer_run),
                enabled = uiState.content is AutoTimerListContent.Ready && !uiState.pending &&
                    !uiState.running && !uiState.refreshing,
                onClick = { online(viewModel::requestRun) }
            ),
            ShellTopBarAction(
                id = ACTION_ADD,
                label = stringResource(R.string.autotimer_new),
                iconRes = R.drawable.ic_action_fab_add,
                enabled = uiState.content is AutoTimerListContent.Ready && !uiState.pending &&
                    !uiState.running,
                onClick = { online { handle.navigateToAutoTimerEdit(AutoTimerEdit()) } }
            )
        )
    )

    AutoTimerListScreen(
        state = uiState,
        onRefresh = viewModel::reload,
        onOpen = { handle.navigateToAutoTimerPreview(it.id.value, it.name) },
        onEnabledChange = { entry, enabled -> online { viewModel.setEnabled(entry, enabled) } },
        onMenu = viewModel::onItemMenu,
        onMenuAction = { entry, action ->
            if (action == AutoTimerRowAction.Edit) {
                online { handle.navigateToAutoTimerEdit(AutoTimerEdit(entry.id.value, entry.name)) }
            } else {
                online { viewModel.onMenuAction(entry, action) }
            }
        },
        onMenuDismiss = viewModel::onMenuDismiss,
        modifier = modifier
    )
    AutoTimerListDialogs(
        state = uiState,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmRun = viewModel::confirmRun,
        onDismissRun = viewModel::dismissRun,
        onRunResultShown = viewModel::onRunResultShown
    )
}
