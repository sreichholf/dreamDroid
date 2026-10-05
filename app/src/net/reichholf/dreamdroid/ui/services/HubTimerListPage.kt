package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.BindShellFab
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.text.asString

/**
 * The hub Timers tab. The list and its load live on [HubTimerListViewModel]; the same
 * [remountEpoch] does not load again. HubDestination owns the REQUEST_EDIT_TIMER result
 * and bumps [remountEpoch] after a timer edit.
 */
@Composable
fun HubTimerListPage(
    handle: PhoneNavHandle,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: HubTimerListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    fun online(action: () -> Unit) {
        if (uiState.mutationsBlocked) handle.requestNeedsReceiver() else action()
    }

    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = Statics.ITEM_CLEANUP,
                label = stringResource(R.string.cleanup),
                iconRes = R.drawable.ic_action_clean,
                enabled = !uiState.cleaning,
                onClick = { online(viewModel::cleanup) }
            )
        )
    )

    val newTimerLabel = stringResource(R.string.new_timer)
    BindShellFab(
        contentDescription = newTimerLabel,
        iconRes = R.drawable.ic_action_fab_add,
        onClick = { online { handle.navigateToTimerEdit(Timer.getInitialTimer(), create = true) } },
        text = newTimerLabel,
        lookDisabled = uiState.mutationsBlocked
    )

    LaunchedEffect(viewModel, remountEpoch) {
        viewModel.onRemount(remountEpoch)
    }

    val resources = LocalResources.current
    val items = remember(uiState.timers, resources) {
        timerListItemsFrom(resources, uiState.timers)
    }
    DreamDroidPullRefresh(
        refreshing = uiState.refreshing,
        onRefresh = { viewModel.reload(forceRefresh = true) },
        modifier = modifier
    ) {
        if (items.isEmpty()) {
            ListEmptyState(
                loading = uiState.refreshing,
                message = uiState.emptyMessage?.asString(),
                onRetry = { viewModel.reload(forceRefresh = true) }
            )
        } else {
            TimerListScreen(
                items = items,
                onItemClick = { item ->
                    uiState.timers.getOrNull(item.index)?.let { timer ->
                        handle.navigateToTimerEdit(timer, create = false)
                    }
                }
            )
        }
    }

    IndeterminateProgressHost(
        if (uiState.cleaning) {
            IndeterminateProgressState(message = stringResource(R.string.cleaning_timerlist))
        } else {
            null
        }
    )
}
