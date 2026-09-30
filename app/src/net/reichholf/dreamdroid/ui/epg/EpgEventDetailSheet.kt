package net.reichholf.dreamdroid.ui.epg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.AutoTimerEdit
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly

/**
 * The EPG detail sheet of [viewModel] in a phone destination. Timer actions need the
 * receiver; similar events open EPG search.
 */
@Composable
fun EpgEventDetailHost(handle: PhoneNavHandle, viewModel: EpgEventDetailViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    EpgEventDetailSheet(
        state = uiState,
        onDismiss = viewModel::dismissDetail,
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
    val event = state.event
    if (event != null) {
        val content = event.toEpgDetailContentOrUnavailable(
            stringResource(R.string.minutes_short),
            stringResource(R.string.not_available)
        )
        EpgDetailModalSheet(
            content = content,
            onDismiss = onDismiss,
            onSetTimer = { onSetTimer(event) },
            onEditTimer = { onEditTimer(event) },
            onImdb = { onImdb(event) },
            onSimilar = { onSimilar(event) },
            timerWritesBlocked = state.timerWritesBlocked,
            onRecordSeries = if (state.autoTimerAvailable) {
                { onRecordSeries(event) }
            } else {
                null
            }
        )
    }
    val progress = if (state.saving) {
        IndeterminateProgressState(message = stringResource(R.string.saving))
    } else {
        null
    }
    IndeterminateProgressHost(progress)
}
