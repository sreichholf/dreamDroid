package net.reichholf.dreamdroid.ui.profiles

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
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
import net.reichholf.dreamdroid.ui.compose.saveAndDeleteActions
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellViewModel
import net.reichholf.dreamdroid.ui.text.asString

/** Profile create/edit as a NavHost destination; the route is read by the ViewModel. */
@Composable
fun ProfileEditDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditViewModel = hiltViewModel(),
    shellActions: ShellViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val finished = uiState.finished
    LaunchedEffect(finished) {
        if (finished != null) {
            // The confirmation outlives this destination, so it goes to the
            // shell ViewModel's message instead of this screen's state.
            shellActions.showMessage(finished)
            handle.deliverPickResult(Activity.RESULT_OK, null)
        }
    }

    val form = uiState.form ?: return
    BindShellTopBarActions(
        saveAndDeleteActions(
            saveLabel = stringResource(R.string.save),
            deleteLabel = stringResource(R.string.delete),
            canDelete = uiState.canDelete,
            onSave = viewModel::save,
            onDelete = { showDeleteConfirm = true }
        )
    )

    ProfileEditScreen(
        form = form,
        fields = viewModel.fields,
        hostError = uiState.hostError?.asString(),
        onFormChange = viewModel::onFormChange,
        onSslChange = viewModel::onSslChange,
        saveLabel = stringResource(R.string.save),
        onSave = viewModel::save,
        showSaveFab = false,
        modifier = modifier
    )

    if (showDeleteConfirm) {
        ConfirmAlertDialog(
            title = uiState.savedName,
            message = stringResource(R.string.confirm_delete_profile),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = viewModel::delete,
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }
}
