package net.reichholf.dreamdroid.ui.signal

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/**
 * Signal meter as a NavHost destination (also a Tools hub tab). The meter polls and keeps
 * the screen on while this destination is composed and started.
 */
@Composable
fun SignalDestination(
    modifier: Modifier = Modifier,
    handle: PhoneNavHandle? = null,
    viewModel: SignalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    val window = LocalActivity.current?.window
    LifecycleStartEffect(viewModel, window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.onShown()
        onStopOrDispose {
            viewModel.onHidden()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    SignalScreen(
        state = uiState,
        onEnabledChange = { enabled ->
            if (uiState.blocked) {
                handle?.requestNeedsReceiver()
            }
            viewModel.onEnabledChange(enabled)
        },
        onAcousticChange = viewModel::onAcousticChange,
        modifier = modifier
    )
}
