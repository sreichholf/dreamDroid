package net.reichholf.dreamdroid.ui.pick

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.zap.ZapListMapper

/**
 * Phase 2.7g: timer service picker (bouquet → channel) as a Compose destination.
 * Result Intent carries [BaseHttpFragment.sData] ExtendedHashMap for timer edit.
 */
@Composable
fun TimerServicePickDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = remember { PickServiceListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var bouquetRef by rememberSaveable { mutableStateOf("") }
    var bouquetName by rememberSaveable { mutableStateOf("") }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    val session = remember { TimerServicePickSession() }
    session.hostFragment = hostFragment
    session.context = context
    session.listState = listState
    session.refresh = refresh
    session.scope = scope
    session.bouquetRef = bouquetRef
    session.bouquetName = bouquetName
    session.onBouquetRef = { bouquetRef = it }
    session.onBouquetName = { bouquetName = it }
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }

    BackHandler(enabled = bouquetRef.isNotEmpty()) {
        session.showBouquetList()
    }

    DisposableEffect(Unit) {
        session.setToolbarTitle(session.finishedTitle())
        onDispose {
            loadJob?.cancel()
            loadJob = null
        }
    }

    LaunchedEffect(Unit) {
        session.reload()
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { session.reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        PickServiceScreen(
            items = listState.items,
            emptyMessage = emptyMessage,
            onItemClick = { session.onRowClick(it) },
        )
    }
}

private class TimerServicePickSession {
    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    var listState: PickServiceListState? = null
    var refresh: ComposeRefreshState? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var bouquetRef: String = ""
    var bouquetName: String = ""
    var onBouquetRef: ((String) -> Unit)? = null
    var onBouquetName: ((String) -> Unit)? = null
    var onEmptyMessage: ((String?) -> Unit)? = null
    var onLoadJob: ((Job?) -> Unit)? = null
    private var bouquets: List<Service> = emptyList()
    private var loadJob: Job? = null
    private var loadGeneration = 0

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun finishedTitle(): String {
        val ctx = context ?: return ""
        return if (bouquetRef.isEmpty()) {
            ctx.getString(R.string.service)
        } else {
            bouquetName.ifEmpty { ctx.getString(R.string.service) }
        }
    }

    fun reload() {
        if (bouquetRef.isEmpty()) {
            loadBouquets()
        } else {
            loadServices()
        }
    }

    fun showBouquetList() {
        val ctx = context ?: return
        val state = listState ?: return
        bouquetRef = ""
        bouquetName = ""
        onBouquetRef?.invoke("")
        onBouquetName?.invoke("")
        loadGeneration++
        loadJob?.cancel()
        if (bouquets.isNotEmpty()) {
            onEmptyMessage?.invoke(null)
            state.replaceAll(bouquets)
            setToolbarTitle(ctx.getString(R.string.service))
        } else {
            state.replaceAll(emptyList())
            loadBouquets()
        }
    }

    fun onRowClick(service: Service) {
        val host = hostFragment ?: return
        val ctx = context ?: return
        val state = listState ?: return
        if (bouquetRef.isEmpty()) {
            if (ServiceKeys.isMarker(service.reference)) {
                return
            }
            bouquetRef = service.reference
            bouquetName = service.name
            onBouquetRef?.invoke(bouquetRef)
            onBouquetName?.invoke(bouquetName)
            state.replaceAll(emptyList())
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
            loadServices()
            return
        }
        if (ServiceKeys.isMarker(service.reference)) {
            return
        }
        val data = Intent().apply {
            putExtra(BaseHttpFragment.sData, ZapListMapper.toBouquetMap(service))
        }
        host.deliverPickResult(Activity.RESULT_OK, data)
    }

    private fun loadBouquets() {
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        val coroutineScope = scope ?: return
        if (state.items.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        loadJob?.cancel()
        loadGeneration++
        val generation = loadGeneration
        loadJob = coroutineScope.launch {
            val result = loadBouquetList(ctx.applicationContext)
            if (generation != loadGeneration || bouquetRef.isNotEmpty()) {
                return@launch
            }
            refreshState.setRefreshing(false)
            setToolbarTitle(ctx.getString(R.string.service))
            if (!result.success) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(result.errorText)
                return@launch
            }
            val rows = ArrayList(result.bouquets.tv)
            rows.addAll(result.bouquets.radio)
            bouquets = rows
            if (rows.isEmpty()) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
            } else {
                onEmptyMessage?.invoke(null)
                state.replaceAll(rows)
            }
        }
        onLoadJob?.invoke(loadJob)
    }

    private fun loadServices() {
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        val coroutineScope = scope ?: return
        if (state.items.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        loadJob?.cancel()
        loadGeneration++
        val generation = loadGeneration
        val title = bouquetName.ifEmpty { ctx.getString(R.string.service) }
        loadJob = coroutineScope.launch {
            val result = loadServiceList(
                ctx.applicationContext,
                listOf(NameValuePair("sRef", bouquetRef)),
            )
            if (generation != loadGeneration || bouquetRef.isEmpty()) {
                return@launch
            }
            refreshState.setRefreshing(false)
            setToolbarTitle(title)
            if (!result.success) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(result.errorText)
                return@launch
            }
            val rows = ZapListMapper.rowsFrom(result.services)
            if (rows.isEmpty()) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
            } else {
                onEmptyMessage?.invoke(null)
                state.replaceAll(rows)
            }
        }
        onLoadJob?.invoke(loadJob)
    }
}
