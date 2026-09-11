package net.reichholf.dreamdroid.ui.current

import android.app.ProgressDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadCurrentService
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.epg.EpgListMapper
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys

private const val KEY_SAVED_CURRENT = "current_service"
private const val KEY_SAVED_ITEM = "current_item"

private val CurrentServiceNullableSaver = Saver<CurrentService?, Bundle>(
    save = { current ->
        Bundle().apply {
            if (current != null) {
                putSerializable(KEY_SAVED_CURRENT, current)
            }
        }
    },
    restore = { bundle ->
        @Suppress("DEPRECATION")
        bundle.getSerializable(KEY_SAVED_CURRENT) as? CurrentService
    },
)

private val ExtendedHashMapNullableSaver = Saver<ExtendedHashMap?, Bundle>(
    save = { item ->
        Bundle().apply {
            if (item != null) {
                putSerializable(KEY_SAVED_ITEM, item)
            }
        }
    },
    restore = { bundle ->
        @Suppress("DEPRECATION")
        bundle.getSerializable(KEY_SAVED_ITEM) as? ExtendedHashMap
    },
)

/**
 * Phase 2.7c: Current Service as a direct Compose NavHost destination.
 * Dialog actions (EPG sheet → timer/IMDb/similar) are registered on [hostFragment].
 */
@Composable
fun CurrentServiceDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState = remember { CurrentServiceUiState() }
    val refresh = remember { ComposeRefreshState() }
    var current by rememberSaveable(stateSaver = CurrentServiceNullableSaver) {
        mutableStateOf<CurrentService?>(null)
    }
    var currentItem by rememberSaveable(stateSaver = ExtendedHashMapNullableSaver) {
        mutableStateOf<ExtendedHashMap?>(null)
    }
    var ready by rememberSaveable { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    val baseTitle = context.getString(R.string.current_service)

    val session = remember { CurrentServiceSession() }
    session.current = current
    session.currentItem = currentItem
    session.ready = ready
    session.hostFragment = hostFragment
    session.context = context

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun toast(message: CharSequence) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun applyCurrent(content: CurrentService?) {
        if (content != null && !content.isEmpty()) {
            current = content
            ready = true
            uiState.apply(content)
        } else {
            if (!ready) {
                uiState.apply(null)
            }
            toast(context.getText(R.string.not_available))
        }
    }

    fun showEpgDetail(event: Event?) {
        if (event == null) {
            return
        }
        currentItem = EpgListMapper.toExtendedHashMap(event)
        val sheet = EpgDetailBottomSheet.newInstance(event)
        (context as MultiPaneHandler).showDialogFragment(sheet, "current_epg_detail_dialog")
    }

    fun streamService() {
        val service = current?.service
        val ref = service?.reference.orEmpty()
        val name = service?.name.orEmpty()
        if (ref.isEmpty()) {
            toast(context.getText(R.string.not_available))
            return
        }
        val activity = context as AppCompatActivity
        activity.startActivity(IntentFactory.getStreamServiceIntent(activity, ref, name))
    }

    fun onNowOrNextOrStream(action: Int) {
        if (!ready) {
            toast(context.getText(R.string.not_available))
            return
        }
        when (action) {
            Statics.ITEM_NOW -> showEpgDetail(current?.now)
            Statics.ITEM_NEXT -> showEpgDetail(current?.next)
            Statics.ITEM_STREAM -> streamService()
        }
    }

    fun reload() {
        refresh.setRefreshing(true)
        setToolbarTitle(context.getString(R.string.loading))
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadCurrentService(context.applicationContext)
            refresh.setRefreshing(false)
            setToolbarTitle(baseTitle)
            if (!result.success) {
                if (!ready) {
                    uiState.apply(null)
                }
                toast(result.errorText ?: context.getText(R.string.not_available))
                return@launch
            }
            applyCurrent(result.current)
        }
    }

    DisposableEffect(hostFragment, session) {
        hostFragment.composeDialogActionListener = session
        setToolbarTitle(baseTitle)
        onDispose {
            if (hostFragment.composeDialogActionListener === session) {
                hostFragment.composeDialogActionListener = null
            }
            loadJob?.cancel()
            loadJob = null
            session.dismissProgress()
        }
    }

    LaunchedEffect(Unit) {
        if (current == null || current!!.isEmpty()) {
            reload()
        } else {
            ready = true
            uiState.apply(current)
            setToolbarTitle(baseTitle)
        }
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        CurrentServiceScreen(
            state = uiState,
            onNowClick = { onNowOrNextOrStream(Statics.ITEM_NOW) },
            onNextClick = { onNowOrNextOrStream(Statics.ITEM_NEXT) },
            onStream = { onNowOrNextOrStream(Statics.ITEM_STREAM) },
        )
    }
}

private class CurrentServiceSession : ActionDialog.DialogActionListener {
    var current: CurrentService? = null
    var currentItem: ExtendedHashMap? = null
    var ready: Boolean = false
    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    private var progress: ProgressDialog? = null

    fun dismissProgress() {
        progress?.takeIf { it.isShowing }?.dismiss()
        progress = null
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        val ctx = context ?: return
        val host = hostFragment ?: return
        when (action) {
            Statics.ACTION_SET_TIMER -> {
                val event = currentItem ?: return
                dismissProgress()
                progress = ProgressDialog.show(ctx, "", ctx.getText(R.string.saving), true)
                host.launchSimpleResultLoad(
                    TimerAddByEventIdRequestHandler(),
                    Timer.getEventIdParams(event),
                ) { _, result, http ->
                    dismissProgress()
                    var toastText = ctx.getText(R.string.get_content_error).toString()
                    val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
                    when {
                        !stateText.isNullOrEmpty() -> toastText = stateText
                        http.hasError() -> toastText = http.getErrorText(ctx).orEmpty()
                    }
                    Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show()
                }
            }
            Statics.ACTION_EDIT_TIMER -> {
                val event = currentItem ?: return
                host.navigateToTimerEdit(Timer.createByEvent(event), true)
            }
            Statics.ACTION_FIND_SIMILAR -> {
                val query = currentItem?.getString(EventKeys.KEY_EVENT_TITLE)
                host.navigateToEpgSearch(query)
            }
            Statics.ACTION_IMDB -> {
                val event = currentItem ?: return
                val activity = ctx as? AppCompatActivity ?: return
                IntentFactory.queryIMDb(activity, event)
            }
        }
    }
}
