package net.reichholf.dreamdroid.ui.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A ViewModel-owned [TextFieldState]. The UI edits [state] directly, so typing never
 * waits for a StateFlow round trip.
 *
 * With a [handle], the text is kept under [key] and restored from it, so it survives
 * process death. It is stored as a plain String rather than through `saveable`, whose
 * Bundle JVM tests only have as a stub. [onEdit] runs on [scope] after the text changed
 * other than through [set], i.e. when the user typed.
 */
class SavedTextField(
    scope: CoroutineScope,
    private val handle: SavedStateHandle?,
    private val key: String,
    initial: String = "",
    private val onEdit: (String) -> Unit = {}
) {
    val state = TextFieldState(handle?.get<String>(key) ?: initial)

    private var known: String = state.text.toString()

    val text: String
        get() = state.text.toString()

    init {
        handle?.set(key, known)
        scope.launch {
            snapshotFlow { state.text.toString() }.collect { text ->
                if (text != known) {
                    known = text
                    handle?.set(key, text)
                    onEdit(text)
                }
            }
        }
    }

    /** Replaces the text without counting as a user edit. */
    fun set(text: String) {
        known = text
        handle?.set(key, text)
        state.setTextAndPlaceCursorAtEnd(text)
    }
}
