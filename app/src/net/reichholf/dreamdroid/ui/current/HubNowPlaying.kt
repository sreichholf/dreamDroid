package net.reichholf.dreamdroid.ui.current

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.loadCurrentService
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState

private const val POLL_MS = 30_000L
private const val PROFILE_WAIT_MS = 20_000L

/**
 * Polls `/web/getcurrent` into [hubState] for the Coordinator now-playing strip
 * and hosts [CurrentServiceSheet] on tap.
 */
@Composable
fun HubNowPlaying(
    hostFragment: PhoneNavHostFragment,
    reloadEpoch: Int,
    hubState: TvMoviesHubState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<CurrentService?>(null) }
    var ready by remember { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val loadingText = stringResource(R.string.loading)
    val unavailableText = stringResource(R.string.not_available)

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadCurrentService(context.applicationContext)
            val next = result.current
            if (result.success && next != null && !next.isEmpty()) {
                current = next
            }
            ready = true
        }
    }

    LaunchedEffect(Unit) {
        withTimeoutOrNull(PROFILE_WAIT_MS) {
            while (DreamDroid.getCurrentProfile().cachedDeviceInfo == null) {
                delay(100)
            }
        }
        reload()
        while (true) {
            delay(POLL_MS)
            reload()
        }
    }

    LaunchedEffect(reloadEpoch) {
        if (reloadEpoch > 0) {
            reload()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            loadJob?.cancel()
            loadJob = null
        }
    }

    val service = current?.service
    val now = current?.now
    hubState.nowPlayingHeadline = nowPlayingHeadline(
        ready = ready,
        serviceName = service?.name.orEmpty(),
        eventTitle = now?.title.orEmpty(),
        loadingText = loadingText,
        unavailableText = unavailableText,
    )
    hubState.nowPlayingProgress = eventProgressFraction(now)
    hubState.nowPlayingReference = service?.reference.orEmpty()
    hubState.nowPlayingName = service?.name.orEmpty()
    hubState.onNowPlayingClick = { showSheet = true }

    if (showSheet) {
        CurrentServiceSheet(
            hostFragment = hostFragment,
            onDismiss = {
                showSheet = false
                reload()
            },
        )
    }
}
