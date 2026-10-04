package net.reichholf.dreamdroid.ui.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BackupDocuments
import net.reichholf.dreamdroid.data.BackupRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.ui.text.UiText

/** A saved profile and whether the export includes it. */
data class BackupProfileToggle(
    val id: Int,
    val name: String,
    val host: String,
    val current: Boolean = false,
    val checked: Boolean = true
)

/**
 * The export choices. App settings and receiver passwords stay out of the file until the
 * user turns [exportSettings] or [includePasswords] on. [confirmingPasswords] shows the
 * warning before an export that includes passwords; [pickingExport] asks the destination
 * to open the document picker once.
 */
data class BackupUiState(
    val profiles: List<BackupProfileToggle> = emptyList(),
    val exportSettings: Boolean = false,
    val includePasswords: Boolean = false,
    val confirmingPasswords: Boolean = false,
    val pickingExport: Boolean = false,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.backup)

    val selectedProfiles: Int
        get() = profiles.count { it.checked }

    /** False when the file would hold neither a profile nor the settings. */
    val canExport: Boolean
        get() = selectedProfiles > 0 || exportSettings

    /** Passwords only reach the file with a profile that carries them. */
    val passwordsInExport: Boolean
        get() = includePasswords && selectedProfiles > 0
}

/**
 * Import and export of profiles and settings. The destination picks the documents; this
 * reads and writes them through [BackupDocuments]. The export choices survive process
 * death in the [SavedStateHandle].
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val backups: BackupRepository,
    private val profiles: ProfileRepository,
    private val documents: BackupDocuments
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BackupUiState(
            exportSettings = savedStateHandle[KEY_EXPORT_SETTINGS] ?: false,
            includePasswords = savedStateHandle[KEY_INCLUDE_PASSWORDS] ?: false
        )
    )
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var excluded: Set<Int> =
        savedStateHandle.get<IntArray>(KEY_EXCLUDED)?.toSet() ?: emptySet()

    init {
        reload()
    }

    fun setProfileChecked(id: Int, checked: Boolean) {
        excluded = if (checked) excluded - id else excluded + id
        savedStateHandle[KEY_EXCLUDED] = excluded.toIntArray()
        _uiState.update { state ->
            state.copy(
                profiles = state.profiles.map { row ->
                    if (row.id == id) row.copy(checked = checked) else row
                }
            )
        }
    }

    fun setAllProfilesChecked(checked: Boolean) {
        val ids = _uiState.value.profiles.map { it.id }
        excluded = if (checked) excluded - ids.toSet() else excluded + ids
        savedStateHandle[KEY_EXCLUDED] = excluded.toIntArray()
        _uiState.update { state ->
            state.copy(profiles = state.profiles.map { it.copy(checked = checked) })
        }
    }

    fun setExportSettings(export: Boolean) {
        savedStateHandle[KEY_EXPORT_SETTINGS] = export
        _uiState.update { it.copy(exportSettings = export) }
    }

    fun setIncludePasswords(include: Boolean) {
        savedStateHandle[KEY_INCLUDE_PASSWORDS] = include
        _uiState.update { it.copy(includePasswords = include) }
    }

    /**
     * The export button: says so when there is nothing to export, warns before an export
     * with passwords, and otherwise opens the picker.
     */
    fun requestExport() {
        val state = _uiState.value
        when {
            !state.canExport -> showMessage(UiText.Resource(R.string.backup_nothing_selected))
            state.passwordsInExport -> _uiState.update { it.copy(confirmingPasswords = true) }
            else -> _uiState.update { it.copy(pickingExport = true) }
        }
    }

    fun confirmPasswords() {
        _uiState.update { it.copy(confirmingPasswords = false, pickingExport = true) }
    }

    fun dismissPasswordWarning() {
        _uiState.update { it.copy(confirmingPasswords = false) }
    }

    fun onExportPickerOpened() {
        _uiState.update { it.copy(pickingExport = false) }
    }

    /** Imports the document at [uri], then lists the profiles again. */
    fun importFrom(uri: String) {
        viewModelScope.launch {
            val content = documents.read(uri)
            val imported = content != null &&
                withContext(Dispatchers.IO) { backups.importBackup(content) }
            val message = if (imported) {
                R.string.backup_import_successful
            } else {
                R.string.backup_import_error
            }
            showMessage(UiText.Resource(message))
            if (imported) {
                reload()
            }
        }
    }

    /** Writes the chosen profiles, and the settings if chosen, to [uri]. */
    fun exportTo(uri: String) {
        val state = _uiState.value
        val skipped = excluded
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                val data = backups.backupData()
                if (!state.exportSettings) {
                    data.settings = null
                }
                data.profiles.removeAll { it.id in skipped }
                backups.exportJson(data, state.passwordsInExport)
            }
            val message = if (documents.write(uri, json)) {
                R.string.backup_export_successful
            } else {
                R.string.backup_export_missing_permission
            }
            showMessage(UiText.Resource(message))
        }
    }

    /** No app can pick the document. [reason] is the system's text, when it gave one. */
    fun onImportPickerMissing(reason: String?) {
        showMessage(reason?.let(UiText::Raw) ?: UiText.Resource(R.string.backup_import_error))
    }

    fun onExportPickerMissing(reason: String?) {
        showMessage(
            reason?.let(UiText::Raw)
                ?: UiText.Resource(R.string.backup_export_missing_permission)
        )
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun reload() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { profiles.profiles() }
            val currentId = profiles.current.value?.id
            _uiState.update { state ->
                state.copy(
                    profiles = saved.map { profile ->
                        val id = profile.id ?: 0
                        BackupProfileToggle(
                            id = id,
                            name = profile.name.orEmpty(),
                            host = profile.host.orEmpty(),
                            current = profile.id != null && profile.id == currentId,
                            checked = id !in excluded
                        )
                    }
                )
            }
        }
    }

    private fun showMessage(message: UiText) {
        _uiState.update { it.copy(userMessage = message) }
    }

    private companion object {
        const val KEY_EXCLUDED = "backup_excluded_profiles"
        const val KEY_EXPORT_SETTINGS = "backup_export_settings"
        const val KEY_INCLUDE_PASSWORDS = "backup_include_passwords"
    }
}
