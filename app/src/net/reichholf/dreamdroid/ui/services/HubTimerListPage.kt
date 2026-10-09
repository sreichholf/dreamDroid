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
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListDetailEmptyPane
import net.reichholf.dreamdroid.ui.compose.ListDetailPanes
import net.reichholf.dreamdroid.ui.compose.ListDetailSinglePane
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.showsListDetailPanes
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.BindShellFab
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.text.asString
import net.reichholf.dreamdroid.ui.timers.TimerEditPaneHost
import net.reichholf.dreamdroid.ui.timers.TimerPaneViewModel

/**
 * The hub Timers tab. The list and its load live on [HubTimerListViewModel]; the same
 * [remountEpoch] does not load again. HubDestination owns the REQUEST_EDIT_TIMER result
 * and bumps [remountEpoch] after a timer edit. Where the window fits two panes, a timer opens
 * in a form beside the list instead of on its own screen; [paneViewModel] is the hub's, which
 * also takes the service pick.
 */
@Composable
fun HubTimerListPage(
    handle: PhoneNavHandle,
    paneViewModel: TimerPaneViewModel,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: HubTimerListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val paneState by paneViewModel.uiState.collectAsStateWithLifecycle()
    val openedPane by paneViewModel.opened.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    fun online(action: () -> Unit) {
        if (uiState.mutationsBlocked) handle.requestNeedsReceiver() else action()
    }

    val twoPanes = showsListDetailPanes()
    fun edit(timer: TypedTimer, create: Boolean) {
        if (twoPanes) {
            paneViewModel.open(timer, create)
        } else {
            handle.navigateToTimerEdit(timer, create)
        }
    }

    // The FAB would sit on the open form; the form has its own Save.
    if (openedPane == null) {
        val newTimerLabel = stringResource(R.string.new_timer)
        BindShellFab(
            contentDescription = newTimerLabel,
            iconRes = R.drawable.ic_action_fab_add,
            onClick = { online { edit(Timer.getInitialTimer(), create = true) } },
            text = newTimerLabel,
            lookDisabled = uiState.mutationsBlocked
        )
    }

    // A pane save or delete that went through: the list shows the receiver's timers again.
    LaunchedEffect(paneState.finished) {
        if (paneState.finished) {
            paneViewModel.onFinishHandled()
            paneViewModel.dismiss()
            viewModel.reload(forceRefresh = true)
        }
    }

    // An open form has its own bar; cleaning up the list under it would pull timers away.
    BindShellTopBarActions(
        if (openedPane != null) {
            emptyList()
        } else {
            listOf(
                ShellTopBarAction(
                    id = Statics.ITEM_CLEANUP,
                    label = stringResource(R.string.cleanup),
                    iconRes = R.drawable.ic_action_clean,
                    enabled = !uiState.cleaning,
                    onClick = { online(viewModel::cleanup) }
                )
            )
        }
    )

    LaunchedEffect(viewModel, remountEpoch) {
        viewModel.onRemount(remountEpoch)
    }

    val resources = LocalResources.current
    val items = remember(uiState.timers, resources) {
        timerListItemsFrom(resources, uiState.timers)
    }
    ListDetailPanes(
        detail = openedPane,
        onDetailDismiss = paneViewModel::dismiss,
        list = {
            DreamDroidPullRefresh(
                refreshing = uiState.refreshing,
                onRefresh = { viewModel.reload(forceRefresh = true) }
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
                                edit(timer, create = false)
                            }
                        }
                    )
                }
            }
        },
        emptyDetail = { ListDetailEmptyPane(stringResource(R.string.timer_detail_pane_empty)) },
        // Only a window that narrowed under an open form gets here: the form fills the space.
        singlePaneDetail = {
            ListDetailSinglePane { TimerEditPaneHost(handle, paneViewModel) }
        },
        modifier = modifier
    ) {
        TimerEditPaneHost(handle, paneViewModel)
    }

    IndeterminateProgressHost(
        if (uiState.cleaning) {
            IndeterminateProgressState(message = stringResource(R.string.cleaning_timerlist))
        } else {
            null
        }
    )
}
