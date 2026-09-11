package net.reichholf.dreamdroid.ui.backup

import android.app.Activity
import android.content.ActivityNotFoundException
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
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.BackupService
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

private const val TAG = "BackupDestination"

/**
 * Phase 2.7c: Backup as a direct Compose NavHost destination (no nested Fragment).
 */
@Composable
fun BackupDestination(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backupService = remember { BackupService(context.applicationContext) }
    val uiState = remember { BackupUiState() }
    var backupData by remember { mutableStateOf(backupService.backupData) }

    fun refreshProfileToggles(data: BackupData) {
        uiState.setProfilesFromBackup(
            data.getProfiles(),
            DreamDroid.getCurrentProfile().id,
            context.getString(R.string.backup_current_profile),
        )
    }

    fun reloadBackupData() {
        val data = backupService.backupData
        backupData = data
        refreshProfileToggles(data)
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    val pickImportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            backupService.doImport(readTextFromUri(context, uri))
            reloadBackupData()
            toast(context.getString(R.string.backup_import_successful))
        } catch (e: IOException) {
            Log.e(TAG, "unable to readTextFromUri:$uri", e)
            toast(context.getString(R.string.backup_import_error))
        }
    }

    fun doImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" }
        try {
            pickImportFile.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast(e.localizedMessage ?: context.getString(R.string.backup_import_error))
        }
    }

    fun doExport() {
        val data = backupData
        if (!uiState.exportSettings) {
            data.setSettings(null)
        }
        val excluded = uiState.profiles.filterNot { it.checked }.map { it.id }.toHashSet()
        if (excluded.isNotEmpty()) {
            data.getProfiles().removeIf { excluded.contains(it.id) }
        }
        backupService.doExport(data)
        reloadBackupData()
        toast(context.getString(R.string.backup_export_successful))
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
        onExport = { doExport() },
        modifier = modifier,
    )
}

@Throws(IOException::class)
private fun readTextFromUri(context: android.content.Context, uri: Uri): String {
    context.contentResolver.openInputStream(uri).use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val builder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
            return builder.toString()
        }
    }
}
