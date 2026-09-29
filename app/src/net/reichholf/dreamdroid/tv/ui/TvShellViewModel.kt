package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded

/**
 * The TV ProfileCheck [gate]; null until Room answered whether the active profile has a
 * cache, so Checking never flashes over content Room can paint.
 */
data class TvShellUiState(val gate: TvSessionGate? = null)

/**
 * The TV activity's session: checks the active profile when the hub starts and on every
 * profile switch, rechecks on request, and probes reachability while the activity is
 * resumed. The outcome is the shared [SessionConnectionHolder] status, which the hub reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvShellViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val checks: ProfileCheckRepository,
    private val services: ServiceRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    /**
     * Whether Room can paint for which profile id. Refreshed on each profile or session
     * change; unknown until Room answered.
     */
    private val cache: StateFlow<Pair<Int?, Boolean?>> =
        combine(profiles.current, sessions.status.map { it.session }.distinctUntilChanged()) {
                profile,
                _
            ->
            profile?.id
        }
            .mapLatest<Int?, Pair<Int?, Boolean?>> { id ->
                id to (id?.let { services.hasCache(it) } ?: false)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UNKNOWN_CACHE)

    val uiState: StateFlow<TvShellUiState> = combine(
        sessions.status,
        profiles.current,
        cache
    ) { status, profile, (cacheProfileId, hasCache) ->
        val known = hasCache.takeIf { profile?.id == cacheProfileId }
        TvShellUiState(
            gate = known?.let {
                tvSessionGate(
                    status = status,
                    hasCache = it,
                    receiverLabel = profile?.let { p -> "${p.user}@${p.host}:${p.port}" }.orEmpty()
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TvShellUiState())

    private var checkJob: Job? = null
    private var switchesJob: Job? = null
    private var checkedProfile: Profile? = null

    /**
     * Follows profile switches from now on and checks the active profile, unless it is the
     * one this ViewModel already checked (the activity was recreated).
     */
    fun start() {
        if (switchesJob == null) {
            switchesJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                profiles.switches.collect { onProfileChanged(it) }
            }
        }
        val profile = profiles.requireCurrent()
        if (profile !== checkedProfile) {
            onProfileChanged(profile)
        }
    }

    /** Asks the receiver again, dropping its cached device info. */
    fun recheck() {
        startCheck(profiles.requireCurrent(), reuseDeviceInfo = false)
    }

    /** Drops a running check, for example when the active profile is gone. */
    fun cancelCheck() {
        checkJob?.cancel()
        checkJob = null
    }

    /**
     * One reachability probe: Online can become Offline and Offline can recover without
     * reselecting the profile. Skipped while a check runs, and for failures polling cannot
     * fix. Does not show Checking.
     */
    suspend fun probeReachability() {
        val active = profiles.current.value ?: return
        probeSessionReachabilityIfNeeded(
            holder = sessions,
            hasCache = hasCache(active),
            isBusy = { checkJob != null },
            check = { checks.check(profiles.current.value ?: active) }
        )
    }

    private fun onProfileChanged(profile: Profile) {
        if (profile.id != checkedProfile?.id) {
            sessions.resetForProfileChange()
        }
        val reuse = profiles.deviceInfo(profile) != null
        if (!reuse && profile === checkedProfile && checkJob != null) {
            return
        }
        checkedProfile = profile
        startCheck(profile, reuse)
    }

    private fun startCheck(profile: Profile, reuseDeviceInfo: Boolean) {
        checkJob?.cancel()
        // Cached device info answers without asking the receiver: no Checking flash.
        if (!reuseDeviceInfo) {
            sessions.beginChecking()
        }
        checkJob = viewModelScope.launch {
            val result = if (reuseDeviceInfo) {
                checks.checkReusingDeviceInfo(profile)
            } else {
                checks.check(profile)
            }
            checkJob = null
            apply(profile, result)
        }
    }

    private suspend fun apply(profile: Profile, result: ProfileCheckResult) {
        if (profiles.current.value == null) return
        sessions.applyProfileCheckResult(result, hasCache(profile))
    }

    private suspend fun hasCache(profile: Profile): Boolean =
        profile.id?.let { services.hasCache(it) } ?: false

    private companion object {
        val UNKNOWN_CACHE: Pair<Int?, Boolean?> = Pair(-1, null)
    }
}
