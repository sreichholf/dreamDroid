package net.reichholf.dreamdroid.ui.timers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListDetailPaneTopBar
import net.reichholf.dreamdroid.ui.compose.saveAndDeleteActions
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarActionButton

/**
 * The timer form of [viewModel] in a detail pane. The service pick answers through the hub's
 * [PhoneNavHandle.composeActivityResultListener], which hands it to [viewModel].
 */
@Composable
fun TimerEditPaneHost(
    handle: PhoneNavHandle,
    viewModel: TimerPaneViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun online(action: () -> Unit) {
        if (uiState.mutationsBlocked) handle.requestNeedsReceiver() else action()
    }

    TimerEditPane(
        uiState = uiState,
        name = viewModel.name.state,
        description = viewModel.description.state,
        actions = viewModel,
        onPickService = { handle.navigateToTimerServicePick() },
        onSave = { online(viewModel::save) },
        onDelete = { online(viewModel::delete) },
        onClose = viewModel::dismiss,
        modifier = modifier
    )
}

/**
 * The timer form with its own [ListDetailPaneTopBar]. Delete asks first, unless the session
 * blocks it.
 */
@Composable
fun TimerEditPane(
    uiState: TimerEditUiState,
    name: TextFieldState,
    description: TextFieldState,
    actions: TimerFormActions,
    onPickService: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        ListDetailPaneTopBar(
            title = stringResource(if (uiState.isCreate) R.string.new_timer else R.string.timer),
            onClose = onClose
        ) {
            saveAndDeleteActions(
                saveLabel = stringResource(R.string.save),
                deleteLabel = stringResource(R.string.delete),
                canDelete = !uiState.isCreate,
                actionsEnabled = uiState.progress == null,
                onSave = onSave,
                // Blocked: [onDelete] explains why rather than asking first.
                onDelete = {
                    if (uiState.mutationsBlocked) onDelete() else showDeleteConfirm = true
                }
            ).forEach { ShellTopBarActionButton(it) }
        }
        TimerEditContent(
            uiState = uiState,
            name = name,
            description = description,
            actions = actions,
            onPickService = onPickService
        )
    }

    if (showDeleteConfirm) {
        ConfirmAlertDialog(
            title = uiState.timer?.name.orEmpty(),
            message = stringResource(R.string.delete_confirm),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = onDelete,
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }
}
