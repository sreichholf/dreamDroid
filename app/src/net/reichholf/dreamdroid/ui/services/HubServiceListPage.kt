package net.reichholf.dreamdroid.ui.services

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListDetailPanes
import net.reichholf.dreamdroid.ui.compose.ListDetailSinglePane
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.showsListDetailPanes
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailHost
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailViewModel
import net.reichholf.dreamdroid.ui.epg.ServiceEpgPane
import net.reichholf.dreamdroid.ui.epg.ServiceEpgViewModel
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.text.asString

/**
 * One TV/Radio hub bouquet tab. The ViewModel is keyed by the tab's ref on the hub
 * back-stack entry. The host keeps this in composition only while it is the active hub
 * child, so the top bar actions stay scoped. System back closes one opened folder before
 * leaving the hub; [onProvideGoUp] hands the host the tab-reselect action. Where the window
 * fits two panes, a channel's EPG opens beside the list ([epgViewModel]) instead of on its own
 * screen; events open in the EPG sheet either way. Each bouquet tab keeps its own open pane.
 */
@Composable
fun HubServiceListPage(
    handle: PhoneNavHandle,
    bouquetRef: String,
    bouquetName: String,
    modifier: Modifier = Modifier,
    onProvideGoUp: ((() -> Unit)?) -> Unit = {},
    onZapped: () -> Unit = {},
    viewModel: HubServiceListViewModel =
        hiltViewModel<HubServiceListViewModel, HubServiceListViewModel.Factory>(
            key = "hub-service:$bouquetRef"
        ) { factory -> factory.create(Service(bouquetRef, bouquetName)) },
    detailViewModel: EpgEventDetailViewModel = hiltViewModel(),
    epgViewModel: ServiceEpgViewModel = hiltViewModel(key = "hub-service-epg:$bouquetRef")
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val epgState by epgViewModel.uiState.collectAsStateWithLifecycle()
    val twoPanes = showsListDetailPanes()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    BackHandler(enabled = uiState.historyDepth > 0) {
        viewModel.navigateUp()
    }

    DisposableEffect(viewModel) {
        onProvideGoUp(viewModel::upOrReload)
        onDispose { onProvideGoUp(null) }
    }

    BindShellTopBarActions(
        hubServiceTopBarActions(
            state = uiState,
            multiEpgLabel = stringResource(R.string.multiepg),
            listEpgLabel = stringResource(R.string.epg_list),
            setDefaultLabel = stringResource(R.string.set_default),
            resetDefaultLabel = stringResource(R.string.reset_default),
            onMultiEpg = viewModel::openMultiEpg,
            onListEpg = viewModel::openListEpg,
            onToggleDefault = viewModel::toggleDefaultBouquet
        )
    )

    val currentOnZapped by rememberUpdatedState(onZapped)
    val effect = uiState.effect
    LaunchedEffect(effect) {
        when (effect) {
            null -> return@LaunchedEffect

            is HubServiceEffect.ShowEvent -> detailViewModel.showDetail(effect.event)

            is HubServiceEffect.ServiceEpg -> if (twoPanes) {
                epgViewModel.show(effect.reference, effect.name)
            } else {
                handle.navigateToServiceEpg(effect.reference, effect.name)
            }

            is HubServiceEffect.MultiEpg -> handle.navigateToMultiEpg(effect.reference, effect.name)

            is HubServiceEffect.ListEpg -> handle.navigateToEpg(effect.reference, effect.name)

            is HubServiceEffect.Stream -> {
                val row = effect.row
                try {
                    context.startActivity(
                        IntentFactory.getStreamServiceIntent(
                            context,
                            effect.stream,
                            row.serviceName,
                            effect.bouquetRef,
                            row
                        )
                    )
                } catch (_: ActivityNotFoundException) {
                    viewModel.onStreamFailed()
                }
            }

            HubServiceEffect.Zapped -> currentOnZapped()
        }
        viewModel.onEffectHandled()
    }

    val epgPane = @Composable {
        ServiceEpgPane(
            state = epgState,
            onRefresh = { epgViewModel.reload(forceRefresh = true) },
            onEventClick = detailViewModel::showDetail,
            onClose = epgViewModel::clear
        )
    }
    ListDetailPanes(
        detail = epgState.serviceRef.takeIf { it.isNotEmpty() },
        onDetailDismiss = epgViewModel::clear,
        list = {
            HubServiceListScreen(
                state = uiState,
                onRefresh = { viewModel.reload(forceRefresh = true) },
                onItemClick = { item, isLong ->
                    when (item.kind) {
                        ServiceRowKind.MARKER -> Unit

                        ServiceRowKind.DIRECTORY -> viewModel.openDirectory(item.index)

                        ServiceRowKind.CHANNEL -> {
                            if (viewModel.zapsOnTap(isLong)) {
                                handle.runOnlineOnly { viewModel.zap(item.index) }
                            } else {
                                viewModel.onItemMenu(item.index)
                            }
                        }
                    }
                },
                onMenuAction = { action ->
                    if (action.onlineOnly) {
                        handle.runOnlineOnly { viewModel.onMenuAction(action) }
                    } else {
                        viewModel.onMenuAction(action)
                    }
                },
                onMenuDismiss = viewModel::onMenuDismiss
            )
        },
        // The services grid keeps the whole width until a service's EPG is asked for.
        emptyDetail = null,
        // Only a window that narrowed under an open EPG gets here: it fills the space.
        singlePaneDetail = {
            ListDetailSinglePane { epgPane() }
        },
        modifier = modifier
    ) {
        epgPane()
    }

    EpgEventDetailHost(handle, detailViewModel)
}

/** [HubServiceListPage] without its ViewModel: the list and its row menu. */
@Composable
fun HubServiceListScreen(
    state: HubServiceListUiState,
    onRefresh: () -> Unit,
    onItemClick: (item: ServiceListItem, isLong: Boolean) -> Unit,
    onMenuAction: (ServiceRowAction) -> Unit,
    onMenuDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DreamDroidPullRefresh(
        refreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        if (state.items.isEmpty()) {
            ListEmptyState(
                loading = state.refreshing,
                message = state.emptyMessage?.asString(),
                onRetry = onRefresh
            )
        } else {
            ServiceListScreen(
                items = state.items,
                onItemClick = { onItemClick(it, false) },
                onItemLongClick = { onItemClick(it, true) },
                maxColumns = state.maxColumns,
                menu = state.menu,
                onMenuAction = onMenuAction,
                onMenuDismiss = onMenuDismiss
            )
        }
    }
}

/**
 * EPG jumps once a list is open, then the default-bouquet toggle (a filled star when the
 * list on screen is the profile's default).
 */
fun hubServiceTopBarActions(
    state: HubServiceListUiState,
    multiEpgLabel: String,
    listEpgLabel: String,
    setDefaultLabel: String,
    resetDefaultLabel: String,
    onMultiEpg: () -> Unit,
    onListEpg: () -> Unit,
    onToggleDefault: () -> Unit
): List<ShellTopBarAction> = buildList {
    if (state.currentRef.isNotEmpty()) {
        add(
            ShellTopBarAction(
                id = R.id.menu_multiepg,
                label = multiEpgLabel,
                iconRes = R.drawable.ic_multiepg,
                onClick = onMultiEpg
            )
        )
        add(
            ShellTopBarAction(
                id = R.id.menu_epg_list,
                label = listEpgLabel,
                iconRes = R.drawable.ic_action_list,
                onClick = onListEpg
            )
        )
    }
    val isDefault = state.isDefaultBouquet
    add(
        ShellTopBarAction(
            id = Statics.ITEM_SET_DEFAULT,
            label = if (isDefault) resetDefaultLabel else setDefaultLabel,
            iconRes = if (isDefault) R.drawable.ic_action_fav else R.drawable.ic_action_nofav,
            onClick = onToggleDefault
        )
    )
}
