package net.reichholf.dreamdroid.ui.services

import android.content.ActivityNotFoundException
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuProvider
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadEpgNowNext
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailSheetHost
import net.reichholf.dreamdroid.ui.epg.EpgEventDialogSession
import net.reichholf.dreamdroid.widget.AnchorPopup

/**
 * Phase 2.7h: one TV/Radio hub bouquet page as Compose (parity with former ServiceListPageFragment).
 * Host must keep this in composition only while the page is the active hub child so
 * the options menu stay scoped.
 * System back pops one directory drill-down level before leaving the hub.
 */
@Composable
fun HubServiceListPage(
    hostFragment: PhoneNavHostFragment,
    bouquetRef: String,
    bouquetName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val listState = remember { ServiceListState() }
    val refresh = remember { ComposeRefreshState() }
    val rows = remember { emptyList<ServiceNowNext>().toMutableStateList() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var zapJob by remember { mutableStateOf<Job?>(null) }

    var currentRef by rememberSaveable(bouquetRef) { mutableStateOf(bouquetRef) }
    var currentName by rememberSaveable(bouquetRef) { mutableStateOf(bouquetName) }
    val history = remember(bouquetRef) {
        mutableListOf<Pair<String, String>>()
    }
    var historyDepth by remember(bouquetRef) { mutableIntStateOf(0) }

    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.hostFragment = hostFragment
    dialogSession.context = context

    val session = remember { HubServiceListSession() }
    session.hostFragment = hostFragment
    session.context = context
    session.popupRoot = view as? ViewGroup
    session.currentRef = currentRef
    session.currentName = currentName
    session.listState = listState
    session.refresh = refresh
    session.rows = rows
    session.scope = scope
    session.dialogSession = dialogSession
    session.onCurrentRef = { currentRef = it }
    session.onCurrentName = { currentName = it }
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }
    session.onZapJob = { zapJob = it }
    session.history = history
    session.onHistoryDepth = { historyDepth = it }
    session.rootRef = bouquetRef
    session.rootName = bouquetName

    BackHandler(enabled = historyDepth > 0) {
        session.navigateUp()
    }

    DisposableEffect(hostFragment, session, dialogSession) {
        val activity = context as? AppCompatActivity
        activity?.addMenuProvider(session, hostFragment.viewLifecycleOwner)
        session.setToolbarTitle(session.finishedTitle())
        onDispose {
            activity?.removeMenuProvider(session)
            loadJob?.cancel()
            loadJob = null
            zapJob?.cancel()
            zapJob = null
            dialogSession.dismissProgress()
        }
    }

    LaunchedEffect(bouquetRef, bouquetName) {
        history.clear()
        historyDepth = 0
        currentRef = bouquetRef
        currentName = bouquetName
    }

    LaunchedEffect(currentRef, currentName) {
        session.currentRef = currentRef
        session.currentName = currentName
        session.reload()
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { session.reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        if (listState.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (emptyMessage != null) {
                    Text(
                        text = emptyMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            ServiceListScreen(
                items = listState.items,
                onItemClick = { item, x, y -> session.onItemClick(item, isLong = false, x, y) },
                onItemLongClick = { item, x, y -> session.onItemClick(item, isLong = true, x, y) },
            )
        }
    }

    EpgEventDetailSheetHost(dialogSession)
}

private class HubServiceListSession : MenuProvider {
    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    var popupRoot: ViewGroup? = null
    var currentRef: String = ""
    var currentName: String = ""
    var rootRef: String = ""
    var rootName: String = ""
    var listState: ServiceListState? = null
    var refresh: ComposeRefreshState? = null
    var rows: MutableList<ServiceNowNext>? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var dialogSession: EpgEventDialogSession? = null
    var history: MutableList<Pair<String, String>>? = null
    var onHistoryDepth: ((Int) -> Unit)? = null
    var onCurrentRef: ((String) -> Unit)? = null
    var onCurrentName: ((String) -> Unit)? = null
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
        return currentName.takeIf { it.isNotEmpty() } ?: ctx.getString(R.string.services)
    }

    fun toast(message: CharSequence) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }

    fun httpParams(): List<NameValuePair> {
        val param = if (Service.isBouquet(currentRef)) "bRef" else "sRef"
        return listOf(NameValuePair(param, currentRef))
    }

    fun reload() {
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
        loadJob = coroutineScope.launch {
            val result = loadEpgNowNext(ctx.applicationContext, httpParams())
            refreshState.setRefreshing(false)
            setToolbarTitle(finishedTitle())
            (ctx as? AppCompatActivity)?.invalidateOptionsMenu()
            rows?.clear()
            if (!result.success) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(result.errorText)
                return@launch
            }
            if (result.rows.isEmpty()) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
            } else {
                onEmptyMessage?.invoke(null)
                rows?.addAll(result.rows)
                state.replaceAll(serviceListItemsFromNowNext(result.rows))
            }
        }
        onLoadJob?.invoke(loadJob)
    }

    fun onItemClick(item: ServiceListItem, isLong: Boolean, windowX: Int, windowY: Int) {
        val index = item.index
        val rowList = rows ?: return
        if (index < 0 || index >= rowList.size) {
            return
        }
        val row = rowList[index]
        val ref = row.serviceReference
        val name = row.serviceName
        if (Service.isMarker(ref)) {
            return
        }
        if (Service.isDirectory(ref)) {
            val h = history ?: return
            h.add(currentRef to currentName)
            onHistoryDepth?.invoke(h.size)
            currentRef = ref
            currentName = name
            onCurrentRef?.invoke(ref)
            onCurrentName?.invoke(name)
            return
        }
        val ctx = context ?: return
        val instantZap = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, false)
        if ((instantZap && !isLong) || (!instantZap && isLong)) {
            zapTo(ref)
        } else {
            showPopupMenu(windowX, windowY, row)
        }
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

    fun showPopupMenu(windowX: Int, windowY: Int, row: ServiceNowNext) {
        val root = popupRoot ?: return
        val ctx = context ?: return
        val host = hostFragment ?: return
        val dialogs = dialogSession ?: return
        AnchorPopup.showAtWindow(root, windowX, windowY) { menu ->
            menu.menuInflater.inflate(R.menu.popup_servicelist, menu.menu)
            menu.menu.findItem(R.id.menu_next_event).isVisible =
                DreamDroid.featureNowNext() && row.next != null
            menu.setOnMenuItemClickListener { menuItem ->
                val ref = row.serviceReference
                val name = row.serviceName
                when (menuItem.itemId) {
                    R.id.menu_next_event -> {
                        row.next?.let { dialogs.showDetail(it) }
                        true
                    }
                    R.id.menu_current_event -> {
                        row.now?.let { dialogs.showDetail(it) }
                        true
                    }
                    R.id.menu_browse_epg -> {
                        host.navigateToServiceEpg(ref, name)
                        true
                    }
                    R.id.menu_zap -> {
                        zapTo(ref)
                        true
                    }
                    R.id.menu_stream -> {
                        try {
                            val activity = ctx as AppCompatActivity
                            activity.startActivity(
                                IntentFactory.getStreamServiceIntent(
                                    activity,
                                    ref,
                                    name,
                                    currentRef,
                                    serviceNowNextToExtendedHashMap(row),
                                ),
                            )
                        } catch (_: ActivityNotFoundException) {
                            toast(ctx.getText(R.string.missing_stream_player))
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /** Pop one directory level; returns false when already at the hub bouquet. */
    fun navigateUp(): Boolean {
        val h = history ?: return false
        if (h.isEmpty()) {
            return false
        }
        val (ref, name) = h.removeAt(h.lastIndex)
        onHistoryDepth?.invoke(h.size)
        currentRef = ref
        currentName = name
        onCurrentRef?.invoke(ref)
        onCurrentName?.invoke(name)
        return true
    }

    /** Reset drill-down history to the hub bouquet and reload (parity with Fragment.upOrReload). */
    fun upOrReload() {
        history?.clear()
        onHistoryDepth?.invoke(0)
        currentRef = rootRef
        currentName = rootName
        onCurrentRef?.invoke(rootRef)
        onCurrentName?.invoke(rootName)
        reload()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.servicelistpage, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        val mph = context as? MultiPaneHandler
        if (mph?.isDrawerOpen == true) {
            return
        }
        val setDefault = menu.findItem(R.id.menu_default) ?: return
        setDefault.isVisible = true
        val defaultReference = DreamDroid.getCurrentProfile().defaultBouquetTv
        if (defaultReference != null && defaultReference == currentRef) {
            setDefault.setIcon(R.drawable.ic_action_fav)
            setDefault.setTitle(R.string.reset_default)
        } else {
            setDefault.setIcon(R.drawable.ic_action_nofav)
            setDefault.setTitle(R.string.set_default)
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != Statics.ITEM_SET_DEFAULT) {
            return false
        }
        val ctx = context ?: return true
        if (currentRef.isEmpty()) {
            toast(ctx.getText(R.string.default_bouquet_not_set))
            return true
        }
        val p: Profile = DreamDroid.getCurrentProfile()
        var reset = false
        if (p.defaultBouquetTv != null && p.defaultBouquetTv == currentRef) {
            p.defaultBouquetTv = null
            reset = true
        } else {
            p.setDefaultRefValues(currentRef, currentName)
        }
        AppDatabase.profiles(ctx).updateProfile(p)
        if (!reset) {
            toast(ctx.getText(R.string.default_bouquet_set_to).toString() + " '" + currentName + "'")
        }
        (ctx as? AppCompatActivity)?.invalidateOptionsMenu()
        return true
    }
}
