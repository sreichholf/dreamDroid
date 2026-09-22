package net.reichholf.dreamdroid.ui.pick

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

/**
 * Phase 2.7f: bouquet/service picker as a direct Compose NavHost destination.
 * Result Intent carries typed [net.reichholf.dreamdroid.enigma.Service] as [KEY_BOUQUET].
 * The list and load job live on [PickServiceViewModel].
 */
@Composable
fun PickServiceDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: PickServiceViewModel = viewModel()
) {
    val context = LocalContext.current
    val title = viewModel.toolbarTitle

    LaunchedEffect(title) {
        (context as? AppCompatActivity)?.title = title
    }
    LaunchedEffect(viewModel) {
        viewModel.start()
    }

    DreamDroidPullRefresh(
        refreshing = viewModel.refresh.isRefreshing,
        onRefresh = { viewModel.reload() },
        enabled = viewModel.refresh.enabled,
        modifier = modifier
    ) {
        PickServiceScreen(
            items = viewModel.listState.items,
            emptyMessage = viewModel.emptyMessage,
            onItemClick = { service ->
                val data = Intent().apply {
                    putExtra(KEY_BOUQUET, service)
                }
                handle.deliverPickResult(Activity.RESULT_OK, data)
            }
        )
    }
}

const val KEY_BOUQUET = "bouquet"
