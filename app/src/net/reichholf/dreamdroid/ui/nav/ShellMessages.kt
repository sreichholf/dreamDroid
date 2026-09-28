package net.reichholf.dreamdroid.ui.nav

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.text.asString

/**
 * One-shot user messages for the phone shell and the TV hub. In-app results post here
 * instead of using `Toast`; each started host collects them into a [SnackbarHostState].
 * There is no replay: a message posted while no host is started (for example a mutation
 * that finishes with the app in the background) is dropped.
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

/**
 * The shell's [SnackbarHostState]. Destinations whose ViewModel keeps its user message in
 * UI state show it here with [ShowShellUserMessage]. Null outside a shell.
 */
val LocalShellSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

@Composable
fun ShellSnackbarHost(
    modifier: Modifier = Modifier,
    hostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Only a started host collects: a stopped shell under the player must not queue
    // (and later replay) messages.
    LaunchedEffect(hostState, lifecycleOwner) {
        ShellMessages.messages
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { text -> hostState.showSnackbar(text) }
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}

/**
 * Shows [message] in the shell snackbar, then calls [onShown] so the ViewModel clears it.
 * A message still in state when the destination leaves composition shows again on return.
 */
@Composable
fun ShowShellUserMessage(message: UiText?, onShown: () -> Unit) {
    val hostState = LocalShellSnackbarHostState.current ?: return
    val text = message?.asString()?.trim().orEmpty()
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(hostState, message) {
        if (message == null) {
            return@LaunchedEffect
        }
        if (text.isNotEmpty()) {
            hostState.showSnackbar(text)
        }
        currentOnShown()
    }
}
