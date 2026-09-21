package net.reichholf.dreamdroid.ui.current

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.loadCurrentService
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.video.startLiveServiceStream

private const val POLL_MS = 30_000L
private const val PROFILE_WAIT_MS = 20_000L

/**
 * Polls `/web/getcurrent` into [hubState] for the Coordinator now-playing strip
 * and hosts [CurrentServiceSheet] on tap. No-op when
 * [DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP] is off.
 */
@Composable
fun HubNowPlaying(handle: PhoneNavHandle, reloadEpoch: Int, hubState: TvMoviesHubState) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var enabled by remember {
        mutableStateOf(prefs.getBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, true))
    }
    var profileId by remember {
        mutableIntStateOf(DreamDroid.getCurrentProfile().id ?: -1)
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP) {
                enabled = prefs.getBoolean(key, true)
            }
            if (key == DreamDroid.CURRENT_PROFILE) {
                profileId = prefs.getInt(DreamDroid.CURRENT_PROFILE, profileId)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    hubState.nowPlayingStripEnabled = enabled
    if (!enabled) {
        hubState.nowPlayingLabel = ""
        hubState.nowPlayingHeadline = ""
        hubState.nowPlayingProgress = 0f
        hubState.nowPlayingReference = ""
        hubState.nowPlayingName = ""
        hubState.onNowPlayingClick = {}
        return
    }

    val scope = rememberCoroutineScope()
    val gate = remember { CurrentServiceLoadGate() }
    var current by remember { mutableStateOf<CurrentService?>(null) }
    var ready by remember { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val loadingText = stringResource(R.string.loading)
    val session = SessionConnectionHolder.shared.status.collectAsState().value.session
    val sessionOffline = session == ConnectionStatus.Session.Offline
    val unavailableText = nowPlayingFallbackText(
        sessionOffline = sessionOffline,
        offlineText = stringResource(R.string.session_offline),
        unavailableText = stringResource(R.string.not_available)
    )
    val shown = current.takeIf {
        gate.lastGoodProfileId == profileId && !sessionOffline
    }

    fun reload() {
        if (SessionConnectionHolder.shared.status.value.session ==
            ConnectionStatus.Session.Offline
        ) {
            ready = true
            return
        }
        val generation = gate.beginLoad()
        val loadProfileId = DreamDroid.getCurrentProfile().id ?: -1
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadCurrentService(context.applicationContext)
            val next = result.current
            if (result.success && next != null) {
                gate.applySuccess(generation, loadProfileId, next)
            }
            if (gate.isCurrent(generation)) {
                current = gate.visible(DreamDroid.getCurrentProfile().id ?: -1)
                ready = true
            }
        }
    }

    fun stream() {
        if (!currentServiceCanStream(shown)) {
            return
        }
        handle.runOnlineOnly {
            val service = shown?.service
            val ref = service?.reference.orEmpty()
            val name = service?.name.orEmpty()
            val activity = context as AppCompatActivity
            activity.startLiveServiceStream(activity, ref) {
                activity.startActivity(IntentFactory.getStreamServiceIntent(activity, ref, name))
            }
        }
    }

    LaunchedEffect(profileId, session) {
        current = gate.visible(profileId)
        ready = current != null
        if (session == ConnectionStatus.Session.Offline) {
            ready = true
            return@LaunchedEffect
        }
        if (session != ConnectionStatus.Session.Online) {
            withTimeoutOrNull(PROFILE_WAIT_MS) {
                while (DreamDroid.getCurrentProfile().cachedDeviceInfo == null) {
                    delay(100)
                }
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

    val service = shown?.service
    val now = shown?.now
    hubState.nowPlayingLabel = nowPlayingLabelText(
        sessionOffline = sessionOffline,
        connectionText = stringResource(R.string.connection),
        currentServiceText = stringResource(R.string.current_service)
    )
    hubState.nowPlayingHeadline = nowPlayingHeadline(
        ready = ready,
        serviceName = service?.name.orEmpty(),
        eventTitle = now?.title.orEmpty(),
        loadingText = loadingText,
        unavailableText = unavailableText
    )
    hubState.nowPlayingProgress = eventProgressFraction(now)
    hubState.nowPlayingReference = service?.reference.orEmpty()
    hubState.nowPlayingName = service?.name.orEmpty()
    hubState.onNowPlayingClick = { showSheet = true }

    if (showSheet) {
        CurrentServiceSheet(
            current = shown,
            loading = shown == null && !ready,
            onStream = { stream() },
            onDismiss = {
                showSheet = false
                reload()
            }
        )
    }
}

/**
 * Last-good `/web/getcurrent` keyed by profile id. [beginLoad] stamps a generation so a
 * slower previous fetch cannot paint after a newer reload.
 */
class CurrentServiceLoadGate {
    private var loadGeneration = 0
    var lastGood: CurrentService? = null
        private set
    var lastGoodProfileId: Int? = null
        private set

    fun beginLoad(): Int = ++loadGeneration

    fun isCurrent(generation: Int): Boolean = generation == loadGeneration

    fun applySuccess(generation: Int, profileId: Int, next: CurrentService): Boolean {
        if (generation != loadGeneration) {
            return false
        }
        if (next.isEmpty()) {
            return false
        }
        lastGood = next
        lastGoodProfileId = profileId
        return true
    }

    fun visible(profileId: Int): CurrentService? = lastGood.takeIf {
        lastGoodProfileId == profileId
    }
}

/** Stream is only valid when `/web/getcurrent` gave a non-empty service reference. */
fun currentServiceCanStream(current: CurrentService?): Boolean =
    current?.service?.reference?.isNotEmpty() == true
