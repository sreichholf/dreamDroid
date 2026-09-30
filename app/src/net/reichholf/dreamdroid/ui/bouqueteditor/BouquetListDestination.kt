package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

private const val ACTION_ADD_BOUQUET = 1

/**
 * The bouquet editor's index as a Tools hub tab. While the session blocks edits, a tap on
 * an edit explains that it needs the receiver.
 */
@Composable
fun BouquetListDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    onOpenBouquet: ((ref: String, name: String) -> Unit)? = null,
    viewModel: BouquetListViewModel = hiltViewModel()
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
                id = ACTION_ADD_BOUQUET,
                label = stringResource(R.string.bouquet_add),
                iconRes = R.drawable.ic_action_fab_add,
                enabled = uiState.content is BouquetListContent.Ready && !uiState.pending,
                onClick = { online(viewModel::openAdd) }
            )
        )
    )

    BouquetListScreen(
        state = uiState,
        onModeChange = viewModel::setMode,
        onRefresh = viewModel::reload,
        onMenu = viewModel::onItemMenu,
        onMenuAction = { bouquet, action -> online { viewModel.onMenuAction(bouquet, action) } },
        onMenuDismiss = viewModel::onMenuDismiss,
        onMove = viewModel::move,
        onOpenBouquet = onOpenBouquet?.let { open ->
            { bouquet: BouquetEntry -> open(bouquet.reference, bouquet.name) }
        },
        modifier = modifier
    )

    BouquetListDialogs(
        state = uiState,
        nameState = viewModel.name.state,
        onConfirmAdd = viewModel::confirmAdd,
        onConfirmRename = viewModel::confirmRename,
        onConfirmRemove = viewModel::confirmRemove,
        onDismiss = viewModel::dismissDialog
    )
}
