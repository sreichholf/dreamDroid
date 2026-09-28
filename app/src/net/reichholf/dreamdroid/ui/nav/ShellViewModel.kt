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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.PowerState as PowerStateKeys
import net.reichholf.dreamdroid.ui.text.UiText

/** Shell-wide results and one-shot effects. Effects stay until the shell handles them. */
data class ShellUiState(
    val userMessage: UiText? = null,
    val sleepTimerEffect: SleepTimer? = null,
    val profileSwitchEffect: Profile? = null
)

/**
 * Power, sleep timer, and send message. Activity-scoped so a configuration
 * change does not cancel the request. Results are [ShellUiState]: messages
 * show in the shell snackbar, the sleep timer effect opens its dialog, and
 * the profile switch effect runs the profile check.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    private var powerJob: Job? = null
    private var sleepJob: Job? = null
    private var messageJob: Job? = null

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            profiles.switches.collect { profile ->
                _uiState.update { it.copy(profileSwitchEffect = profile) }
            }
        }
    }

    fun onPowerMenuAction(itemId: Int) {
        val state = when (itemId) {
            Statics.ITEM_TOGGLE_STANDBY -> PowerStateKeys.STATE_TOGGLE
            Statics.ITEM_RESTART_GUI -> PowerStateKeys.STATE_GUI_RESTART
            Statics.ITEM_REBOOT -> PowerStateKeys.STATE_SYSTEM_REBOOT
            Statics.ITEM_SHUTDOWN -> PowerStateKeys.STATE_SHUTDOWN
            else -> return
        }
        setPowerState(state)
    }

    fun setPowerState(state: String) {
        powerJob?.cancel()
        powerJob = viewModelScope.launch {
            val response = receiver.setPowerState(state)
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

    fun onProfileSwitchHandled() {
        _uiState.update { it.copy(profileSwitchEffect = null) }
    }

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
