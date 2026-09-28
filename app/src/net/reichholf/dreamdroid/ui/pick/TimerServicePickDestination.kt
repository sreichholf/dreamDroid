package net.reichholf.dreamdroid.ui.pick

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.text.asString

/**
 * Timer service picker (bouquet, then channel) as a Compose destination. The result Intent
 * carries the picked [net.reichholf.dreamdroid.enigma.Service] as [NavExtras.DATA] for the
 * timer editor. Back on a bouquet's channels returns to the bouquet list.
 */
@Composable
fun TimerServicePickDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: TimerServicePickViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)

    BackHandler(enabled = !uiState.showsBouquets) {
        viewModel.showBouquetList()
    }

    DreamDroidPullRefresh(
        refreshing = uiState.refreshing,
        onRefresh = viewModel::reload,
        modifier = modifier
    ) {
        PickServiceScreen(
            items = uiState.items,
            loading = uiState.refreshing,
            emptyMessage = uiState.emptyMessage?.asString(),
            onItemClick = { service ->
                val picked = viewModel.onRowClick(service)
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
