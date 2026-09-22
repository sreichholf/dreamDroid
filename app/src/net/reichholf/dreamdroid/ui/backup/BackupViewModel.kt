package net.reichholf.dreamdroid.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.BackupService

/**
 * Owns [BackupUiState], the in-memory backup, and the export JSON staged for the document picker.
 * [savedStateHandle] is accepted by the default factory. Backup has no saved keys.
 */
class BackupViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val uiState: BackupUiState = BackupUiState()

    /** JSON waiting for the destination's create-document callback. Memory only. */
    var pendingExportJson: String? = null

    private val backupService = BackupService(getApplication())
    private var backupData: BackupData = backupService.getBackupData()

    fun reload() {
        val app = getApplication<Application>()
        val data = backupService.getBackupData()
        backupData = data
        uiState.setProfilesFromBackup(
            data.profiles,
            DreamDroid.getCurrentProfile().id ?: -1,
            app.getString(R.string.backup_current_profile)
        )
    }

    /** Returns false when [content] cannot be imported. Reloads profiles on success. */
    fun importBackup(content: String): Boolean {
        if (!backupService.doImport(content)) {
            return false
        }
        reload()
        return true
    }

    fun prepareExport() {
        applyExportSelection(backupData, uiState)
        pendingExportJson = backupService.exportJson(backupData, uiState.includePasswords)
        reload()
    }
}

private fun applyExportSelection(data: BackupData, state: BackupUiState) {
    if (!state.exportSettings) {
        data.settings = null
    }
    val excluded = state.profiles.filterNot { it.checked }.map { it.id }.toHashSet()
    if (excluded.isNotEmpty()) {
        data.profiles.removeIf { excluded.contains(it.id) }
    }
}
