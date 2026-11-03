package net.reichholf.dreamdroid.ui.backup

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.reichholf.dreamdroid.data.BackupDocuments
import net.reichholf.dreamdroid.data.BackupRepository
import net.reichholf.dreamdroid.data.ImportChoice
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.carriesPasswords
import net.reichholf.dreamdroid.helpers.backup.BackupData

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

    /** What the passwords switch shows: off whenever it cannot be chosen. */
    val passwordsChecked: Boolean
        get() = includePasswords && passwordsSelectable

    val canImport: Boolean
        get() = selectedProfiles > 0 || includeSettings

    val choice: ImportChoice
        get() = ImportChoice(
            profiles = profiles.filter { it.checked }.map { it.index }.toSet(),
            passwords = passwordsChecked,
            settings = includeSettings
        )
}

/**
 * A picked backup and the choices made about it, shared by the Backup screen's import dialog
 * and the setup assistant's restore step. [review] stays null until [open] read a file, and
 * nothing is written before [confirm]. The parsed file and the choices survive process death
 * in the [SavedStateHandle].
 */
class BackupImport(
    private val handle: SavedStateHandle,
    private val backups: BackupRepository,
    private val profiles: ProfileRepository,
    private val documents: BackupDocuments
) {
    private val _review = MutableStateFlow<ImportReview?>(null)

    /** What the user takes from the file, or null when none is open. */
    val review: StateFlow<ImportReview?> = _review.asStateFlow()

    /** The parsed file behind [review]. */
    private var pending: BackupData? = null

    /** Reads the document at [uri] and asks what to take from it. False when it is unusable. */
    suspend fun open(uri: String): Boolean {
        val content = documents.read(uri, MAX_CONTENT_CHARS) ?: return false
        return review(content)
    }

    /** [open] of the file saved before process death; false when nothing is saved or parses. */
    suspend fun reopenSaved(): Boolean {
        val content = handle.get<String>(KEY_CONTENT) ?: return false
        return review(content).also { if (!it) clear() }
    }

    fun setProfileChecked(index: Int, checked: Boolean) = update { review ->
        review.copy(
            profiles = review.profiles.map {
                if (it.index == index) it.copy(checked = checked) else it
            }
        )
    }

    fun setAllProfilesChecked(checked: Boolean) = update { review ->
        review.copy(profiles = review.profiles.map { it.copy(checked = checked) })
    }

    fun setPasswords(include: Boolean) = update { review ->
        review.copy(includePasswords = include && review.passwordsAvailable)
    }

    fun setSettings(include: Boolean) = update { review ->
        review.copy(includeSettings = include && review.settingsAvailable)
    }

    /**
     * Writes what [review] chose and closes it. False when nothing was chosen, or when the
     * profiles could not be written; nothing is changed then.
     */
    suspend fun confirm(): Boolean {
        val backup = pending ?: return false
        val choice = _review.value?.takeIf { it.canImport }?.choice ?: return false
        clear()
        return backups.importBackup(backup, choice)
    }

    fun clear() {
        pending = null
        handle[KEY_CONTENT] = null
        handle.remove<IntArray>(KEY_EXCLUDED)
        handle.remove<Boolean>(KEY_PASSWORDS)
        handle.remove<Boolean>(KEY_SETTINGS)
        _review.value = null
    }

    private suspend fun review(content: String): Boolean {
        val backup = backups.parse(content)
            ?.takeUnless { it.profiles.isEmpty() && it.settings.isNullOrEmpty() }
            ?: return false
        val savedNames = profiles.profiles().mapNotNull { it.name }.toSet()
        val excluded = handle.get<IntArray>(KEY_EXCLUDED)?.toSet().orEmpty()
        val passwordsAvailable = backup.carriesPasswords
        val settingsAvailable = !backup.settings.isNullOrEmpty()
        pending = backup
        handle[KEY_CONTENT] = content
        _review.value = ImportReview(
            profiles = backup.profiles.mapIndexed { index, profile ->
                ImportProfileToggle(
                    index = index,
                    name = profile.name.orEmpty(),
                    host = profile.host.orEmpty(),
                    replaces = profile.name.orEmpty() in savedNames,
                    checked = index !in excluded
                )
            },
            passwordsAvailable = passwordsAvailable,
            includePasswords = passwordsAvailable &&
                handle.get<Boolean>(KEY_PASSWORDS) ?: true,
            settingsAvailable = settingsAvailable,
            includeSettings = settingsAvailable &&
                handle.get<Boolean>(KEY_SETTINGS) ?: true
        )
        return true
    }

    private fun update(transform: (ImportReview) -> ImportReview) {
        val next = _review.value?.let(transform) ?: return
        handle[KEY_EXCLUDED] = next.profiles.filterNot { it.checked }.map { it.index }.toIntArray()
        handle[KEY_PASSWORDS] = next.includePasswords
        handle[KEY_SETTINGS] = next.includeSettings
        _review.value = next
    }

    private companion object {
        const val KEY_CONTENT = "backup_import_content"
        const val KEY_EXCLUDED = "backup_import_excluded_profiles"
        const val KEY_PASSWORDS = "backup_import_passwords"
        const val KEY_SETTINGS = "backup_import_settings"

        /** A backup is a few KB; a larger file would not fit the saved state. */
        const val MAX_CONTENT_CHARS = 64 * 1024
    }
}
