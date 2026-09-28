package net.reichholf.dreamdroid.ui.timers

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.ui.compose.saveAndDeleteActions
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle

/**
 * Timer create/edit as a NavHost destination; the route is read by the ViewModel. The
 * service pick answers through [PhoneNavHandle.composeActivityResultListener]. A save or
 * delete that went through pops this destination with `RESULT_OK`.
 */
@Composable
fun TimerEditDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: TimerEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    DisposableEffect(handle, viewModel) {
        val listener = PhoneNavHandle.ActivityResultListener { requestCode, resultCode, data ->
            if (requestCode == Statics.REQUEST_PICK_SERVICE && resultCode == Activity.RESULT_OK) {
                data?.getSerializableExtraCompat<Service>(NavExtras.DATA)
                    ?.let(viewModel::onServicePicked)
            }
        }
        handle.composeActivityResultListener = listener
        handle.dispatchPendingComposeActivityResult()
        onDispose {
            if (handle.composeActivityResultListener === listener) {
                handle.composeActivityResultListener = null
            }
        }
    }

    // A save that finishes during the service pick lands here once the form is back.
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            viewModel.onFinishHandled()
            handle.deliverPickResult(Activity.RESULT_OK, null)
        }
    }

    fun online(action: () -> Unit) {
        if (uiState.mutationsBlocked) handle.requestNeedsReceiver() else action()
    }

    BindShellTopBarActions(
        saveAndDeleteActions(
            saveLabel = stringResource(R.string.save),
            deleteLabel = stringResource(R.string.delete),
            canDelete = !uiState.isCreate,
            actionsEnabled = uiState.progress == null,
            onSave = { online(viewModel::save) },
            onDelete = { online { showDeleteConfirm = true } }
        )
    )

    TimerEditContent(
        uiState = uiState,
        name = viewModel.name.state,
        description = viewModel.description.state,
        actions = viewModel,
        onPickService = { handle.navigateToTimerServicePick() },
        modifier = modifier
    )

    if (showDeleteConfirm) {
        ConfirmAlertDialog(
            title = uiState.timer?.name.orEmpty(),
            message = stringResource(R.string.delete_confirm),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = viewModel::delete,
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }
}
