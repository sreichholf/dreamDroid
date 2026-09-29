package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.text.input.InputTransformation
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.reichholf.dreamdroid.ui.text.SavedTextField

data class SendMessageUiState(val typeIndex: Int = SendMessageViewModel.DEFAULT_TYPE_INDEX)

/**
 * The send-message form. Its text and choices survive rotation and process death; the
 * shell sends the message and reports the receiver's reply.
 */
@HiltViewModel
class SendMessageViewModel @Inject constructor(private val handle: SavedStateHandle) :
    ViewModel() {
    val message = SavedTextField(viewModelScope, handle, KEY_MESSAGE)
    val timeout = SavedTextField(viewModelScope, handle, KEY_TIMEOUT, DEFAULT_TIMEOUT)

    private val _uiState = MutableStateFlow(
        SendMessageUiState(typeIndex = handle[KEY_TYPE] ?: DEFAULT_TYPE_INDEX)
    )
    val uiState: StateFlow<SendMessageUiState> = _uiState.asStateFlow()

    fun onTypeSelected(index: Int) {
        handle[KEY_TYPE] = index
        _uiState.update { it.copy(typeIndex = index) }
    }

    companion object {
        const val DEFAULT_TYPE_INDEX = 2
        const val DEFAULT_TIMEOUT = "20"
        private const val KEY_MESSAGE = "send_message_text"
        private const val KEY_TIMEOUT = "send_message_timeout"
        private const val KEY_TYPE = "send_message_type"

        /** At most two digits: the receiver takes a timeout of up to 99 seconds. */
        val TimeoutInput = InputTransformation {
            if (length > 2 || !asCharSequence().all { it.isDigit() }) {
                revertAllChanges()
            }
        }
    }
}
