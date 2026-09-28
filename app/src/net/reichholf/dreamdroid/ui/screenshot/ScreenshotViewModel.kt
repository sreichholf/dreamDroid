package net.reichholf.dreamdroid.ui.screenshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * Screenshot of the receiver. [image] holds the JPEG bytes that the screen decodes and that
 * share and save write out. [blocked] mirrors the session's `blocksMutations`, which blocks
 * grabs.
 */
data class ScreenshotUiState(
    val image: ByteArray? = null,
    val loading: Boolean = false,
    val blocked: Boolean = false,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.screenshot)
}

/**
 * Grabs a screenshot when created and on [refresh]. The bytes stay in memory for the life
 * of this ViewModel; they are too large for `SavedStateHandle`.
 */
@HiltViewModel
class ScreenshotViewModel @Inject constructor(
    private val receiver: ReceiverRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ScreenshotUiState(blocked = sessions.status.value.blocksMutations)
    )
    val uiState: StateFlow<ScreenshotUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(blocked = status.blocksMutations) }
            }
        }
        refresh()
    }

    /** Grabs a new screenshot; does nothing while blocked. */
    fun refresh() {
        if (sessions.status.value.blocksMutations) {
            return
        }
        loadJob?.cancel()
        _uiState.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val response = receiver.screenshot()
            val image = response.value
            if (image == null) {
                val failure = response.error?.failure?.userMessageText()
                val message = failure?.takeUnless { it == UiText.Raw("") }
                    ?: UiText.Resource(R.string.error)
                _uiState.update { it.copy(loading = false, userMessage = message) }
                return@launch
            }
            _uiState.update { it.copy(image = image, loading = false) }
        }
    }

    fun onSaved(fileName: String) {
        _uiState.update {
            it.copy(userMessage = UiText.Resource(R.string.screenshot_saved, listOf(fileName)))
        }
    }

    /** Writing the image for save or share failed. */
    fun onFileFailed() {
        _uiState.update { it.copy(userMessage = UiText.Resource(R.string.error)) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
