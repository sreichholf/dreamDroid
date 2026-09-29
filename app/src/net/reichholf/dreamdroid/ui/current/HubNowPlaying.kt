package net.reichholf.dreamdroid.ui.current

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.text.asString

/**
 * Publishes [HubNowPlayingViewModel]'s strip into [hubState], which the shell draws, and
 * hosts [CurrentServiceSheet] on tap. Polls only while the strip setting is on.
 */
@Composable
fun HubNowPlaying(
    handle: PhoneNavHandle,
    reloadEpoch: Int,
    hubState: TvMoviesHubState,
    viewModel: HubNowPlayingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    hubState.nowPlayingStripEnabled = uiState.enabled
    if (!uiState.enabled) {
        hubState.nowPlayingLabel = ""
        hubState.nowPlayingHeadline = ""
        hubState.nowPlayingProgress = 0f
        hubState.nowPlayingReference = ""
        hubState.nowPlayingName = ""
        hubState.onNowPlayingClick = {}
        return
    }

    val shown = uiState.shown

    val stream = uiState.stream
    LaunchedEffect(stream) {
        if (stream != null) {
            startServiceStream(context, stream, viewModel::onStreamFailed)
            viewModel.onStreamStarted()
        }
    }

    LaunchedEffect(viewModel) { viewModel.poll() }

    LaunchedEffect(reloadEpoch) { viewModel.onReloadEpoch(reloadEpoch) }

    hubState.nowPlayingLabel = uiState.label.asString()
    hubState.nowPlayingHeadline = uiState.headline.asString()
    hubState.nowPlayingProgress = eventProgressFraction(shown?.now)
    hubState.nowPlayingReference = shown?.service?.reference.orEmpty()
    hubState.nowPlayingName = shown?.service?.name.orEmpty()
    hubState.onNowPlayingClick = viewModel::openSheet

    if (uiState.sheetOpen) {
        CurrentServiceSheet(
            current = shown,
            loading = shown == null && !uiState.ready,
            streamBlocked = uiState.streamBlocked,
            onStream = { handle.runOnlineOnly(viewModel::stream) },
            onDismiss = viewModel::closeSheet
        )
    }
}

/** Stream is only valid when `/web/getcurrent` gave a non-empty service reference. */
fun currentServiceCanStream(current: CurrentService?): Boolean =
    current?.service?.reference?.isNotEmpty() == true
