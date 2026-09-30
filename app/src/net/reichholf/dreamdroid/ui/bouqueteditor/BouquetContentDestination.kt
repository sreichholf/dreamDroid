package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

private const val ACTION_ADD_SERVICES = 1
private const val ACTION_ADD_MARKER = 2

/**
 * One bouquet's entries as a pushed destination; the route is read by the ViewModel. While
 * the session blocks edits, a tap on an edit explains that it needs the receiver.
 */
@Composable
fun BouquetContentDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: BouquetContentViewModel = hiltViewModel()
) {
    // Compose BackHandler so system Back pops to the bouquets before the leave-confirm.
    BackHandler {
        handle.popNavBackStack()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // The profile changed: the bouquet is the old receiver's.
    LaunchedEffect(uiState.closed) {
        if (uiState.closed) {
            handle.popNavBackStack()
        }
    }
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    fun online(action: () -> Unit) {
        if (uiState.blocked) handle.requestNeedsReceiver() else action()
    }

    val ready = uiState.content is BouquetContentList.Ready && !uiState.pending
    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = ACTION_ADD_MARKER,
                label = stringResource(R.string.bouquet_marker_add),
                iconRes = R.drawable.ic_bookmark_add,
                enabled = ready,
                onClick = { online { viewModel.openAddMarker() } }
            ),
            ShellTopBarAction(
                id = ACTION_ADD_SERVICES,
                label = stringResource(R.string.bouquet_add_services),
                iconRes = R.drawable.ic_action_fab_add,
                enabled = ready,
                onClick = {
                    online {
                        handle.navigateToBouquetAddServices(uiState.bouquetRef, uiState.mode)
                    }
                }
            )
        )
    )

    BouquetContentScreen(
        state = uiState,
        onRefresh = viewModel::reload,
        onMenu = viewModel::onItemMenu,
        onMenuAction = { row, action -> online { viewModel.onMenuAction(row, action) } },
        onMenuDismiss = viewModel::onMenuDismiss,
        onMove = viewModel::move,
        modifier = modifier
    )

    BouquetContentDialogs(
        state = uiState,
        nameState = viewModel.name.state,
        onConfirmRename = viewModel::confirmRename,
        onConfirmAddMarker = viewModel::confirmAddMarker,
        onConfirmRemove = viewModel::confirmRemove,
        onDismiss = viewModel::dismissDialog
    )
}
