package net.reichholf.dreamdroid.ui.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

private const val BACKUP_EXPORT_FILENAME = "dreamdroid_backup.json"
private const val BACKUP_MIME_TYPE = "application/json"

/** Backup as a NavHost destination. The system document pickers run here. */
@Composable
fun BackupDestination(modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    val pickImportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFrom(it.toString()) }
    }
    val createBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri ->
        uri?.let { viewModel.exportTo(it.toString()) }
    }

    fun openImportPicker() {
        try {
            pickImportFile.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            viewModel.onImportPickerMissing(e.localizedMessage)
        }
    }

    fun openExportPicker() {
        try {
            createBackupFile.launch(BACKUP_EXPORT_FILENAME)
        } catch (e: ActivityNotFoundException) {
            viewModel.onExportPickerMissing(e.localizedMessage)
        }
    }

    BackupScreen(
        state = uiState,
        onImport = ::openImportPicker,
        onExport = {
            if (uiState.includePasswords) viewModel.confirmPasswords() else openExportPicker()
        },
        onProfileCheckedChange = viewModel::setProfileChecked,
        onExportSettingsChange = viewModel::setExportSettings,
        onIncludePasswordsChange = viewModel::setIncludePasswords,
        modifier = modifier
    )

    if (uiState.confirmingPasswords) {
        ConfirmAlertDialog(
            title = stringResource(R.string.backup_passwords_confirm_title),
            message = stringResource(R.string.backup_passwords_confirm),
            onDismiss = viewModel::dismissPasswordWarning,
            onConfirm = ::openExportPicker,
            confirmLabel = stringResource(R.string.backup_export)
        )
    }
}
