package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import net.reichholf.dreamdroid.ui.nav.ShellViewModel
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

private const val ACTION_ADD = 1

/**
 * The service picker of one bouquet as a pushed destination. Back leaves a folder, then the
 * source, then the picker. Once the box answered an add, the outcome goes to the shell
 * message and the picker pops.
 */
@Composable
fun BouquetAddServicesDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: BouquetAddServicesViewModel = hiltViewModel(),
    shellActions: ShellViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler {
        if (!viewModel.navigateUp()) {
            handle.popNavBackStack()
        }
    }
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    val finished = uiState.finished
    LaunchedEffect(finished) {
        if (finished != null) {
            // The outcome outlives this destination, so it goes to the shell's message.
            shellActions.showMessage(finished)
            viewModel.onFinishHandled()
            handle.popNavBackStack()
        }
    }

    BindShellTopBarActions(
        if (uiState.source == null) {
            emptyList()
        } else {
            listOf(
                ShellTopBarAction(
                    id = ACTION_ADD,
                    label = stringResource(R.string.bouquet_add_selected, uiState.selected.size),
                    enabled = uiState.selected.isNotEmpty() && !uiState.pending,
                    onClick = {
                        if (uiState.blocked) handle.requestNeedsReceiver() else viewModel.add()
                    }
                )
            )
        }
    )

    BouquetAddServicesScreen(
        state = uiState,
        onSource = viewModel::openSource,
        onFolder = viewModel::openFolder,
        onToggle = viewModel::toggle,
        onRetry = viewModel::reload,
        modifier = modifier
    )
}
