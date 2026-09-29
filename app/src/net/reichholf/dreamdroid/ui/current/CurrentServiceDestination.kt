package net.reichholf.dreamdroid.ui.current

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailHost
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailViewModel
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly

/**
 * Current service as a NavHost destination. The now and next event open the shared EPG
 * detail sheet, whose timer, IMDb, and similar actions work as on the EPG screens.
 */
@Composable
fun CurrentServiceDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: CurrentServiceViewModel = hiltViewModel(),
    detailViewModel: EpgEventDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    fun showDetail(event: Event?) {
        if (uiState.ready && event != null) {
            detailViewModel.showDetail(event)
        }
    }

    val stream = uiState.stream
    LaunchedEffect(stream) {
        if (stream != null) {
            startServiceStream(context, stream, viewModel::onStreamFailed)
            viewModel.onStreamStarted()
        }
    }

    DreamDroidPullRefresh(
        refreshing = uiState.refreshing,
        onRefresh = viewModel::reload,
        enabled = true,
        modifier = modifier
    ) {
        CurrentServiceScreen(
            state = uiState,
            onNowClick = { showDetail(uiState.current?.now) },
            onNextClick = { showDetail(uiState.current?.next) },
            onStream = { handle.runOnlineOnly(viewModel::stream) }
        )
    }

    EpgEventDetailHost(handle, detailViewModel)
}

/** Starts the player for [stream]; [onMissingPlayer] when no app on the device plays it. */
internal fun startServiceStream(
    context: Context,
    stream: CurrentServiceStream,
    onMissingPlayer: () -> Unit
) {
    try {
        context.startActivity(
            IntentFactory.getStreamServiceIntent(context, stream.stream, stream.name)
        )
    } catch (_: ActivityNotFoundException) {
        onMissingPlayer()
    }
}
