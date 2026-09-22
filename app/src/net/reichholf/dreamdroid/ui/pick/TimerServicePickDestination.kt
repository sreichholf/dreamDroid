package net.reichholf.dreamdroid.ui.pick

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

/**
 * Phase 2.7g: timer service picker (bouquet → channel) as a Compose destination.
 * Result Intent carries typed [net.reichholf.dreamdroid.enigma.Service] as [NavExtras.DATA]
 * for timer edit. The list, saved bouquet, and load jobs live on [TimerServicePickViewModel].
 */
@Composable
fun TimerServicePickDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: TimerServicePickViewModel = viewModel()
) {
    val context = LocalContext.current
    val session = viewModel.session
    val title = session.toolbarTitle

    BackHandler(enabled = session.bouquetRef.isNotEmpty()) {
        session.showBouquetList()
    }

    LaunchedEffect(title) {
        (context as? AppCompatActivity)?.title = title
    }
    LaunchedEffect(viewModel) {
        viewModel.start()
    }

    DreamDroidPullRefresh(
        refreshing = session.refresh.isRefreshing,
        onRefresh = { session.reload() },
        enabled = session.refresh.enabled,
        modifier = modifier
    ) {
        PickServiceScreen(
            items = session.listState.items,
            emptyMessage = session.emptyMessage,
            onItemClick = { service ->
                val picked = session.onRowClick(service)
                if (picked != null) {
                    val data = Intent().apply {
                        putExtra(NavExtras.DATA, picked)
                    }
                    handle.deliverPickResult(Activity.RESULT_OK, data)
                }
            }
        )
    }
}
