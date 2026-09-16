package net.reichholf.dreamdroid.ui.current

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadCurrentService
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.epg.EpgDetailModalSheet
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContentOrUnavailable
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.launchSimpleResultLoad

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
    }
)

private val EventNullableSaver = Saver<Event?, Bundle>(
    save = { item ->
        Bundle().apply {
            if (item != null) {
                putSerializable(KEY_SAVED_ITEM, item)
            }
        }
    },
    restore = { bundle ->
        @Suppress("DEPRECATION")
        bundle.getSerializable(KEY_SAVED_ITEM) as? Event
    }
)

/**
 * Phase 2.7c: Current Service as a direct Compose NavHost destination.
 * Dialog actions (EPG sheet → timer/IMDb/similar) are registered on [handle].
 *
 * [updateToolbarTitle] is false when hosted in [CurrentServiceSheet] so the hub
 * bouquet title is not overwritten.
 */
@Composable
fun CurrentServiceDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    updateToolbarTitle: Boolean = true
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    val scope = rememberCoroutineScope()
    val uiState = remember { CurrentServiceUiState() }
    val refresh = remember { ComposeRefreshState() }
    val gate = remember { CurrentServiceLoadGate() }
    var profileId by remember {
        mutableIntStateOf(DreamDroid.getCurrentProfile().id ?: -1)
    }
    var current by rememberSaveable(profileId, stateSaver = CurrentServiceNullableSaver) {
        mutableStateOf<CurrentService?>(null)
    }
    var currentItem by rememberSaveable(stateSaver = EventNullableSaver) {
        mutableStateOf<Event?>(null)
    }
    var ready by rememberSaveable { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var detailEvent by remember { mutableStateOf<Event?>(null) }

    val baseTitle = context.getString(R.string.current_service)

    val session = remember { CurrentServiceSession() }
    session.current = current
    session.currentItem = currentItem
    session.ready = ready
    session.handle = handle
    session.context = context

    fun setToolbarTitle(title: String) {
        if (!updateToolbarTitle) {
            return
        }
        (context as? AppCompatActivity)?.title = title
    }

    fun publishVisible(generation: Int) {
        if (!gate.isCurrent(generation)) {
            return
        }
        val shown = gate.visible(DreamDroid.getCurrentProfile().id ?: -1)
        if (shown != null) {
            current = shown
            ready = true
            uiState.apply(shown)
            return
        }
        uiState.apply(null)
    }

    fun applyCurrent(generation: Int, loadProfileId: Int, content: CurrentService?) {
        if (content != null && !content.isEmpty()) {
            if (!gate.applySuccess(generation, loadProfileId, content)) {
                return
            }
        } else if (!gate.isCurrent(generation)) {
            return
        }
        publishVisible(generation)
    }

    fun showEpgDetail(event: Event?) {
        if (event == null) {
            return
        }
        currentItem = event
        detailEvent = event
    }

    fun streamService() {
        if (!currentServiceCanStream(current)) {
            return
        }
        val service = current?.service
        val ref = service?.reference.orEmpty()
        val name = service?.name.orEmpty()
        val activity = context as AppCompatActivity
        activity.startActivity(IntentFactory.getStreamServiceIntent(activity, ref, name))
    }

    fun onNowOrNextOrStream(action: Int) {
        if (!ready) {
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
        val generation = gate.beginLoad()
        val loadProfileId = DreamDroid.getCurrentProfile().id ?: -1
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadCurrentService(context.applicationContext)
            if (!gate.isCurrent(generation)) {
                return@launch
            }
            refresh.setRefreshing(false)
            setToolbarTitle(baseTitle)
            applyCurrent(
                generation,
                loadProfileId,
                content = if (result.success) result.current else null
            )
        }
    }

    DisposableEffect(prefs, gate) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DreamDroid.CURRENT_PROFILE) {
                profileId = prefs.getInt(DreamDroid.CURRENT_PROFILE, profileId)
                gate.beginLoad()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(handle, session) {
        handle.composeDialogActionListener = session
        setToolbarTitle(baseTitle)
        onDispose {
            if (handle.composeDialogActionListener === session) {
                handle.composeDialogActionListener = null
            }
            loadJob?.cancel()
            loadJob = null
            session.dismissProgress()
        }
    }

    LaunchedEffect(profileId) {
        val shown = gate.visible(profileId)
        if (shown != null) {
            current = shown
            ready = true
            uiState.apply(shown)
            setToolbarTitle(baseTitle)
        } else if (current == null || current!!.isEmpty()) {
            ready = false
            uiState.clear()
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
        modifier = modifier
    ) {
        CurrentServiceScreen(
            state = uiState,
            onNowClick = { onNowOrNextOrStream(Statics.ITEM_NOW) },
            onNextClick = { onNowOrNextOrStream(Statics.ITEM_NEXT) },
            onStream = { onNowOrNextOrStream(Statics.ITEM_STREAM) }
        )
    }

    detailEvent?.let { event ->
        val minutesShort = stringResource(R.string.minutes_short)
        val unavailable = stringResource(R.string.not_available)
        val content = event.toEpgDetailContentOrUnavailable(minutesShort, unavailable)
        EpgDetailModalSheet(
            content = content,
            onDismiss = { detailEvent = null },
            onSetTimer = {
                session.onDialogAction(Statics.ACTION_SET_TIMER, null, null)
            },
            onEditTimer = {
                session.onDialogAction(Statics.ACTION_EDIT_TIMER, null, null)
            },
            onImdb = {
                session.onDialogAction(Statics.ACTION_IMDB, null, null)
            },
            onSimilar = {
                session.onDialogAction(Statics.ACTION_FIND_SIMILAR, null, null)
            }
        )
    }

    IndeterminateProgressHost(session.progress)
}

private class CurrentServiceSession : DialogActionListener {
    var current: CurrentService? = null
    var currentItem: Event? = null
    var ready: Boolean = false
    var handle: PhoneNavHandle? = null
    var context: android.content.Context? = null
    var progress by mutableStateOf<IndeterminateProgressState?>(null)

    fun dismissProgress() {
        progress = null
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        val ctx = context ?: return
        val host = handle ?: return
        when (action) {
            Statics.ACTION_SET_TIMER -> {
                val event = currentItem ?: return
                progress = IndeterminateProgressState(message = ctx.getString(R.string.saving))
                host.launchSimpleResultLoad(
                    TimerAddByEventIdRequestHandler(),
                    Timer.getEventIdParams(event)
                ) { _, result, error ->
                    dismissProgress()
                    var toastText = ctx.getText(R.string.get_content_error).toString()
                    val stateText = result.stateText
                    when {
                        !stateText.isNullOrEmpty() -> toastText = stateText
                        error != null -> toastText = error.resolve(ctx).orEmpty()
                    }
                    Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show()
                }
            }

            Statics.ACTION_EDIT_TIMER -> {
                val event = currentItem ?: return
                host.navigateToTimerEdit(Timer.createByEvent(event), true)
            }

            Statics.ACTION_FIND_SIMILAR -> {
                val query = currentItem?.title
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
