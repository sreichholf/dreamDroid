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
import net.reichholf.dreamdroid.data.ImportChoice
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.carriesPasswords
import net.reichholf.dreamdroid.helpers.backup.BackupData
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
    val importReview: ImportReview? = null,
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
 * A profile in the backup being imported, by its [index] in the file. [replaces] marks a
 * name that is already saved, whose row the import overwrites.
 */
data class ImportProfileToggle(
    val index: Int,
    val name: String,
    val host: String,
    val replaces: Boolean = false,
    val checked: Boolean = true
)

/**
 * What the user takes from a picked backup before anything is written. A part the file
 * does not hold ([passwordsAvailable], [settingsAvailable]) cannot be chosen.
 */
data class ImportReview(
    val profiles: List<ImportProfileToggle>,
    val passwordsAvailable: Boolean,
    val includePasswords: Boolean,
    val settingsAvailable: Boolean,
    val includeSettings: Boolean
) {
    val selectedProfiles: Int
        get() = profiles.count { it.checked }

    /** Passwords come with a chosen profile only. */
    val passwordsSelectable: Boolean
        get() = passwordsAvailable && selectedProfiles > 0

    val canImport: Boolean
        get() = selectedProfiles > 0 || includeSettings

    val choice: ImportChoice
        get() = ImportChoice(
            profiles = profiles.filter { it.checked }.map { it.index }.toSet(),
            passwords = includePasswords,
            settings = includeSettings
        )
}

/**
 * Import and export of profiles and settings. The destination picks the documents; this
 * reads and writes them through [BackupDocuments]. The export choices and a pending
 * import, with its choices, survive process death in the [SavedStateHandle].
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

    /** The parsed file behind [BackupUiState.importReview]. */
    private var pendingImport: BackupData? = null

    init {
        reload()
        savedStateHandle.get<String>(KEY_IMPORT_CONTENT)?.let { content ->
            viewModelScope.launch {
                val backup = backups.parse(content)
                if (backup == null) {
                    clearImport()
                } else {
                    review(backup)
                }
            }
        }
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

    /** Reads the document at [uri] and asks what to take from it. */
    fun importFrom(uri: String) {
        viewModelScope.launch {
            val content = documents.read(uri)
            val backup = content?.let { backups.parse(it) }
            if (backup == null) {
                showMessage(UiText.Resource(R.string.backup_import_error))
                return@launch
            }
            savedStateHandle[KEY_IMPORT_CONTENT] = content
            savedStateHandle.remove<IntArray>(KEY_IMPORT_EXCLUDED)
            savedStateHandle.remove<Boolean>(KEY_IMPORT_PASSWORDS)
            savedStateHandle.remove<Boolean>(KEY_IMPORT_SETTINGS)
            review(backup)
        }
    }

    fun setImportProfileChecked(index: Int, checked: Boolean) = updateImport { review ->
        review.copy(
            profiles = review.profiles.map {
                if (it.index == index) it.copy(checked = checked) else it
            }
        )
    }

    fun setAllImportProfilesChecked(checked: Boolean) = updateImport { review ->
        review.copy(profiles = review.profiles.map { it.copy(checked = checked) })
    }

    fun setImportPasswords(include: Boolean) = updateImport { review ->
        review.copy(includePasswords = include && review.passwordsAvailable)
    }

    fun setImportSettings(include: Boolean) = updateImport { review ->
        review.copy(includeSettings = include && review.settingsAvailable)
    }

    fun dismissImport() {
        clearImport()
    }

    /** Imports what the review chose, then lists the profiles again. */
    fun confirmImport() {
        val backup = pendingImport ?: return
        val review = _uiState.value.importReview ?: return
        if (!review.canImport) {
            return
        }
        clearImport()
        viewModelScope.launch {
            backups.importBackup(backup, review.choice)
            showMessage(UiText.Resource(R.string.backup_import_successful))
            reload()
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

    /** Shows [backup] for review, with any choices saved before process death. */
    private suspend fun review(backup: BackupData) {
        val savedNames = withContext(Dispatchers.IO) { profiles.profiles() }
            .mapNotNull { it.name }
            .toSet()
        val excludedRows = savedStateHandle.get<IntArray>(KEY_IMPORT_EXCLUDED)?.toSet().orEmpty()
        val passwordsAvailable = backup.carriesPasswords
        val settingsAvailable = !backup.settings.isNullOrEmpty()
        pendingImport = backup
        _uiState.update { state ->
            state.copy(
                importReview = ImportReview(
                    profiles = backup.profiles.mapIndexed { index, profile ->
                        ImportProfileToggle(
                            index = index,
                            name = profile.name.orEmpty(),
                            host = profile.host.orEmpty(),
                            replaces = profile.name.orEmpty() in savedNames,
                            checked = index !in excludedRows
                        )
                    },
                    passwordsAvailable = passwordsAvailable,
                    includePasswords = passwordsAvailable &&
                        savedStateHandle.get<Boolean>(KEY_IMPORT_PASSWORDS) ?: true,
                    settingsAvailable = settingsAvailable,
                    includeSettings = settingsAvailable &&
                        savedStateHandle.get<Boolean>(KEY_IMPORT_SETTINGS) ?: true
                )
            )
        }
    }

    private fun updateImport(transform: (ImportReview) -> ImportReview) {
        val review = _uiState.value.importReview?.let(transform) ?: return
        savedStateHandle[KEY_IMPORT_EXCLUDED] =
            review.profiles.filterNot { it.checked }.map { it.index }.toIntArray()
        savedStateHandle[KEY_IMPORT_PASSWORDS] = review.includePasswords
        savedStateHandle[KEY_IMPORT_SETTINGS] = review.includeSettings
        _uiState.update { it.copy(importReview = review) }
    }

    private fun clearImport() {
        pendingImport = null
        savedStateHandle.remove<String>(KEY_IMPORT_CONTENT)
        savedStateHandle.remove<IntArray>(KEY_IMPORT_EXCLUDED)
        savedStateHandle.remove<Boolean>(KEY_IMPORT_PASSWORDS)
        savedStateHandle.remove<Boolean>(KEY_IMPORT_SETTINGS)
        _uiState.update { it.copy(importReview = null) }
    }

    private fun showMessage(message: UiText) {
        _uiState.update { it.copy(userMessage = message) }
    }

    private companion object {
        const val KEY_EXCLUDED = "backup_excluded_profiles"
        const val KEY_EXPORT_SETTINGS = "backup_export_settings"
        const val KEY_INCLUDE_PASSWORDS = "backup_include_passwords"
        const val KEY_IMPORT_CONTENT = "backup_import_content"
        const val KEY_IMPORT_EXCLUDED = "backup_import_excluded_profiles"
        const val KEY_IMPORT_PASSWORDS = "backup_import_passwords"
        const val KEY_IMPORT_SETTINGS = "backup_import_settings"
    }
}
