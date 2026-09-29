package net.reichholf.dreamdroid.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.text.asString

/**
 * The shell's [SnackbarHostState] (phone shell and TV hub). Destinations whose ViewModel
 * keeps its user message in UI state show it here with [ShowShellUserMessage]. Null
 * outside a shell.
 */
val LocalShellSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

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
