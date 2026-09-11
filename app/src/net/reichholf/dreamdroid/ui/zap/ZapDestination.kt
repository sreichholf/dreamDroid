package net.reichholf.dreamdroid.ui.zap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
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
import androidx.core.view.MenuProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET

/**
 * Phase 2.7d: Zap channel grid as a direct Compose NavHost destination.
 * Bouquet pick results arrive via [PhoneNavHostFragment.composeActivityResultListener].
 */
@Composable
fun ZapDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = remember { ZapListState() }
    val refresh = remember { ComposeRefreshState() }
    var bouquetRef by rememberSaveable {
        mutableStateOf(DreamDroid.getCurrentProfile().defaultBouquetTv.orEmpty())
    }
    var bouquetName by rememberSaveable {
        mutableStateOf(DreamDroid.getCurrentProfile().defaultBouquetTvName.orEmpty())
    }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var zapJob by remember { mutableStateOf<Job?>(null) }
    var waitingForPicker by rememberSaveable { mutableStateOf(false) }

    val session = remember { ZapSession() }
    session.hostFragment = hostFragment
    session.context = context
    session.bouquetRef = bouquetRef
    session.bouquetName = bouquetName
    session.waitingForPicker = waitingForPicker
    session.listState = listState
    session.refresh = refresh
    session.onBouquetRef = { bouquetRef = it }
    session.onBouquetName = { bouquetName = it }
    session.onWaitingForPicker = { waitingForPicker = it }
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }
    session.onZapJob = { zapJob = it }
    session.scope = scope

    DisposableEffect(hostFragment, session) {
        hostFragment.composeActivityResultListener = session
        val activity = context as? AppCompatActivity
        activity?.addMenuProvider(session, hostFragment.viewLifecycleOwner)
        session.setToolbarTitle(session.finishedTitle())
        onDispose {
            if (hostFragment.composeActivityResultListener === session) {
                hostFragment.composeActivityResultListener = null
            }
            activity?.removeMenuProvider(session)
            loadJob?.cancel()
            loadJob = null
            zapJob?.cancel()
            zapJob = null
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
        ZapScreen(
            items = listState.items,
            gridState = listState.gridState,
            scrollEpoch = listState.scrollEpoch,
            emptyMessage = emptyMessage,
            onItemClick = { service: Service -> session.zapTo(service.reference) },
            onItemLongClick = { service: Service -> session.stream(service) },
        )
    }
}

private class ZapSession :
    PhoneNavHostFragment.ActivityResultListener,
    MenuProvider {
    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    var bouquetRef: String = ""
    var bouquetName: String = ""
    var waitingForPicker: Boolean = false
    var listState: ZapListState? = null
    var refresh: ComposeRefreshState? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var onBouquetRef: ((String) -> Unit)? = null
    var onBouquetName: ((String) -> Unit)? = null
    var onWaitingForPicker: ((Boolean) -> Unit)? = null
    var onEmptyMessage: ((String?) -> Unit)? = null
    var onLoadJob: ((Job?) -> Unit)? = null
    var onZapJob: ((Job?) -> Unit)? = null
    private var loadJob: Job? = null
    private var zapJob: Job? = null

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun finishedTitle(): String {
        val ctx = context ?: return ""
        return bouquetName.takeIf { it.isNotEmpty() } ?: ctx.getString(R.string.app_name)
    }

    fun toast(message: CharSequence) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }

    fun reload() {
        val host = hostFragment ?: return
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        val coroutineScope = scope ?: return
        if (bouquetRef.isEmpty() && !waitingForPicker) {
            waitingForPicker = true
            onWaitingForPicker?.invoke(true)
            host.navigateToPickBouquet(Statics.REQUEST_PICK_BOUQUET)
            return
        }
        if (bouquetRef.isEmpty()) {
            return
        }
        if (state.items.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            val params = listOf(NameValuePair("sRef", bouquetRef))
            val result = loadServiceList(ctx.applicationContext, params)
            refreshState.setRefreshing(false)
            setToolbarTitle(finishedTitle())
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

    fun zapTo(ref: String) {
        val host = hostFragment ?: return
        val ctx = context ?: return
        zapJob?.cancel()
        zapJob = host.launchSimpleResultLoad(
            ZapRequestHandler(),
            listOf(NameValuePair("sRef", ref)),
        ) { _, result, http ->
            var toastText = ctx.getText(R.string.get_content_error).toString()
            val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
            when {
                !stateText.isNullOrEmpty() -> toastText = stateText
                http.hasError() -> toastText = http.getErrorText(ctx).orEmpty()
            }
            toast(toastText)
        }
        onZapJob?.invoke(zapJob)
    }

    fun stream(service: Service) {
        val ctx = context ?: return
        try {
            val activity = ctx as AppCompatActivity
            activity.startActivity(
                IntentFactory.getStreamServiceIntent(activity, service.reference, service.name),
            )
        } catch (_: ActivityNotFoundException) {
            toast(ctx.getText(R.string.missing_stream_player))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        if (requestCode != Statics.REQUEST_PICK_BOUQUET) {
            return
        }
        @Suppress("DEPRECATION")
        val bouquetMap = data?.getSerializableExtra(KEY_BOUQUET) as? ExtendedHashMap
        val bouquet = ZapListMapper.bouquetFrom(bouquetMap)
        if (bouquet.reference != bouquetRef) {
            bouquetRef = bouquet.reference
            bouquetName = bouquet.name
            onBouquetRef?.invoke(bouquetRef)
            onBouquetName?.invoke(bouquetName)
            listState?.scrollToTop()
        }
        waitingForPicker = false
        onWaitingForPicker?.invoke(false)
        reload()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.epgbouquet, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.menu_pick_bouquet) {
            val host = hostFragment ?: return true
            waitingForPicker = true
            onWaitingForPicker?.invoke(true)
            host.navigateToPickBouquet(Statics.REQUEST_PICK_BOUQUET)
            return true
        }
        return false
    }
}
