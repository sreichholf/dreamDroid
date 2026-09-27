package net.reichholf.dreamdroid.ui.nav

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot user messages for the phone shell, the TV hub, and the player. In-app
 * results post here instead of using `Toast`; each host collects them into a
 * [SnackbarHostState].
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
    val lifecycleOwner = LocalLifecycleOwner.current
    // Only a started host collects: a stopped shell under the player must not queue
    // (and later replay) the player's messages.
    LaunchedEffect(hostState, lifecycleOwner) {
        ShellMessages.messages
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { text -> hostState.showSnackbar(text) }
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}
