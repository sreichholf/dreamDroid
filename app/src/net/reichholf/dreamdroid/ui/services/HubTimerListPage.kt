package net.reichholf.dreamdroid.ui.services

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.MenuProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.enigma.TimerListLoadResult
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadTimerList
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerCleanupRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.TimerDao
import net.reichholf.dreamdroid.room.TimerSnapshotStore
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.BindShellFab
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.launchSimpleResultLoad
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Phase 2.7h: hub Timers page as Compose (parity with former TimerListFragment).
 *
 * Reload after timer edit:
 * - bump [remountEpoch] from HubDestination when returning, and/or
 * - register [HubTimerListSession] on [PhoneNavHandle.composeActivityResultListener]
 *   (this page does so while composed).
 */
@Composable
fun HubTimerListPage(handle: PhoneNavHandle, remountEpoch: Int = 0, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val scope = rememberCoroutineScope()
    val listState = remember { TimerListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var mutateJob by remember { mutableStateOf<Job?>(null) }

    val session = remember { HubTimerListSession() }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    session.onRequestDeleteConfirm = { title -> showDeleteConfirm = title }
    session.handle = handle
    session.context = context
    session.activity = activity
    session.listState = listState
    session.refresh = refresh
    session.scope = scope
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }
    session.onMutateJob = { mutateJob = it }
    session.profileId = DreamDroid.getCurrentProfile().id
    session.timerDao = AppDatabase.timer(context)

    DisposableEffect(handle, session) {
        // HubDestination owns REQUEST_EDIT_TIMER → remountEpoch; do not steal
        // composeActivityResultListener. Session still implements ActivityResultListener
        // if a host prefers registering it instead of remountEpoch.
        activity.addMenuProvider(session)
        session.setToolbarTitle(context.getString(R.string.timer))
        onDispose {
            activity.removeMenuProvider(session)
            session.finishActionMode()
            session.dismissProgress()
            loadJob?.cancel()
            loadJob = null
            mutateJob?.cancel()
            mutateJob = null
        }
    }

    val newTimerLabel = stringResource(R.string.new_timer)
    val timerWritesBlocked =
        SessionConnectionHolder.shared.status.collectAsState().value.blocksMutations
    BindShellFab(
        contentDescription = newTimerLabel,
        iconRes = R.drawable.ic_action_fab_add,
        onClick = { session.createTimer() },
        text = newTimerLabel,
        lookDisabled = timerWritesBlocked
    )

    LaunchedEffect(remountEpoch) {
        session.reload()
    }

    LaunchedEffect(session.progress) {
        activity.invalidateOptionsMenu()
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
            TimerListScreen(
                items = listState.items,
                onItemClick = { session.onItemClick(it) },
                onItemLongClick = { session.onItemLongClick(it) }
            )
        }
    }

    showDeleteConfirm?.let { title ->
        ConfirmAlertDialog(
            title = title,
            message = stringResource(R.string.delete_confirm),
            onDismiss = { showDeleteConfirm = null },
            onConfirm = {
                session.confirmDeleteSelected()
                showDeleteConfirm = null
            },
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }

    IndeterminateProgressHost(session.progress)
}

/**
 * Owns timer-list load/mutations and activity-result reload for the hub Timers tab.
 * HubDestination may also assign this to [PhoneNavHandle.composeActivityResultListener]
 * when the timer page is selected (the page registers itself while composed).
 */
class HubTimerListSession :
    PhoneNavHandle.ActivityResultListener,
    MenuProvider {

    var handle: PhoneNavHandle? = null
    var context: android.content.Context? = null
    var activity: AppCompatActivity? = null
    var listState: TimerListState? = null
    var refresh: ComposeRefreshState? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var onEmptyMessage: ((String?) -> Unit)? = null
    var onLoadJob: ((Job?) -> Unit)? = null
    var onMutateJob: ((Job?) -> Unit)? = null
    var profileId: Int? = null
    var timerDao: TimerDao? = null
    var loadTimers: suspend (android.content.Context) -> TimerListLoadResult =
        { context -> loadTimerList(context) }

    var onRequestDeleteConfirm: ((String) -> Unit)? = null

    private val timers = ArrayList<TypedTimer>()
    private var selected: TypedTimer = TypedTimer()
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var mutateJob: Job? = null
    var progress by mutableStateOf<IndeterminateProgressState?>(null)
    private var actionMode: ActionMode? = null
    private var actionModeActive = false

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.timerlist_context, menu)
            actionModeActive = true
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val toggle = menu.findItem(R.id.menu_toggle_enabled)
            if (selected.disabled == "0") {
                toggle?.setTitle(R.string.disable)
            } else {
                toggle?.setTitle(R.string.enable)
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            mode.finish()
            return onItemSelected(item.itemId)
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionModeActive = false
            actionMode = null
        }
    }

    fun setToolbarTitle(title: String) {
        activity?.title = title
    }

    fun finishActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    fun dismissProgress() {
        progress = null
    }

    fun toast(message: CharSequence) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }

    fun beginLoad(): Int = ++loadGeneration

    fun applyLoadResult(
        generation: Int,
        success: Boolean,
        loaded: List<TypedTimer>,
        errorText: String?
    ) {
        if (generation != loadGeneration) {
            return
        }
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        refreshState.setRefreshing(false)
        setToolbarTitle(ctx.getString(R.string.timer))
        timers.clear()
        state.replaceAll(emptyList())
        if (!success) {
            onEmptyMessage?.invoke(errorText)
            return
        }
        if (loaded.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
            return
        }
        onEmptyMessage?.invoke(null)
        timers.addAll(loaded)
        state.replaceAll(timerListItemsFrom(ctx, timers))
    }

    fun reload() {
        val ctx = context ?: return
        if (listState == null) {
            return
        }
        val refreshState = refresh ?: return
        val coroutineScope = scope ?: return
        if (timers.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        val generation = beginLoad()
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            loadAndApply(generation)
        }
        onLoadJob?.invoke(loadJob)
    }

    suspend fun loadAndApply(generation: Int) {
        val ctx = context ?: return
        val result = loadTimers(ctx.applicationContext)
        if (generation != loadGeneration) {
            return
        }
        if (result.success) {
            persistSnapshot(result.timers)
            applyLoadResult(generation, true, result.timers, null)
            return
        }
        val dao = timerDao
        val pid = profileId
        val cached = if (dao != null && pid != null) {
            TimerSnapshotStore.load(dao, pid)
        } else {
            null
        }
        if (cached != null) {
            applyLoadResult(generation, true, cached, null)
        } else {
            applyLoadResult(generation, false, emptyList(), result.errorText)
        }
    }

    private suspend fun persistSnapshot(loaded: List<TypedTimer>) {
        val dao = timerDao ?: return
        val pid = profileId ?: return
        TimerSnapshotStore.replace(dao, pid, loaded)
    }

    fun createTimer() {
        val host = handle ?: return
        host.runOnlineOnly {
            selected = Timer.getInitialTimer()
            editTimer(selected, create = true)
        }
    }

    fun onItemClick(item: TimerListItem) {
        if (item.index !in timers.indices) return
        selected = timers[item.index]
        if (actionModeActive) {
            return
        }
        editTimer(selected, create = false)
    }

    fun onItemLongClick(item: TimerListItem) {
        if (item.index !in timers.indices) return
        selected = timers[item.index]
        val act = activity ?: return
        actionMode = act.startSupportActionMode(actionModeCallback)
    }

    private fun editTimer(timer: TypedTimer, create: Boolean) {
        val host = handle ?: return
        host.navigateToTimerEdit(timer, create)
    }

    private fun deleteTimerConfirm() {
        val name = selected.name
        onRequestDeleteConfirm?.invoke(name.orEmpty())
    }

    fun confirmDeleteSelected() {
        deleteTimer(selected)
    }

    fun deleteTimer(timer: TypedTimer) {
        if (progress != null) {
            return
        }
        val host = handle ?: return
        val ctx = context ?: return
        host.runOnlineOnly {
            progress = IndeterminateProgressState(message = ctx.getString(R.string.deleting))
            val params = Timer.getDeleteParams(timer)
            mutateJob?.cancel()
            mutateJob =
                host.launchSimpleResultLoad(
                    TimerDeleteRequestHandler(),
                    params
                ) { _, result, error ->
                    onSimpleResult(result, error)
                }
            onMutateJob?.invoke(mutateJob)
        }
    }

    private fun toggleTimerEnabled(timer: TypedTimer) {
        if (progress != null) {
            return
        }
        val host = handle ?: return
        val ctx = context ?: return
        host.runOnlineOnly {
            val timerNew = timer.copy(
                disabled = if (timer.disabled == "1") "0" else "1"
            )
            progress = IndeterminateProgressState(message = ctx.getString(R.string.saving))
            val params = Timer.getSaveParams(timerNew, timer)
            mutateJob?.cancel()
            mutateJob =
                host.launchSimpleResultLoad(
                    TimerChangeRequestHandler(),
                    params
                ) { _, result, error ->
                    onSimpleResult(result, error)
                }
            onMutateJob?.invoke(mutateJob)
        }
    }

    private fun cleanupTimerList() {
        if (progress != null) {
            return
        }
        val host = handle ?: return
        val ctx = context ?: return
        host.runOnlineOnly {
            progress = IndeterminateProgressState(
                message = ctx.getString(R.string.cleaning_timerlist)
            )
            mutateJob?.cancel()
            mutateJob = host.launchSimpleResultLoad(
                TimerCleanupRequestHandler(),
                emptyList()
            ) { _, result, error ->
                onSimpleResult(result, error)
            }
            onMutateJob?.invoke(mutateJob)
        }
    }

    private fun onSimpleResult(result: SimpleResult, error: EnigmaHttpError?) {
        dismissProgress()
        val ctx = context ?: return
        var toastText = ctx.getText(R.string.get_content_error).toString()
        val stateText = result.stateText
        when {
            !stateText.isNullOrEmpty() -> toastText = stateText
            error != null -> toastText = error.resolve(ctx).orEmpty()
        }
        toast(toastText)
        reload()
    }

    private fun onItemSelected(id: Int): Boolean = when (id) {
        Statics.ITEM_NEW_TIMER -> {
            createTimer()
            true
        }

        Statics.ITEM_CLEANUP -> {
            cleanupTimerList()
            true
        }

        Statics.ITEM_TOGGLE_ENABLED -> {
            toggleTimerEnabled(selected)
            true
        }

        Statics.ITEM_DELETE -> {
            deleteTimerConfirm()
            true
        }

        else -> false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != Statics.REQUEST_EDIT_TIMER) {
            return
        }
        if (resultCode == Activity.RESULT_OK) {
            Log.w(DreamDroid.LOG_TAG, "TIMER SAVED!")
            reload()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.timerlist, menu)
        menu.findItem(Statics.ITEM_CLEANUP)?.isEnabled = progress == null
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = onItemSelected(menuItem.itemId)
}
