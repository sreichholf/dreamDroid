package net.reichholf.dreamdroid.ui.signal

import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

internal class SignalPollGate {
    var generation: Int = 0
        private set
    var active: Boolean = false
        private set

    fun start() {
        active = true
    }

    fun stop() {
        active = false
        generation++
    }

    fun nextLoadGeneration(): Int {
        generation++
        return generation
    }

    fun isCurrent(generation: Int): Boolean = active && generation == this.generation
}

/**
 * Phase 2.7c: Signal meter as a direct Compose NavHost destination (no nested Fragment).
 * The poll loop and acoustic tone live on [SignalViewModel].
 */
@Composable
fun SignalDestination(
    handle: PhoneNavHandle? = null,
    modifier: Modifier = Modifier,
    viewModel: SignalViewModel = viewModel()
) {
    val context = LocalContext.current
    val status by SessionConnectionHolder.shared.status.collectAsState()
    val blocked = status.blocksMutations
    val title = viewModel.toolbarTitle
    val error = viewModel.errorText

    LaunchedEffect(title) {
        (context as? AppCompatActivity)?.title = title
    }
    LaunchedEffect(error) {
        if (!error.isNullOrEmpty()) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }

    DisposableEffect(blocked) {
        val activity = context as? AppCompatActivity
        activity?.title = context.getString(R.string.signal_meter)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (blocked) {
            viewModel.stopPolling(clearMeter = true)
        } else {
            viewModel.startPolling()
        }
        activity?.title = viewModel.toolbarTitle
        onDispose {
            viewModel.stopPolling(clearMeter = false)
            activity?.title = viewModel.toolbarTitle
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    SignalScreen(
        state = viewModel.uiState,
        meterBlocked = blocked,
        onEnabledChange = { enabled ->
            if (blocked) {
                handle?.requestNeedsReceiver()
            }
            viewModel.onEnabledChange(enabled)
        },
        onAcousticChange = viewModel::onAcousticChange,
        modifier = modifier
    )
}
