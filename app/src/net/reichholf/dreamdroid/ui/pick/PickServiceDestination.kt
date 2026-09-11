package net.reichholf.dreamdroid.ui.pick

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.zap.ZapListMapper

/**
 * Phase 2.7f: bouquet/service picker as a direct Compose NavHost destination.
 * Result Intent still carries [KEY_BOUQUET] ExtendedHashMap for Zap / EPG consumers.
 */
@Composable
fun PickServiceDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = remember { PickServiceListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun reload() {
        if (listState.items.isEmpty()) {
            emptyMessage = context.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        setToolbarTitle(context.getString(R.string.loading))
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadBouquetList(context.applicationContext)
            refresh.setRefreshing(false)
            setToolbarTitle(context.getString(R.string.services))
            if (!result.success) {
                listState.replaceAll(emptyList())
                emptyMessage = result.errorText
                return@launch
            }
            val rows = ArrayList(result.bouquets.tv)
            rows.addAll(result.bouquets.radio)
            if (rows.isEmpty()) {
                listState.replaceAll(emptyList())
                emptyMessage = context.getString(R.string.no_list_item)
            } else {
                emptyMessage = null
                listState.replaceAll(rows)
            }
        }
    }

    DisposableEffect(Unit) {
        setToolbarTitle(context.getString(R.string.services))
        onDispose {
            loadJob?.cancel()
            loadJob = null
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        PickServiceScreen(
            items = listState.items,
            emptyMessage = emptyMessage,
            onItemClick = { service ->
                val data = Intent().apply {
                    putExtra(KEY_BOUQUET, ZapListMapper.toBouquetMap(service))
                }
                hostFragment.deliverPickResult(Activity.RESULT_OK, data)
            },
        )
    }
}

const val KEY_BOUQUET = "bouquet"
