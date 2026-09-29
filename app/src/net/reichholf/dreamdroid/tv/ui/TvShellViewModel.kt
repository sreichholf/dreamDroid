package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded

/**
 * The TV activity's session: checks the active profile when the hub starts and on every
 * profile switch, rechecks on request, and probes reachability while the activity is
 * resumed. The outcome is the shared [SessionConnectionHolder] status, which the hub reads.
 */
@HiltViewModel
class TvShellViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val checks: ProfileCheckRepository,
    private val services: ServiceRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
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
}
