package net.reichholf.dreamdroid.ui.services

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.MenuProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadTimerList
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerCleanupRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh

/**
 * Phase 2.7h: hub Timers page as Compose (parity with former TimerListFragment).
 *
 * Reload after timer edit:
 * - bump [remountEpoch] from HubDestination when returning, and/or
 * - register [HubTimerListSession] on [PhoneNavHostFragment.composeActivityResultListener]
 *   (this page does so while composed).
 */
@Composable
fun HubTimerListPage(
    hostFragment: PhoneNavHostFragment,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val scope = rememberCoroutineScope()
    val listState = remember { TimerListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var mutateJob by remember { mutableStateOf<Job?>(null) }

    val session = remember { HubTimerListSession() }
    session.hostFragment = hostFragment
    session.context = context
    session.activity = activity
    session.listState = listState
    session.refresh = refresh
    session.scope = scope
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }
    session.onMutateJob = { mutateJob = it }

    DisposableEffect(hostFragment, session) {
        // HubDestination owns REQUEST_EDIT_TIMER → remountEpoch; do not steal
        // composeActivityResultListener. Session still implements ActivityResultListener
        // if a host prefers registering it instead of remountEpoch.
        hostFragment.composeDialogActionListener = session
        activity.addMenuProvider(session, hostFragment.viewLifecycleOwner)
        session.setToolbarTitle(context.getString(R.string.timer))
        val fab = activity.findViewById<FloatingActionButton?>(R.id.fab_main)
        fab?.let {
            it.show()
            it.contentDescription = context.getString(R.string.new_timer)
            it.setImageResource(R.drawable.ic_action_fab_add)
            it.setOnClickListener { session.createTimer() }
            it.setOnLongClickListener { v ->
                Toast.makeText(activity, v.contentDescription, Toast.LENGTH_SHORT).show()
                true
            }
        }
        onDispose {
            if (hostFragment.composeDialogActionListener === session) {
                hostFragment.composeDialogActionListener = null
            }
            activity.removeMenuProvider(session)
            session.finishActionMode()
            session.dismissProgress()
            loadJob?.cancel()
            loadJob = null
            mutateJob?.cancel()
            mutateJob = null
            fab?.let {
                it.setOnClickListener(null)
                it.setOnLongClickListener(null)
                it.hide()
            }
            (activity as? MainActivity)?.unregisterFab(R.id.fab_main)
        }
    }

    LaunchedEffect(remountEpoch) {
        session.reload()
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { session.reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            TimerListScreen(
                items = listState.items,
                onItemClick = { session.onItemClick(it) },
                onItemLongClick = { session.onItemLongClick(it) },
            )
            val message = emptyMessage
            if (message != null && listState.items.isEmpty()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * Owns timer-list load/mutations and activity-result reload for the hub Timers tab.
 * HubDestination may also assign this to [PhoneNavHostFragment.composeActivityResultListener]
 * when the timer page is selected (the page registers itself while composed).
 */
class HubTimerListSession :
    PhoneNavHostFragment.ActivityResultListener,
    ActionDialog.DialogActionListener,
    MenuProvider {

    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    var activity: AppCompatActivity? = null
    var listState: TimerListState? = null
    var refresh: ComposeRefreshState? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var onEmptyMessage: ((String?) -> Unit)? = null
    var onLoadJob: ((Job?) -> Unit)? = null
    var onMutateJob: ((Job?) -> Unit)? = null

    private val timers = ArrayList<TypedTimer>()
    private val mapList = ArrayList<ExtendedHashMap>()
    private var selected: ExtendedHashMap = ExtendedHashMap()
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var mutateJob: Job? = null
    private var progress: ProgressDialog? = null
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
            if (selected.getString(Timer.KEY_DISABLED) == "0") {
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
        progress?.takeIf { it.isShowing }?.dismiss()
        progress = null
    }

    fun toast(message: CharSequence) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }

    fun reload() {
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        val coroutineScope = scope ?: return
        if (timers.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            val result = loadTimerList(ctx.applicationContext)
            if (generation != loadGeneration) {
                return@launch
            }
            refreshState.setRefreshing(false)
            setToolbarTitle(ctx.getString(R.string.timer))
            timers.clear()
            mapList.clear()
            state.replaceAll(emptyList())
            if (!result.success) {
                onEmptyMessage?.invoke(result.errorText)
                return@launch
            }
            if (result.timers.isEmpty()) {
                onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
                return@launch
            }
            onEmptyMessage?.invoke(null)
            timers.addAll(result.timers)
            for (timer in result.timers) {
                mapList.add(TimerListMapper.toExtendedHashMap(timer))
            }
            state.replaceAll(timerListItemsFrom(ctx, timers))
        }
        onLoadJob?.invoke(loadJob)
    }

    fun createTimer() {
        selected = Timer.getInitialTimer()
        editTimer(selected, create = true)
    }

    fun onItemClick(item: TimerListItem) {
        if (item.index !in mapList.indices) return
        selected = mapList[item.index]
        if (actionModeActive) {
            return
        }
        editTimer(selected, create = false)
    }

    fun onItemLongClick(item: TimerListItem) {
        if (item.index !in mapList.indices) return
        selected = mapList[item.index]
        val act = activity ?: return
        actionMode = act.startSupportActionMode(actionModeCallback)
    }

    private fun editTimer(timer: ExtendedHashMap, create: Boolean) {
        val host = hostFragment ?: return
        if (host.navigateToTimerEdit(timer, create)) {
            return
        }
        val mph = activity as? MultiPaneHandler ?: return
        Timer.edit(mph, timer, host, create)
    }

    private fun deleteTimerConfirm() {
        val mph = activity as? MultiPaneHandler ?: return
        val name = selected.getString(Timer.KEY_NAME)
        val dialog = PositiveNegativeDialog.newInstance(
            name,
            R.string.delete_confirm,
            android.R.string.yes,
            Statics.ACTION_DELETE_CONFIRMED,
            android.R.string.no,
            Statics.ACTION_NONE,
        )
        mph.showDialogFragment(dialog, "dialog_delete_timer_confirm")
    }

    private fun deleteTimer(timer: ExtendedHashMap) {
        val host = hostFragment ?: return
        val ctx = context ?: return
        dismissProgress()
        progress = ProgressDialog.show(activity, "", ctx.getText(R.string.deleting), true)
        val params = Timer.getDeleteParams(timer)
        mutateJob?.cancel()
        mutateJob = host.launchSimpleResultLoad(TimerDeleteRequestHandler(), params) { _, result, http ->
            onSimpleResult(result, http)
        }
        onMutateJob?.invoke(mutateJob)
    }

    private fun toggleTimerEnabled(timer: ExtendedHashMap) {
        val host = hostFragment ?: return
        val ctx = context ?: return
        val timerNew = timer.clone()
        if (timerNew.getString(Timer.KEY_DISABLED) == "1") {
            timerNew.put(Timer.KEY_DISABLED, "0")
        } else {
            timerNew.put(Timer.KEY_DISABLED, "1")
        }
        dismissProgress()
        progress = ProgressDialog.show(activity, "", ctx.getText(R.string.saving), true)
        val params = Timer.getSaveParams(timerNew, timer)
        mutateJob?.cancel()
        mutateJob = host.launchSimpleResultLoad(TimerChangeRequestHandler(), params) { _, result, http ->
            onSimpleResult(result, http)
        }
        onMutateJob?.invoke(mutateJob)
    }

    private fun cleanupTimerList() {
        val host = hostFragment ?: return
        val ctx = context ?: return
        dismissProgress()
        progress = ProgressDialog.show(activity, "", ctx.getText(R.string.cleaning_timerlist), true)
        mutateJob?.cancel()
        mutateJob = host.launchSimpleResultLoad(
            TimerCleanupRequestHandler(),
            emptyList(),
        ) { _, result, http ->
            onSimpleResult(result, http)
        }
        onMutateJob?.invoke(mutateJob)
    }

    private fun onSimpleResult(
        result: ExtendedHashMap,
        http: net.reichholf.dreamdroid.helpers.SimpleHttpClient,
    ) {
        dismissProgress()
        val ctx = context ?: return
        var toastText = ctx.getText(R.string.get_content_error).toString()
        val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
        when {
            !stateText.isNullOrEmpty() -> toastText = stateText
            http.hasError() -> toastText = http.getErrorText(ctx).orEmpty()
        }
        toast(toastText)
        reload()
    }

    private fun onItemSelected(id: Int): Boolean {
        return when (id) {
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

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        when (action) {
            Statics.ACTION_EDIT -> editTimer(selected, create = false)
            Statics.ACTION_DELETE -> deleteTimerConfirm()
            Statics.ACTION_DELETE_CONFIRMED -> deleteTimer(selected)
            else -> Unit
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.timerlist, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return onItemSelected(menuItem.itemId)
    }
}
