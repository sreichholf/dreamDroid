package net.reichholf.dreamdroid.tv.ui

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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.services.timerListItemsFrom
import net.reichholf.dreamdroid.ui.text.asString

/**
 * TV hub Timers content: list / add / edit. The list reloads each time this enters
 * composition; [TvTimerHostViewModel] keeps what was loaded meanwhile.
 */
@Composable
fun TvTimerHost(modifier: Modifier = Modifier, viewModel: TvTimerHostViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    LaunchedEffect(viewModel) {
        viewModel.reload()
    }

    TvTimerHostContent(
        uiState = uiState,
        onAdd = viewModel::showAdd,
        onEdit = viewModel::showEdit,
        onToggleEnabled = viewModel::toggleEnabled,
        onDelete = viewModel::deleteTimer,
        onShowList = viewModel::showList,
        modifier = modifier
    ) { timer, isCreate ->
        TvTimerEditorHost(
            timer = timer,
            isCreate = isCreate,
            onDismiss = viewModel::showList,
            onSaved = {
                viewModel.showList()
                viewModel.reload()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * The timer list of [uiState], or [editor] for its Add or Edit page. The list's
 * [fillMaxSize] LazyColumn lives in [TvTimerListScreen]; do not nest another around it.
 * Mutations while [TvTimerHostUiState.mutationsBlocked] show [TvNeedsReceiverOverlay].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTimerHostContent(
    uiState: TvTimerHostUiState,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onToggleEnabled: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onShowList: () -> Unit,
    modifier: Modifier = Modifier,
    editor: @Composable (timer: Timer, isCreate: Boolean) -> Unit
) {
    var showNeedsReceiver by remember { mutableStateOf(false) }
    fun online(action: () -> Unit) {
        if (uiState.mutationsBlocked) showNeedsReceiver = true else action()
    }

    BackHandler(enabled = uiState.page !is TvTimerPage.List) {
        onShowList()
    }

    val resources = LocalResources.current
    val items = remember(uiState.timers, resources) {
        timerListItemsFrom(resources, uiState.timers)
    }
    val editing = uiState.editorTimer
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tv_timers_host")
    ) {
        if (uiState.page is TvTimerPage.List || editing == null) {
            Box(Modifier.fillMaxSize()) {
                TvTimerListScreen(
                    items = items,
                    onAdd = { online(onAdd) },
                    onToggleEnabled = { index -> online { onToggleEnabled(index) } },
                    onEdit = onEdit,
                    onDelete = {},
                    onDeleteConfirmed = { index -> online { onDelete(index) } },
                    modifier = Modifier.fillMaxSize()
                )
                val message = uiState.emptyMessage
                if (items.isEmpty() && message != null) {
                    Text(
                        text = message.asString(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
            }
        } else {
            editor(editing, uiState.page is TvTimerPage.Add)
        }

        if (showNeedsReceiver) {
            TvNeedsReceiverOverlay(onDismiss = { showNeedsReceiver = false })
        }
        IndeterminateProgressHost(
            uiState.progress?.let { IndeterminateProgressState(message = it.asString()) }
        )
    }
}
