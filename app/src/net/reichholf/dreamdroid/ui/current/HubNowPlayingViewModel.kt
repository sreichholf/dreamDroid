package net.reichholf.dreamdroid.ui.current

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.CurrentServiceLoadResult
import net.reichholf.dreamdroid.enigma.loadCurrentService
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

private const val POLL_MS = 30_000L
private const val PROFILE_WAIT_MS = 20_000L

/**
 * `/web/getcurrent` for [HubNowPlaying], scoped to the hub back-stack entry so the
 * last-good service is still painted when the hub is re-entered. Polling runs only
 * while [poll] is collected from composition; the load itself is on [viewModelScope].
 */
class HubNowPlayingViewModel(
    application: Application,
    val sessions: SessionConnectionHolder,
    private val load: suspend (Context) -> CurrentServiceLoadResult
) : AndroidViewModel(application) {
    constructor(application: Application) :
        this(application, SessionConnectionHolder.shared, ::loadCurrentService)

    var enabled by mutableStateOf(true)
        private set

    var profileId by mutableIntStateOf(-1)
        private set

    var current by mutableStateOf<CurrentService?>(null)
        private set

    var ready by mutableStateOf(false)
        private set

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val gate = CurrentServiceLoadGate()
    private var loadJob: Job? = null
    private var lastSuccessAtMs: Long? = null
    private var handledReloadEpoch = 0
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP) {
            enabled = prefs.getBoolean(key, true)
        }
        if (key == DreamDroid.CURRENT_PROFILE) {
            profileId = prefs.getInt(DreamDroid.CURRENT_PROFILE, profileId)
        }
    }

    init {
        enabled = prefs.getBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, true)
        profileId = currentProfileId()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun shown(sessionOffline: Boolean): CurrentService? =
        current.takeIf { gate.lastGoodProfileId == profileId && !sessionOffline }

    /** Paints the last-good service for [profileId], then reloads every [POLL_MS]. */
    suspend fun poll(session: ConnectionStatus.Session?) {
        current = gate.visible(profileId)
        ready = current != null
        if (session == ConnectionStatus.Session.Offline) {
            ready = true
            return
        }
        if (session != ConnectionStatus.Session.Online) {
            withTimeoutOrNull(PROFILE_WAIT_MS) {
                while (ProfileRepository.get().deviceInfo(
                        ProfileRepository.get().requireCurrent()
                    ) == null
                ) {
                    delay(100)
                }
            }
        }
        val lastSuccess = lastSuccessAtMs
        if (current != null && lastSuccess != null) {
            delay(POLL_MS - (SystemClock.elapsedRealtime() - lastSuccess))
        }
        while (true) {
            reload()
            delay(POLL_MS)
        }
    }

    /** [epoch] is the hub's saved zap counter; re-entering the hub must not reload again. */
    fun onReloadEpoch(epoch: Int) {
        if (epoch <= handledReloadEpoch) {
            return
        }
        handledReloadEpoch = epoch
        reload()
    }

    fun reload() {
        if (sessions.status.value.session == ConnectionStatus.Session.Offline) {
            ready = true
            return
        }
        val generation = gate.beginLoad()
        val loadProfileId = currentProfileId()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = load(getApplication())
            val next = result.current
            if (result.success && next != null &&
                gate.applySuccess(generation, loadProfileId, next)
            ) {
                lastSuccessAtMs = SystemClock.elapsedRealtime()
            }
            if (gate.isCurrent(generation)) {
                current = gate.visible(currentProfileId())
                ready = true
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    private fun currentProfileId(): Int = ProfileRepository.get().requireCurrent().id ?: -1
}
