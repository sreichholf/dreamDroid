package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.runtime.Composable
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

private const val ACTION_EDIT = 1

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

    val autoTimer = uiState.autoTimer
    BindShellTopBarActions(
        listOfNotNull(
            autoTimer?.let {
                ShellTopBarAction(
                    id = ACTION_EDIT,
                    label = stringResource(R.string.edit),
                    iconRes = R.drawable.ic_action_edit,
                    enabled = !uiState.pending,
                    onClick = {
                        if (uiState.blocked) {
                            handle.requestNeedsReceiver()
                        } else {
                            handle.navigateToAutoTimerEdit(it.id.value, it.settings.name)
                        }
                    }
                )
            }
        )
    )

    AutoTimerPreviewScreen(
        state = uiState,
        onRefresh = viewModel::reload,
        onEnable = { if (uiState.blocked) handle.requestNeedsReceiver() else viewModel.enable() },
        onToggleLog = viewModel::toggleLog,
        modifier = modifier
    )
}
