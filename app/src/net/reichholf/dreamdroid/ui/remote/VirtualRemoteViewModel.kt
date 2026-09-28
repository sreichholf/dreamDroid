package net.reichholf.dreamdroid.ui.remote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** The virtual remote pad. [screenshotEpoch] grows on every accepted key. */
data class VirtualRemoteUiState(
    val page: Int = 0,
    val simpleRemote: Boolean = false,
    val playAsPlayPause: Boolean = false,
    val keysBlocked: Boolean = false,
    val screenshotEpoch: Int = 0,
    val userMessage: UiText? = null
) {
    val layout: VirtualRemoteLayout
        get() = when {
            page == 1 -> VirtualRemoteLayout.QuickZap
            simpleRemote -> VirtualRemoteLayout.Simple
            else -> VirtualRemoteLayout.Full
        }

    val title: UiText
        get() = if (page == 1) {
            UiText.Resource(R.string.quickzap)
        } else {
            UiText.Resource(R.string.virtual_remote)
        }
}

/**
 * The virtual remote pad on the phone back-stack entry. The page survives
 * process death; the layout follows the page and the profile's remote type.
 * Accepted keys bump [VirtualRemoteUiState.screenshotEpoch] so the UI
 * reloads the screenshot, rejections become [VirtualRemoteUiState.userMessage].
 */
@HiltViewModel
class VirtualRemoteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder,
    settings: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        VirtualRemoteUiState(page = savedStateHandle[KEY_PAGE] ?: 0)
    )
    val uiState: StateFlow<VirtualRemoteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val simpleVrm = settings.settings.map { it.simpleVrm }.first()
            if (savedStateHandle.get<Int>(KEY_PAGE) == null) {
                _uiState.update { it.copy(page = if (simpleVrm) 0 else 1) }
            }
        }
        viewModelScope.launch {
            profiles.current.map { it?.simpleRemote ?: false }.distinctUntilChanged()
                .collect { simple -> _uiState.update { it.copy(simpleRemote = simple) } }
        }
        viewModelScope.launch {
            settings.settings.map { it.playButtonAsPlayPause }.distinctUntilChanged()
                .collect { play -> _uiState.update { it.copy(playAsPlayPause = play) } }
        }
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(keysBlocked = status.blocksMutations) }
            }
        }
    }

    fun onToggleLayout() {
        val page = if (_uiState.value.page == 0) 1 else 0
        savedStateHandle[KEY_PAGE] = page
        _uiState.update { it.copy(page = page) }
    }

    /** Sends the key; ignored while online-only actions are blocked. */
    fun onKey(keyCode: Int, longClick: Boolean) {
        if (sessions.status.value.blocksMutations) {
            return
        }
        viewModelScope.launch {
            val profile = profiles.requireCurrent()
            val response =
                receiver.remoteCommand(keyCode, profile.simpleRemote, longClick)
            val value = response.value
            val failed = response.error != null || value?.stateText.isNullOrEmpty() ||
                Python.FALSE == value?.state
            _uiState.update {
                it.copy(
                    userMessage = if (failed) {
                        response.userMessageText()
                    } else {
                        it.userMessage
                    },
                    screenshotEpoch = if (failed) {
                        it.screenshotEpoch
                    } else {
                        it.screenshotEpoch + 1
                    }
                )
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private companion object {
        const val KEY_PAGE = "virtual_remote_page"
    }
}
