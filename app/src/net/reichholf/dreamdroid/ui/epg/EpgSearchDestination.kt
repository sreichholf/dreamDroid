package net.reichholf.dreamdroid.ui.epg

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
import net.reichholf.dreamdroid.enigma.loadEventList
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh

/**
 * Phase 2.7f: EPG search results as a direct Compose NavHost destination.
 * Remount/reload is driven by [query] + host remount epoch for same-query resubmits.
 */
@Composable
fun EpgSearchDestination(
    hostFragment: PhoneNavHostFragment,
    query: String,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = remember(query, remountEpoch) { EpgBouquetListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.hostFragment = hostFragment
    dialogSession.context = context

    val baseTitle = context.getString(R.string.epg_search)
    fun finishedTitle() = "$baseTitle - '$query'"

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun reload() {
        if (query.isEmpty()) {
            return
        }
        if (listState.items.isEmpty()) {
            emptyMessage = context.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        setToolbarTitle(context.getString(R.string.loading))
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadEventList(
                context.applicationContext,
                listOf(NameValuePair("search", query)),
                URIStore.EPG_SEARCH,
            )
            refresh.setRefreshing(false)
            setToolbarTitle(finishedTitle())
            if (!result.success) {
                listState.replaceAll(emptyList())
                emptyMessage = result.errorText
                return@launch
            }
            if (result.events.isEmpty()) {
                listState.replaceAll(emptyList())
                emptyMessage = context.getString(R.string.no_list_item)
            } else {
                emptyMessage = null
                listState.replaceAll(result.events)
            }
        }
    }

    DisposableEffect(hostFragment, dialogSession) {
        setToolbarTitle(finishedTitle())
        onDispose {
            loadJob?.cancel()
            loadJob = null
            dialogSession.dismissProgress()
        }
    }

    LaunchedEffect(query, remountEpoch) {
        reload()
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        EpgBouquetScreen(
            items = listState.items,
            listState = listState.listState,
            scrollEpoch = listState.scrollEpoch,
            emptyMessage = emptyMessage,
            onItemClick = { dialogSession.showDetail(it) },
        )
    }

    EpgEventDetailSheetHost(dialogSession)
}
