package net.reichholf.dreamdroid.ui.services

import android.content.ActivityNotFoundException
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.multiepg.MultiEpgSyncHolder
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.UserBouquetEpgFill
import net.reichholf.dreamdroid.multiepg.overlayNowNext
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgDao
import net.reichholf.dreamdroid.room.RosterDao
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailSheetHost
import net.reichholf.dreamdroid.ui.epg.EpgEventDialogSession
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.launchSimpleResultLoad
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.widget.AnchorPopup

/**
 * Phase 2.7h: one TV/Radio hub bouquet page as Compose
 * (parity with former ServiceListPageFragment).
 * Host must keep this in composition only while the page is the active hub child so
 * the options menu stay scoped.
 * System back pops one directory drill-down level before leaving the hub.
 */
@Composable
fun HubServiceListPage(
    handle: PhoneNavHandle,
    bouquetRef: String,
    bouquetName: String,
    modifier: Modifier = Modifier,
    onProvideGoUp: ((() -> Unit)?) -> Unit = {},
    onZapped: () -> Unit = {}
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
    dialogSession.handle = handle
    dialogSession.context = context

    val session = remember { HubServiceListSession() }
    session.handle = handle
    session.context = context
    session.popupRoot = AnchorPopup.overlayRoot(view)
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
    session.onZapped = onZapped
    session.profileId = DreamDroid.getCurrentProfile().id
    session.rosterDao = AppDatabase.roster(context)
    session.epgDao = AppDatabase.epg(context)
    session.excludedTabRefs = UserBouquetCache.excludedHubTabRefs(context)

    BackHandler(enabled = historyDepth > 0) {
        session.navigateUp()
    }

    DisposableEffect(session) {
        onProvideGoUp { session.upOrReload() }
        onDispose { onProvideGoUp(null) }
    }

    DisposableEffect(handle, session, dialogSession) {
        val activity = context as? AppCompatActivity
        activity?.addMenuProvider(session)
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
        modifier = modifier
    ) {
        if (listState.items.isEmpty()) {
            ListEmptyState(
                loading = refresh.isRefreshing,
                message = emptyMessage,
                onRetry = { session.reload() }
            )
        } else {
            ServiceListScreen(
                items = listState.items,
                onItemClick = { item, x, y -> session.onItemClick(item, isLong = false, x, y) },
                onItemLongClick = { item, x, y -> session.onItemClick(item, isLong = true, x, y) }
            )
        }
    }

    EpgEventDetailSheetHost(dialogSession)
}

class HubServiceListSession : MenuProvider {
    var handle: PhoneNavHandle? = null
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
    var onZapped: (() -> Unit)? = null
    var profileId: Int? = null
    var rosterDao: RosterDao? = null
    var epgDao: EpgDao? = null
    var excludedTabRefs: Set<String> = emptySet()
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var zapJob: Job? = null

    fun beginLoad(): Int = ++loadGeneration

    fun applyLoadResult(
        generation: Int,
        success: Boolean,
        rows: List<ServiceNowNext>,
        errorText: String?
    ) {
        if (generation != loadGeneration) {
            return
        }
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        refreshState.setRefreshing(false)
        setToolbarTitle(finishedTitle())
        (ctx as? AppCompatActivity)?.invalidateOptionsMenu()
        this.rows?.clear()
        if (!success) {
            state.replaceAll(emptyList())
            onEmptyMessage?.invoke(errorText)
            return
        }
        if (rows.isEmpty()) {
            state.replaceAll(emptyList())
            onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
        } else {
            onEmptyMessage?.invoke(null)
            this.rows?.addAll(rows)
            state.replaceAll(serviceListItemsFromNowNext(rows))
        }
        persistRoster(rows)
    }

    private fun fillEpgNowChunk() {
        val ctx = context ?: return
        val dao = rosterDao ?: return
        val pid = profileId ?: return
        val coroutineScope = scope ?: return
        val persistRef = currentRef
        val persistTabRoot = rootRef
        val excluded = excludedTabRefs
        coroutineScope.launch {
            try {
                UserBouquetEpgFill.ensureNowChunk(
                    sync = MultiEpgSyncHolder.shared(ctx),
                    rosterDao = dao,
                    profileId = pid,
                    containerRef = persistRef,
                    tabRootRef = persistTabRoot,
                    excludedTabRefs = excluded,
                    unixSec = System.currentTimeMillis() / 1000L
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    throw t
                }
            }
        }
    }

    private fun persistRoster(rows: List<ServiceNowNext>) {
        val dao = rosterDao ?: return
        val pid = profileId ?: return
        val coroutineScope = scope ?: return
        val persistRef = currentRef
        val persistTabRoot = rootRef
        val persistRows = rows.toList()
        val excluded = excludedTabRefs
        coroutineScope.launch {
            UserBouquetCache.persistRosterIfCacheable(
                dao = dao,
                profileId = pid,
                ref = persistRef,
                tabRootRef = persistTabRoot,
                rows = persistRows,
                excludedTabRefs = excluded
            )
        }
    }

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
        val generation = beginLoad()
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            val result = loadEpgNowNext(ctx.applicationContext, httpParams())
            if (result.success) {
                applyLoadResult(generation, true, result.rows, null)
                fillEpgNowChunk()
                return@launch
            }
            val dao = rosterDao
            val pid = profileId
            val cached = if (dao != null && pid != null) {
                UserBouquetCache.loadRosterNowNext(dao, pid, currentRef)
            } else {
                null
            }
            if (cached != null) {
                val nowSec = System.currentTimeMillis() / 1000L
                val epg = epgDao
                val overlayPid = pid
                val events = if (epg != null && overlayPid != null) {
                    val chunk = MultiEpgWindows.chunkContaining(nowSec)
                    epg.eventsOverlapping(
                        overlayPid,
                        currentRef,
                        chunk.startSec,
                        chunk.endSec
                    )
                } else {
                    emptyList()
                }
                applyLoadResult(
                    generation,
                    true,
                    overlayNowNext(cached, events, nowSec),
                    null
                )
            } else {
                applyLoadResult(generation, false, emptyList(), result.errorText)
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
        val host = handle ?: return
        val ctx = context ?: return
        host.runOnlineOnly {
            zapJob?.cancel()
            zapJob = host.launchSimpleResultLoad(
                ZapRequestHandler(),
                listOf(NameValuePair("sRef", ref))
            ) { _, result, error ->
                var toastText = ctx.getText(R.string.get_content_error).toString()
                val stateText = result.stateText
                when {
                    !stateText.isNullOrEmpty() -> toastText = stateText
                    error != null -> toastText = error.resolve(ctx).orEmpty()
                }
                toast(toastText)
                onZapped?.invoke()
            }
            onZapJob?.invoke(zapJob)
        }
    }

    fun showPopupMenu(windowX: Int, windowY: Int, row: ServiceNowNext) {
        val root = popupRoot ?: return
        val ctx = context ?: return
        val host = handle ?: return
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
                        host.runOnlineOnly {
                            try {
                                val activity = ctx as AppCompatActivity
                                activity.startActivity(
                                    IntentFactory.getStreamServiceIntent(
                                        activity,
                                        ref,
                                        name,
                                        currentRef,
                                        row
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                toast(ctx.getText(R.string.missing_stream_player))
                            }
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

    /**
     * Tab reselect: jump back to this bouquet's root when drilled into providers/dirs,
     * otherwise reload. Matches historical ServiceListPageFragment.upOrReload.
     * Ref changes reload via the composable LaunchedEffect(currentRef).
     */
    fun upOrReload() {
        val h = history ?: return
        if (h.isNotEmpty()) {
            h.clear()
            onHistoryDepth?.invoke(0)
            currentRef = rootRef
            currentName = rootName
            onCurrentRef?.invoke(rootRef)
            onCurrentName?.invoke(rootName)
        } else {
            reload()
        }
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
        AppDatabase.profilesBlocking(ctx).updateProfile(p)
        if (!reset) {
            toast(
                ctx.getText(R.string.default_bouquet_set_to).toString() + " '" + currentName + "'"
            )
        }
        (ctx as? AppCompatActivity)?.invalidateOptionsMenu()
        return true
    }
}
