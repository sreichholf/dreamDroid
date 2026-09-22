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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.BackupService

private const val TAG = "BackupDestination"

private const val EXPORT_FILE_NAME = "dreamdroid_backup.json"

/**
 * Phase 2.7c: Backup as a direct Compose NavHost destination (no nested Fragment).
 */
@Composable
fun BackupDestination(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backupService = remember { BackupService(context.applicationContext) }
    val uiState = remember { BackupUiState() }
    val pendingExport = remember { mutableStateOf<BackupData?>(null) }
    val pendingIncludePasswords = remember { mutableStateOf(true) }

    fun reloadBackupData() {
        uiState.setProfilesFromBackup(
            backupService.getBackupData().profiles,
            DreamDroid.getCurrentProfile().id ?: -1,
            context.getString(R.string.backup_current_profile)
        )
    }

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
            if (!backupService.doImport(readTextFromUri(context, uri))) {
                toast(context.getString(R.string.backup_import_error))
                return@rememberLauncherForActivityResult
            }
            reloadBackupData()
            toast(context.getString(R.string.backup_import_successful))
        } catch (e: IOException) {
            Log.e(TAG, "unable to readTextFromUri:$uri", e)
            toast(context.getString(R.string.backup_import_error))
        }
    }

    val createExportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val data = pendingExport.value
        val includePasswords = pendingIncludePasswords.value
        pendingExport.value = null
        if (uri == null || data == null) {
            return@rememberLauncherForActivityResult
        }
        val exported = backupService.doExport(data, uri, includePasswords)
        toast(backupExportUserMessage(context, exported))
    }

    fun doImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" }
        try {
            pickImportFile.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast(e.localizedMessage ?: context.getString(R.string.backup_import_error))
        }
    }

    fun launchExportPicker() {
        val data = backupService.getBackupData()
        if (!uiState.exportSettings) {
            data.settings = null
        }
        val excluded = uiState.profiles.filterNot { it.checked }.map { it.id }.toHashSet()
        if (excluded.isNotEmpty()) {
            data.profiles.removeIf { excluded.contains(it.id) }
        }
        pendingExport.value = data
        pendingIncludePasswords.value = uiState.includePasswords
        try {
            createExportFile.launch(EXPORT_FILE_NAME)
        } catch (e: ActivityNotFoundException) {
            pendingExport.value = null
            toast(e.localizedMessage ?: context.getString(R.string.backup_export_write_failed))
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? AppCompatActivity
        activity?.title = context.getString(R.string.backup)
        onDispose { }
    }

    LaunchedEffect(Unit) {
        reloadBackupData()
    }

    BackupScreen(
        state = uiState,
        onImport = { doImport() },
        onExport = { launchExportPicker() },
        modifier = modifier
    )
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

internal fun backupExportUserMessage(context: Context, exported: Boolean): String = if (exported) {
    context.getString(R.string.backup_export_successful)
} else {
    context.getString(R.string.backup_export_write_failed)
}
