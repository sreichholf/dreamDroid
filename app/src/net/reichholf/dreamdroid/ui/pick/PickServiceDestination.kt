package net.reichholf.dreamdroid.ui.pick

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.text.asString

/**
 * Bouquet picker as a Compose NavHost destination. The result Intent carries the picked
 * [net.reichholf.dreamdroid.enigma.Service] as [KEY_BOUQUET].
 */
@Composable
fun PickServiceDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: PickServiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)

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
                val data = Intent().apply {
                    putExtra(KEY_BOUQUET, service)
                }
                handle.deliverPickResult(Activity.RESULT_OK, data)
            }
        )
    }
}

const val KEY_BOUQUET = "bouquet"
