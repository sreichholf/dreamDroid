package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.enigma.loadTimerList
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.TimerSnapshotStore
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
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
    val pid = ProfileRepository.get().requireCurrent().id ?: return
    TimerSnapshotStore.replace(AppDatabase.timer(context), pid, timers)
}

internal suspend fun tvTimerLoadSnapshot(context: Context): List<TypedTimer>? {
    val pid = ProfileRepository.get().requireCurrent().id ?: return null
    return TimerSnapshotStore.load(AppDatabase.timer(context), pid)
}

/**
 * Snapshot first when the box is not Online, otherwise live with the snapshot as the
 * failure fallback. A live success replaces the snapshot.
 */
internal suspend fun tvTimerLoadPaint(context: Context): TvTimerLoadPaint {
    val snapshot = tvTimerLoadSnapshot(context)
    val skipHttp = shouldSkipTvHubHttp(
        SessionConnectionHolder.shared.status.value,
        snapshot != null
    )
    if (skipHttp && snapshot != null) {
        return tvTimerPaintFromLoad(true, snapshot, null, null)
    }
    val result = loadTimerList(context.applicationContext)
    if (result.success) {
        tvTimerPersistSnapshot(context, result.timers)
    }
    return tvTimerPaintFromLoad(
        result.success,
        result.timers,
        if (result.success) null else snapshot,
        result.errorText
    )
}

/**
 * TV hub Timers content: list / add / edit. [fillMaxSize] LazyColumn lives in
 * [TvTimerListScreen]; this host must not nest another LazyColumn around it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTimerHost(
    modifier: Modifier = Modifier,
    mutationsBlocked: Boolean = false,
    viewModel: TvTimerHostViewModel = viewModel()
) {
    val page = viewModel.page
    var showNeedsReceiver by remember { mutableStateOf(false) }
    val items = viewModel.items

    fun toggleEnabled(index: Int) {
        if (mutationsBlocked) {
            showNeedsReceiver = true
            return
        }
        viewModel.toggleEnabled(index)
    }

    fun deleteTimer(index: Int) {
        if (mutationsBlocked) {
            showNeedsReceiver = true
            return
        }
        viewModel.deleteTimer(index)
    }

    fun onEditorDismiss() {
        viewModel.showList()
    }

    fun onEditorSaved() {
        viewModel.showList()
        viewModel.reload()
    }

    LaunchedEffect(Unit) {
        viewModel.reload()
    }

    BackHandler(enabled = page !is TvTimerPage.List) {
        viewModel.showList()
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
                                viewModel.showAdd()
                            }
                        },
                        onToggleEnabled = { toggleEnabled(it) },
                        onEdit = { index -> viewModel.showEdit(index) },
                        onDelete = {},
                        onDeleteConfirmed = { deleteTimer(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                    val message = viewModel.emptyMessage
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
                val created = viewModel.editorTimer
                if (created != null) {
                    TvTimerEditorHost(
                        timer = created,
                        isCreate = true,
                        onDismiss = { onEditorDismiss() },
                        onSaved = { onEditorSaved() },
                        modifier = Modifier.fillMaxSize(),
                        mutationsBlocked = mutationsBlocked
                    )
                }
            }

            is TvTimerPage.Edit -> {
                val editing = viewModel.editorTimer
                if (editing == null) {
                    LaunchedEffect(current.index) {
                        viewModel.showList()
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
        IndeterminateProgressHost(viewModel.progress)
    }
}
