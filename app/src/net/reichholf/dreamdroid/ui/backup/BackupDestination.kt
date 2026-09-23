package net.reichholf.dreamdroid.ui.backup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog

private const val TAG = "BackupDestination"

private const val BACKUP_EXPORT_FILENAME = "dreamdroid_backup.json"

/**
 * Phase 2.7c: Backup as a direct Compose NavHost destination (no nested Fragment).
 * Profile toggles and the pending export live on [BackupViewModel].
 */
@Composable
fun BackupDestination(modifier: Modifier = Modifier, viewModel: BackupViewModel = viewModel()) {
    val context = LocalContext.current
    val importErrorText = stringResource(R.string.backup_import_error)
    val importSuccessText = stringResource(R.string.backup_import_successful)
    val exportPermissionText = stringResource(R.string.backup_export_missing_permission)
    val title = stringResource(R.string.backup)
    var showPasswordWarning by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    val pickImportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            if (!viewModel.importBackup(readTextFromUri(context, uri))) {
                toast(importErrorText)
                return@rememberLauncherForActivityResult
            }
            toast(importSuccessText)
        } catch (e: IOException) {
            Log.e(TAG, "unable to readTextFromUri:$uri", e)
            toast(importErrorText)
        }
    }

    val createBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = viewModel.pendingExportJson
        viewModel.pendingExportJson = null
        if (uri == null || json == null) {
            return@rememberLauncherForActivityResult
        }
        toast(backupExportUserMessage(context, writeBackupJson(context, uri, json)))
    }

    fun doImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" }
        try {
            pickImportFile.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast(e.localizedMessage ?: importErrorText)
        }
    }

    fun openExportPicker() {
        viewModel.prepareExport()
        try {
            createBackupFile.launch(BACKUP_EXPORT_FILENAME)
        } catch (e: ActivityNotFoundException) {
            viewModel.pendingExportJson = null
            toast(e.localizedMessage ?: exportPermissionText)
        }
    }

    fun onExportClicked() {
        if (viewModel.uiState.includePasswords) {
            showPasswordWarning = true
        } else {
            openExportPicker()
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? AppCompatActivity
        activity?.title = title
        onDispose { }
    }

    LaunchedEffect(Unit) {
        viewModel.reload()
    }

    BackupScreen(
        state = viewModel.uiState,
        onImport = { doImport() },
        onExport = { onExportClicked() },
        modifier = modifier
    )

    if (showPasswordWarning) {
        ConfirmAlertDialog(
            title = stringResource(R.string.backup_passwords_confirm_title),
            message = stringResource(R.string.backup_passwords_confirm),
            onDismiss = { showPasswordWarning = false },
            onConfirm = { openExportPicker() },
            confirmLabel = stringResource(R.string.backup_export)
        )
    }
}

@Throws(IOException::class)
private fun readTextFromUri(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Unable to open backup $uri")
    return inputStream.use { stream ->
        BufferedReader(InputStreamReader(stream)).use { reader ->
            val builder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
            builder.toString()
        }
    }
}

private fun writeBackupJson(context: Context, uri: Uri, json: String): Boolean {
    return try {
        val output = context.contentResolver.openOutputStream(uri) ?: return false
        output.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (e: IOException) {
        Log.e(TAG, "Export write failed.", e)
        false
    } catch (e: SecurityException) {
        Log.e(TAG, "Export write failed.", e)
        false
    }
}

internal fun backupExportUserMessage(context: Context, exported: Boolean): String = if (exported) {
    context.getString(R.string.backup_export_successful)
} else {
    context.getString(R.string.backup_export_missing_permission)
}
