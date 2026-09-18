package net.reichholf.dreamdroid.ui.epg

import androidx.activity.compose.BackHandler
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
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.loadEventList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Phase 2.7f: per-service EPG as a direct Compose NavHost destination.
 */
@Composable
fun ServiceEpgDestination(
    handle: PhoneNavHandle,
    serviceRef: String,
    serviceName: String,
    modifier: Modifier = Modifier
) {
    // Prefer Compose BackHandler so system Back pops to hub before MainActivity leave-confirm.
    BackHandler {
        handle.popNavBackStack()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = remember { ServiceEpgListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.handle = handle
    dialogSession.context = context

    val baseTitle = context.getString(R.string.epg)
    fun finishedTitle() = "$baseTitle - $serviceName"

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun reload(forceRefresh: Boolean = false) {
        if (serviceRef.isEmpty()) {
            handle.popNavBackStack()
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
            val profileId = DreamDroid.getCurrentProfile().id
            val dao = AppDatabase.epg(context)
            val online = SessionConnectionHolder.shared.status.value.session ==
                ConnectionStatus.Session.Online
            val nowSec = System.currentTimeMillis() / 1000L
            suspend fun paintCache(): Boolean {
                val cached = if (profileId != null) {
                    ListEpgCache.loadServiceEvents(dao, profileId, serviceRef, nowSec)
                } else {
                    null
                } ?: return false
                refresh.setRefreshing(false)
                setToolbarTitle(finishedTitle())
                if (cached.isEmpty()) {
                    listState.replaceAll(emptyList())
                    emptyMessage = context.getString(R.string.no_list_item)
                } else {
                    emptyMessage = null
                    listState.replaceAll(cached)
                }
                return true
            }
            if (!forceRefresh && !online && paintCache()) {
                return@launch
            }
            val result = loadEventList(
                context.applicationContext,
                listOf(NameValuePair("sRef", serviceRef)),
                URIStore.EPG_SERVICE
            )
            if (result.success) {
                refresh.setRefreshing(false)
                setToolbarTitle(finishedTitle())
                if (result.events.isEmpty()) {
                    listState.replaceAll(emptyList())
                    emptyMessage = context.getString(R.string.no_list_item)
                } else {
                    emptyMessage = null
                    listState.replaceAll(result.events)
                }
                return@launch
            }
            if (paintCache()) {
                return@launch
            }
            refresh.setRefreshing(false)
            setToolbarTitle(finishedTitle())
            listState.replaceAll(emptyList())
            emptyMessage = result.errorText
        }
    }

    DisposableEffect(handle, dialogSession) {
        setToolbarTitle(finishedTitle())
        onDispose {
            loadJob?.cancel()
            loadJob = null
            dialogSession.dismissProgress()
        }
    }

    LaunchedEffect(serviceRef) {
        if (serviceRef.isEmpty()) {
            handle.popNavBackStack()
        } else {
            reload()
        }
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { reload(forceRefresh = true) },
        enabled = refresh.enabled,
        modifier = modifier
    ) {
        ServiceEpgScreen(
            items = listState.items,
            emptyMessage = emptyMessage,
            onItemClick = { dialogSession.showDetail(it) }
        )
    }

    EpgEventDetailSheetHost(dialogSession)
}
