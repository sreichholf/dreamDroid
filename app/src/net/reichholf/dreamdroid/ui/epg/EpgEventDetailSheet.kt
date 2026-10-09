package net.reichholf.dreamdroid.ui.epg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.ListDetailEmptyPane
import net.reichholf.dreamdroid.ui.compose.ListDetailPanes
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.AutoTimerEdit
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly

/** What the EPG detail's buttons do with the event shown. */
class EpgEventActions(
    val onSetTimer: (Event) -> Unit,
    val onEditTimer: (Event) -> Unit,
    val onImdb: (Event) -> Unit,
    val onSimilar: (Event) -> Unit,
    /** Offered while [EpgEventDetailUiState.autoTimerAvailable]. */
    val onRecordSeries: (Event) -> Unit = {}
)

/** [EpgEventActions] of a phone destination: timer actions need the receiver. */
@Composable
private fun phoneEpgEventActions(
    handle: PhoneNavHandle,
    viewModel: EpgEventDetailViewModel
): EpgEventActions {
    val context = LocalContext.current
    return EpgEventActions(
        onSetTimer = { event -> handle.runOnlineOnly { viewModel.setTimer(event) } },
        onEditTimer = { event ->
            handle.runOnlineOnly { handle.navigateToTimerEdit(Timer.createByEvent(event), true) }
        },
        onImdb = { event -> IntentFactory.queryIMDb(context, event) },
        onSimilar = { event -> handle.navigateToEpgSearch(event.title) },
        onRecordSeries = { event ->
            handle.runOnlineOnly {
                handle.navigateToAutoTimerEdit(AutoTimerEdit.recordSeries(event))
            }
        }
    )
}

/**
 * The EPG detail sheet of [viewModel] in a phone destination that is not an EPG list (MultiEPG,
 * the current service, the services hub). Similar events open EPG search.
 */
@Composable
fun EpgEventDetailHost(handle: PhoneNavHandle, viewModel: EpgEventDetailViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    val actions = phoneEpgEventActions(handle, viewModel)
    EpgEventDetailSheet(
        state = uiState,
        onDismiss = viewModel::dismissDetail,
        onSetTimer = actions.onSetTimer,
        onEditTimer = actions.onEditTimer,
        onImdb = actions.onImdb,
        onSimilar = actions.onSimilar,
        onRecordSeries = actions.onRecordSeries
    )
}

/**
 * An EPG [list] and the EPG detail of [viewModel] in a phone destination: beside the list where
 * the window fits two panes, a sheet elsewhere ([EpgEventListDetail]).
 */
@Composable
fun EpgEventListDetailHost(
    handle: PhoneNavHandle,
    viewModel: EpgEventDetailViewModel,
    modifier: Modifier = Modifier,
    list: @Composable () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    EpgEventListDetail(
        state = uiState,
        onDismiss = viewModel::dismissDetail,
        actions = phoneEpgEventActions(handle, viewModel),
        list = list,
        modifier = modifier
    )
}

/** [EpgEventDetailUiState.event] as a Material 3 modal sheet, plus the saving progress. */
@Composable
fun EpgEventDetailSheet(
    state: EpgEventDetailUiState,
    onDismiss: () -> Unit,
    onSetTimer: (Event) -> Unit,
    onEditTimer: (Event) -> Unit,
    onImdb: (Event) -> Unit,
    onSimilar: (Event) -> Unit,
    /** Offered while [EpgEventDetailUiState.autoTimerAvailable]. */
    onRecordSeries: (Event) -> Unit = {}
) {
    val actions = EpgEventActions(onSetTimer, onEditTimer, onImdb, onSimilar, onRecordSeries)
    state.event?.let { EpgEventSheet(state, it, onDismiss, actions) }
    EpgEventSavingProgress(state)
}

/**
 * [list] with [EpgEventDetailUiState.event] in a detail pane beside it where the window fits two
 * panes, and in a Material 3 modal sheet elsewhere; plus the saving progress.
 */
@Composable
fun EpgEventListDetail(
    state: EpgEventDetailUiState,
    onDismiss: () -> Unit,
    actions: EpgEventActions,
    list: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    ListDetailPanes(
        detail = state.event,
        onDetailDismiss = onDismiss,
        list = list,
        emptyDetail = { ListDetailEmptyPane(stringResource(R.string.epg_detail_pane_empty)) },
        singlePaneDetail = { event -> EpgEventSheet(state, event, onDismiss, actions) },
        modifier = modifier
    ) { event ->
        EpgDetailScreen(
            content = event.detailContent(),
            onSetTimer = { actions.onSetTimer(event) },
            onEditTimer = { actions.onEditTimer(event) },
            onImdb = { actions.onImdb(event) },
            onSimilar = { actions.onSimilar(event) },
            timerWritesBlocked = state.timerWritesBlocked,
            bodyHeightCap = null,
            onRecordSeries = state.recordSeries(event, actions)
        )
    }
    EpgEventSavingProgress(state)
}

@Composable
private fun EpgEventSheet(
    state: EpgEventDetailUiState,
    event: Event,
    onDismiss: () -> Unit,
    actions: EpgEventActions
) {
    EpgDetailModalSheet(
        content = event.detailContent(),
        onDismiss = onDismiss,
        onSetTimer = { actions.onSetTimer(event) },
        onEditTimer = { actions.onEditTimer(event) },
        onImdb = { actions.onImdb(event) },
        onSimilar = { actions.onSimilar(event) },
        timerWritesBlocked = state.timerWritesBlocked,
        onRecordSeries = state.recordSeries(event, actions)
    )
}

@Composable
private fun Event.detailContent(): EpgDetailContent = toEpgDetailContentOrUnavailable(
    stringResource(R.string.minutes_short),
    stringResource(R.string.not_available)
)

private fun EpgEventDetailUiState.recordSeries(
    event: Event,
    actions: EpgEventActions
): (() -> Unit)? = if (autoTimerAvailable) {
    { actions.onRecordSeries(event) }
} else {
    null
}

@Composable
private fun EpgEventSavingProgress(state: EpgEventDetailUiState) {
    val progress = if (state.saving) {
        IndeterminateProgressState(message = stringResource(R.string.saving))
    } else {
        null
    }
    IndeterminateProgressHost(progress)
}
