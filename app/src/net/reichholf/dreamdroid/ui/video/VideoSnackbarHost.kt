package net.reichholf.dreamdroid.ui.video

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.ui.nav.LocalShellSnackbarHostState
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.text.UiText

/** The player's snackbar: shows [message], then calls [onMessageShown]. */
@Composable
fun VideoSnackbarHost(message: UiText?, onMessageShown: () -> Unit, modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalShellSnackbarHostState provides hostState) {
        ShowShellUserMessage(message, onMessageShown)
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}
