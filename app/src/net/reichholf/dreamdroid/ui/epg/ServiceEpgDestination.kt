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
 * Phase 2.7f: per-service EPG as a direct Compose NavHost destination.
 */
@Composable
fun ServiceEpgDestination(
    hostFragment: PhoneNavHostFragment,
    serviceRef: String,
    serviceName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = remember { ServiceEpgListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.hostFragment = hostFragment
    dialogSession.context = context

    val baseTitle = context.getString(R.string.epg)
    fun finishedTitle() = "$baseTitle - $serviceName"

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun reload() {
        if (serviceRef.isEmpty()) {
            hostFragment.popNavBackStack()
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
                listOf(NameValuePair("sRef", serviceRef)),
                URIStore.EPG_SERVICE,
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
        hostFragment.composeDialogActionListener = dialogSession
        setToolbarTitle(finishedTitle())
        onDispose {
            if (hostFragment.composeDialogActionListener === dialogSession) {
                hostFragment.composeDialogActionListener = null
            }
            loadJob?.cancel()
            loadJob = null
            dialogSession.dismissProgress()
        }
    }

    LaunchedEffect(serviceRef) {
        if (serviceRef.isEmpty()) {
            hostFragment.popNavBackStack()
        } else {
            reload()
        }
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        ServiceEpgScreen(
            items = listState.items,
            emptyMessage = emptyMessage,
            onItemClick = { dialogSession.showDetail(it) },
        )
    }
}
