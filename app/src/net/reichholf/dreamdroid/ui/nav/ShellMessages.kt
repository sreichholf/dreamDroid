package net.reichholf.dreamdroid.ui.nav

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Mutation copy for the shell snackbar. A non-blank box `statetext` wins, including
 * [net.reichholf.dreamdroid.enigma.EnigmaFailure.BoxRejected].
 */
fun mutationResultText(stateText: String?, errorText: String?, fallback: String): String = when {
    !stateText.isNullOrEmpty() -> stateText
    !errorText.isNullOrEmpty() -> errorText
    else -> fallback
}

/**
 * One-shot messages for the phone shell and the TV hub. Callers that are not
 * composable (navigation helper, hub pages, TV mutation callbacks) post here.
 * The shell collects them into a [SnackbarHostState].
 */
object ShellMessages {
    private val pending = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = pending.asSharedFlow()

    fun post(message: CharSequence?) {
        val text = message?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            return
        }
        pending.tryEmit(text)
    }
}

@Composable
fun ShellSnackbarHost(modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        ShellMessages.messages.collect { text ->
            hostState.showSnackbar(text)
        }
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}
