package net.reichholf.dreamdroid.ui.device

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh

/**
 * Phase 2.7b: Device Info as a direct Compose NavHost destination (no nested Fragment).
 * The load and saved model live on [DeviceInfoViewModel].
 */
@Composable
fun DeviceInfoDestination(
    modifier: Modifier = Modifier,
    viewModel: DeviceInfoViewModel = viewModel()
) {
    val context = LocalContext.current
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
    LaunchedEffect(Unit) {
        viewModel.start()
    }
    DreamDroidPullRefresh(
        refreshing = viewModel.refreshing,
        onRefresh = { viewModel.reload() },
        enabled = true,
        modifier = modifier
    ) {
        DeviceInfoScreen(state = viewModel.uiState)
    }
}
