package net.reichholf.dreamdroid.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStreamAutoStart
import net.reichholf.dreamdroid.data.LastStreamStart
import net.reichholf.dreamdroid.data.PluginPresence
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverPluginsRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.PowerCommand
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.shouldConsumeVolumeKey
import net.reichholf.dreamdroid.helpers.enigma2.volumeCommandForKey
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckCheckingUi
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckFailedUi
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * Shell-wide results and one-shot effects. Effects stay until the shell handles them.
 *
 * [profileCheck] is what the profile-check gate shows. [profileCheckStarted] and
 * [profileCheckOutcome] tell the shell to navigate: a started check may open the gate,
 * and a finished one opens the failed gate or leaves it.
 */
data class ShellUiState(
    val userMessage: UiText? = null,
    val sleepTimerEffect: SleepTimer? = null,
    val profileName: String = "",
    val profileCheck: ProfileCheckUi = CHECKING,
    val profileCheckStarted: ProfileCheckStart? = null,
    val profileCheckOutcome: ProfileCheckOutcome? = null,
    /** Open the setup assistant: the no-profile gate's Add Profile. */
    val setupRequested: Boolean = false,
    /** The receiver has the AutoTimer plugin, so the drawer lists AutoTimer. */
    val autoTimerInDrawer: Boolean = false,
    /** The active profile's web interface has the sleep timer, so the drawer offers it. */
    val sleepTimerInDrawer: Boolean = true,
    /** Play this saved channel: the launch's first profile check found the receiver online. */
    val lastStreamEffect: LastStreamStart.Play? = null
)

/** A profile check started. [showGate]: open the gate, which shows Checking. */
data class ProfileCheckStart(val showGate: Boolean)

/** Where a finished profile check sends the shell. */
sealed interface ProfileCheckOutcome {
    /** True until the first check was handled: open the drawer, and Profiles on leave. */
    val firstStart: Boolean

    /** Open the gate, which shows [ShellUiState.profileCheck] as failed. */
    data class Failed(override val firstStart: Boolean) : ProfileCheckOutcome

    /**
     * Leave the gate for the start route (Profiles on the first start). When [offGateToo],
     * also go there if the gate is not showing.
     */
    data class Leave(val offGateToo: Boolean, override val firstStart: Boolean) :
        ProfileCheckOutcome
}

private val CHECKING = ProfileCheckUi.Checking(UiText.Resource(R.string.checking_connection))

/**
 * The phone shell's session and actions. Activity-scoped so a configuration change does not
 * cancel a request.
 *
 * Checks the active profile when the shell asks ([checkActiveProfile], [recheck]) and on
 * every profile switch after [start], and probes reachability while the shell is resumed.
 * The connection outcome is the shared [SessionConnectionHolder] status; the gate outcome is
 * [ShellUiState]. Power, sleep timer, send message, and volume keys go to the receiver;
 * their messages show in the shell snackbar and the sleep timer effect opens its dialog.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val receiver: ReceiverRepository,
    private val plugins: ReceiverPluginsRepository,
    private val profiles: ProfileRepository,
    private val checks: ProfileCheckRepository,
    private val services: ServiceRepository,
    private val sessions: SessionConnectionHolder,
    private val settings: SettingsRepository,
    private val lastStreamStart: LastLiveStreamAutoStart,
    capabilities: WebIfCapabilitiesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    private var powerJob: Job? = null
    private var sleepJob: Job? = null
    private var messageJob: Job? = null
    private var volumeJob: Job? = null
    private var checkJob: Job? = null
    private var switchesJob: Job? = null
    private var checkedProfile: Profile? = null

    /** A successful check opens the start route even off the gate (after the failed gate). */
    private var openStartOnSuccess = false

    /** The next check outcome may start the last channel; see [startLastStreamAfterFirstCheck]. */
    private var lastStreamPending = false

    init {
        viewModelScope.launch {
            // Losing the active profile (the last one deleted) turns the gate into NoProfile.
            // A null that was there from the start is the first run; setup, not a loss.
            var hadProfile = false
            profiles.current.collect { profile ->
                val lost = hadProfile && profile == null
                hadProfile = hadProfile || profile != null
                if (lost) {
                    cancelCheck()
                    _uiState.update {
                        it.copy(
                            profileName = "",
                            profileCheck = ProfileCheckUi.NoProfile,
                            profileCheckStarted = ProfileCheckStart(showGate = true),
                            profileCheckOutcome = null
                        )
                    }
                    return@collect
                }
                _uiState.update { state ->
                    state.copy(
                        profileName = profile?.name.orEmpty(),
                        profileCheck = if (
                            profile != null && state.profileCheck is ProfileCheckUi.NoProfile
                        ) {
                            CHECKING
                        } else {
                            state.profileCheck
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(profiles.current, capabilities.all) { profile, all ->
                profile == null || profile.id?.let(all::get)?.sleepTimer != false
            }.distinctUntilChanged().collect { available ->
                _uiState.update { it.copy(sleepTimerInDrawer = available) }
            }
        }
        viewModelScope.launch {
            plugins.autoTimerPresence.collect { presence ->
                _uiState.update {
                    it.copy(autoTimerInDrawer = presence == PluginPresence.Present)
                }
            }
        }
    }

    /**
     * Follows profile switches from now on. The shell calls it once it exists; the setup
     * assistant's switch before that is dropped, because the shell checks on its own.
     */
    fun start() {
        if (switchesJob != null) {
            return
        }
        switchesJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            profiles.switches.collect { checkProfile(it, showGate = false) }
        }
    }

    /** Checks the active profile, reusing its device-info answer when there is one. */
    fun checkActiveProfile() {
        val profile = profiles.current.value ?: return
        checkProfile(profile, showGate = false)
    }

    /** The gate's Recheck: shows Checking and asks the receiver again. */
    fun recheck() {
        val profile = profiles.current.value ?: return
        profiles.setDeviceInfo(profile, null)
        checkProfile(profile, showGate = true)
    }

    /**
     * The app launched: the next profile check, whatever its outcome, decides once whether
     * the last channel starts. A later check or profile switch does not.
     */
    fun startLastStreamAfterFirstCheck() {
        lastStreamPending = true
    }

    fun onLastStreamEffectHandled() {
        _uiState.update { it.copy(lastStreamEffect = null) }
    }

    /** The setup assistant finished; its profile is not a first start any more. */
    fun onSetupFinished() {
        settings.firstStart = false
    }

    /** The failed gate's Profiles: a later success no longer leaves Profiles. */
    fun onProfilesFromGate() {
        openStartOnSuccess = false
    }

    /** The no-profile gate's Add Profile: the shell opens the setup assistant. */
    fun requestSetup() {
        // The shell already ran, so the added profile is not this process's first start:
        // its check leaves for the start route instead of Profiles with the drawer.
        settings.firstStart = false
        _uiState.update { it.copy(setupRequested = true) }
    }

    fun onSetupRequestedHandled() {
        _uiState.update { it.copy(setupRequested = false) }
    }

    /** Drops a running check, for example when the shell stops. */
    fun cancelCheck() {
        checkJob?.cancel()
        checkJob = null
        sessions.cancelChecking()
    }

    /**
     * One reachability probe: Online can become Offline and Offline can recover without
     * reselecting the profile. Skipped while a check runs, and for failures polling cannot
     * fix. Does not show Checking. True when a probe ran.
     */
    suspend fun probeReachability(): Boolean {
        val active = profiles.current.value ?: return false
        return probeSessionReachabilityIfNeeded(
            holder = sessions,
            hasCache = hasCache(active),
            isBusy = { checkJob != null },
            check = { checks.check(profiles.current.value ?: active) }
        )
    }

    fun onProfileCheckStartHandled() {
        _uiState.update { it.copy(profileCheckStarted = null) }
    }

    fun onProfileCheckOutcomeHandled() {
        val outcome = _uiState.value.profileCheckOutcome ?: return
        if (outcome.firstStart) {
            settings.firstStart = false
        }
        _uiState.update { it.copy(profileCheckOutcome = null) }
    }

    /**
     * Whether the volume key [keyCode] sets the receiver's volume instead of the phone's.
     * Without a profile nothing commands the receiver, so the key stays the phone's.
     */
    fun controlsReceiverVolume(keyCode: Int): Boolean = profiles.current.value != null &&
        shouldConsumeVolumeKey(keyCode, settings.current().volumeControl)

    /** Sets the receiver's volume for [keyCode]. A key while a request runs is dropped. */
    fun onVolumeKey(keyCode: Int) {
        if (volumeJob?.isActive == true) {
            return
        }
        val command = volumeCommandForKey(keyCode) ?: return
        volumeJob = viewModelScope.launch { receiver.setVolume(command) }
    }

    fun onPowerMenuAction(itemId: Int) {
        val command = when (itemId) {
            Statics.ITEM_TOGGLE_STANDBY -> PowerCommand.ToggleStandby
            Statics.ITEM_RESTART_GUI -> PowerCommand.RestartGui
            Statics.ITEM_REBOOT -> PowerCommand.Reboot
            Statics.ITEM_SHUTDOWN -> PowerCommand.Shutdown
            else -> return
        }
        setPowerState(command)
    }

    fun setPowerState(command: PowerCommand) {
        powerJob?.cancel()
        powerJob = viewModelScope.launch {
            val response = receiver.setPowerState(command)
            val error = response.error
            _uiState.update {
                it.copy(
                    userMessage = when {
                        error != null -> error.contentErrorText()

                        response.value?.isRunning == true ->
                            UiText.Resource(R.string.is_running)

                        else -> UiText.Resource(R.string.in_standby)
                    }
                )
            }
        }
    }

    fun loadSleepTimerForDialog() {
        sleepJob?.cancel()
        sleepJob = viewModelScope.launch {
            publishSleep(receiver.sleepTimer(), openDialog = true)
        }
    }

    fun setSleepTimer(time: String?, action: String?, enabled: Boolean) {
        sleepJob?.cancel()
        sleepJob = viewModelScope.launch {
            publishSleep(receiver.setSleepTimer(time, action, enabled), openDialog = false)
        }
    }

    fun sendMessage(text: String?, type: String?, timeout: String?) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            val response = receiver.sendMessage(text, type, timeout)
            messageJob = null
            _uiState.update { it.copy(userMessage = response.userMessageText()) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * A message from another destination that outlives its screen, shown in
     * the shell snackbar.
     */
    fun showMessage(message: UiText) {
        _uiState.update { it.copy(userMessage = message) }
    }

    fun onSleepTimerEffectHandled() {
        _uiState.update { it.copy(sleepTimerEffect = null) }
    }

    /**
     * Without a device-info answer, asks the receiver and shows Checking on the gate unless
     * the start route can paint from cache. With one, reuses it. A new check replaces an
     * outcome the shell has not handled yet.
     */
    private fun checkProfile(profile: Profile, showGate: Boolean) {
        if (profile.id != checkedProfile?.id) {
            sessions.resetForProfileChange()
        }
        val reuse = profiles.deviceInfo(profile) != null
        if (!reuse && profile === checkedProfile && checkJob != null) {
            return
        }
        checkedProfile = profile
        checkJob?.cancel()
        _uiState.update {
            it.copy(
                profileCheck = if (showGate) CHECKING else it.profileCheck,
                profileCheckOutcome = null
            )
        }
        checkJob = viewModelScope.launch {
            val gate = showGate || (!reuse && shouldShowProfileCheckCheckingUi(hasCache(profile)))
            _uiState.update {
                it.copy(
                    profileCheck = if (gate) CHECKING else it.profileCheck,
                    profileCheckStarted = ProfileCheckStart(showGate = gate)
                )
            }
            val result = if (reuse) {
                checks.checkReusingDeviceInfo(profile)
            } else {
                sessions.beginChecking()
                checks.check(profile)
            }
            checkJob = null
            onChecked(profile, result)
        }
    }

    private suspend fun onChecked(profile: Profile, result: ProfileCheckResult) {
        if (profiles.current.value == null) {
            return
        }
        val startLastStream = lastStreamPending
        lastStreamPending = false
        val hasCache = hasCache(profile)
        sessions.applyProfileCheckResult(result, hasCache)
        val firstStart = settings.firstStart
        val outcome = if (result.hasError && !result.isSoftError) {
            if (shouldShowProfileCheckFailedUi(hasCache, result.failure)) {
                openStartOnSuccess = true
                _uiState.update { it.copy(profileCheck = failedGate(profile, result)) }
                ProfileCheckOutcome.Failed(firstStart)
            } else {
                ProfileCheckOutcome.Leave(offGateToo = false, firstStart = firstStart)
            }
        } else {
            viewModelScope.launch { plugins.refresh() }
            val openStart = openStartOnSuccess
            openStartOnSuccess = false
            ProfileCheckOutcome.Leave(offGateToo = openStart || firstStart, firstStart = firstStart)
        }
        _uiState.update { it.copy(profileCheckOutcome = outcome) }
        if (startLastStream) {
            publishLastStreamStart()
        }
    }

    private suspend fun publishLastStreamStart() {
        when (val start = lastStreamStart.streamToStart()) {
            is LastStreamStart.Play -> _uiState.update { it.copy(lastStreamEffect = start) }
            is LastStreamStart.Failed -> showMessage(start.message)
            null -> Unit
        }
    }

    private fun failedGate(profile: Profile, result: ProfileCheckResult): ProfileCheckUi.Failed =
        ProfileCheckUi.Failed(
            title = UiText.Raw("${profile.user}@${profile.host}:${profile.port}"),
            message = result.errorText ?: UiText.Resource(result.errorTextId)
        )

    private suspend fun hasCache(profile: Profile): Boolean =
        profile.id?.let { services.hasCache(it) } ?: false

    private fun publishSleep(response: EnigmaResponse<SleepTimer>, openDialog: Boolean) {
        val timer = response.value
        val error = response.error
        when {
            error != null -> showMessage(error.contentErrorText())

            timer?.enabled == null ->
                showMessage(UiText.Resource(R.string.get_content_error))

            openDialog -> _uiState.update { it.copy(sleepTimerEffect = timer) }

            else -> timer.text?.let { showMessage(UiText.Raw(it)) }
        }
    }
}
