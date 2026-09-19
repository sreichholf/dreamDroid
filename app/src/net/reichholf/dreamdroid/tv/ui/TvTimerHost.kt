package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadTimerList
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.TimerSnapshotStore
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.services.TimerListItem
import net.reichholf.dreamdroid.ui.services.timerListItemsFrom
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

internal sealed interface TvTimerPage {
    data object List : TvTimerPage
    data object Add : TvTimerPage
    data class Edit(val index: Int) : TvTimerPage
}

internal data class TvTimerLoadPaint(
    val timers: List<TypedTimer>,
    val success: Boolean,
    val errorText: String?
)

internal fun tvTimerToggledDisabled(disabled: String): String = if (disabled == "1") "0" else "1"

internal fun tvTimerPaintFromLoad(
    resultSuccess: Boolean,
    liveTimers: List<TypedTimer>,
    snapshot: List<TypedTimer>?,
    errorText: String?
): TvTimerLoadPaint {
    if (resultSuccess) {
        return TvTimerLoadPaint(liveTimers, true, null)
    }
    if (snapshot != null) {
        return TvTimerLoadPaint(snapshot, true, null)
    }
    return TvTimerLoadPaint(emptyList(), false, errorText)
}

internal suspend fun tvTimerPersistSnapshot(context: Context, timers: List<TypedTimer>) {
    val pid = DreamDroid.getCurrentProfile().id ?: return
    TimerSnapshotStore.replace(AppDatabase.timer(context), pid, timers)
}

internal suspend fun tvTimerLoadSnapshot(context: Context): List<TypedTimer>? {
    val pid = DreamDroid.getCurrentProfile().id ?: return null
    return TimerSnapshotStore.load(AppDatabase.timer(context), pid)
}

/**
 * TV hub Timers content: list / add / edit. [fillMaxSize] LazyColumn lives in
 * [TvTimerListScreen]; this host must not nest another LazyColumn around it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTimerHost(modifier: Modifier = Modifier, mutationsBlocked: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<TvTimerPage>(TvTimerPage.List) }
    var timers by remember { mutableStateOf<List<TypedTimer>>(emptyList()) }
    var items by remember { mutableStateOf<List<TimerListItem>>(emptyList()) }
    var emptyMessage by remember {
        mutableStateOf<String?>(context.getString(R.string.loading))
    }
    var progress by remember { mutableStateOf<IndeterminateProgressState?>(null) }
    var showNeedsReceiver by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var mutateJob by remember { mutableStateOf<Job?>(null) }
    val loadGeneration = remember { intArrayOf(0) }

    fun applyPaint(paint: TvTimerLoadPaint) {
        if (paint.success) {
            timers = paint.timers
            items = timerListItemsFrom(context, paint.timers)
            emptyMessage = if (paint.timers.isEmpty()) {
                context.getString(R.string.no_list_item)
            } else {
                null
            }
        } else {
            timers = emptyList()
            items = emptyList()
            emptyMessage = paint.errorText
        }
    }

    fun reload() {
        if (timers.isEmpty()) {
            emptyMessage = context.getString(R.string.loading)
        }
        val generation = ++loadGeneration[0]
        loadJob?.cancel()
        loadJob = scope.launch {
            val snapshot = tvTimerLoadSnapshot(context)
            val skipHttp = shouldSkipTvHubHttp(
                SessionConnectionHolder.shared.status.value,
                snapshot != null
            )
            if (skipHttp && snapshot != null) {
                if (generation != loadGeneration[0]) {
                    return@launch
                }
                applyPaint(tvTimerPaintFromLoad(true, snapshot, null, null))
                return@launch
            }
            val result = loadTimerList(context.applicationContext)
            if (generation != loadGeneration[0]) {
                return@launch
            }
            if (result.success) {
                tvTimerPersistSnapshot(context, result.timers)
            }
            applyPaint(
                tvTimerPaintFromLoad(
                    result.success,
                    result.timers,
                    if (result.success) null else snapshot,
                    result.errorText
                )
            )
        }
    }

    fun onSimpleResult(result: SimpleResult, error: EnigmaHttpError?) {
        progress = null
        var toastText = context.getText(R.string.get_content_error).toString()
        val stateText = result.stateText
        when {
            !stateText.isNullOrEmpty() -> toastText = stateText
            error != null -> toastText = error.resolve(context).orEmpty()
        }
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
        reload()
    }

    fun toggleEnabled(index: Int) {
        if (mutationsBlocked) {
            showNeedsReceiver = true
            return
        }
        val timer = timers.getOrNull(index) ?: return
        val host = lifecycleOwner ?: return
        if (progress != null) {
            return
        }
        val timerNew = timer.copy(disabled = tvTimerToggledDisabled(timer.disabled))
        progress = IndeterminateProgressState(message = context.getString(R.string.saving))
        mutateJob?.cancel()
        mutateJob = host.launchSimpleResultLoad(
            TimerChangeRequestHandler(),
            Timer.getSaveParams(timerNew, timer)
        ) { _, result, error ->
            onSimpleResult(result, error)
        }
    }

    fun deleteTimer(index: Int) {
        if (mutationsBlocked) {
            showNeedsReceiver = true
            return
        }
        val timer = timers.getOrNull(index) ?: return
        val host = lifecycleOwner ?: return
        if (progress != null) {
            return
        }
        progress = IndeterminateProgressState(message = context.getString(R.string.deleting))
        mutateJob?.cancel()
        mutateJob = host.launchSimpleResultLoad(
            TimerDeleteRequestHandler(),
            Timer.getDeleteParams(timer)
        ) { _, result, error ->
            onSimpleResult(result, error)
        }
    }

    fun onEditorDismiss() {
        page = TvTimerPage.List
    }

    fun onEditorSaved() {
        page = TvTimerPage.List
        reload()
    }

    LaunchedEffect(Unit) {
        reload()
    }

    DisposableEffect(Unit) {
        onDispose {
            loadJob?.cancel()
            mutateJob?.cancel()
        }
    }

    BackHandler(enabled = page !is TvTimerPage.List) {
        page = TvTimerPage.List
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tv_timers_host")
    ) {
        when (val current = page) {
            TvTimerPage.List -> {
                Box(Modifier.fillMaxSize()) {
                    TvTimerListScreen(
                        items = items,
                        onAdd = {
                            if (mutationsBlocked) {
                                showNeedsReceiver = true
                            } else {
                                page = TvTimerPage.Add
                            }
                        },
                        onToggleEnabled = { toggleEnabled(it) },
                        onEdit = { index -> page = TvTimerPage.Edit(index) },
                        onDelete = {},
                        onDeleteConfirmed = { deleteTimer(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                    val message = emptyMessage
                    if (items.isEmpty() && message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }
                }
            }

            TvTimerPage.Add -> {
                val created = remember { Timer.getInitialTimer() }
                TvTimerEditorHost(
                    timer = created,
                    isCreate = true,
                    onDismiss = { onEditorDismiss() },
                    onSaved = { onEditorSaved() },
                    modifier = Modifier.fillMaxSize(),
                    mutationsBlocked = mutationsBlocked
                )
            }

            is TvTimerPage.Edit -> {
                val editing = remember(current.index) { timers.getOrNull(current.index) }
                if (editing == null) {
                    LaunchedEffect(current.index) {
                        page = TvTimerPage.List
                    }
                } else {
                    TvTimerEditorHost(
                        timer = editing,
                        isCreate = false,
                        onDismiss = { onEditorDismiss() },
                        onSaved = { onEditorSaved() },
                        modifier = Modifier.fillMaxSize(),
                        mutationsBlocked = mutationsBlocked
                    )
                }
            }
        }

        if (showNeedsReceiver) {
            TvNeedsReceiverOverlay(onDismiss = { showNeedsReceiver = false })
        }
        IndeterminateProgressHost(progress)
    }
}
