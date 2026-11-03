/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.BackupManager
import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException

/**
 * Keeps app data out of cloud backup. The Room database and the default preferences hold
 * receiver passwords in plain text, and they were the only domains the old rules backed up,
 * so `backup_rules.xml` and `data_extraction_rules.xml` exclude every app data domain.
 * Profiles and settings move between devices through the explicit Backup & Restore screen,
 * which asks before it writes passwords.
 *
 * The agent declares no helpers, so it backs up nothing. A key-value backup written by an
 * older version is discarded on restore, and [onBackup] writes delete markers for the entities
 * that version stored, so the next backup run drops them from the transport.
 */
class DreamDroidBackupAgent : BackupAgentHelper() {
    /**
     * Deletes the entities the old agent wrote. A key-value backup is incremental: an entity
     * the agent does not mention stays in the transport's data set, so a run that writes
     * nothing would leave the old passwords in place. The agent has no helpers, so the delete
     * markers are all it writes.
     */
    @Throws(IOException::class)
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) {
        deleteLegacyKeyValueEntities { key, dataSize -> data.writeEntityHeader(key, dataSize) }
    }

    /**
     * Drops a key-value backup written by an older version. The agent has no helpers, so no
     * entity would be written anyway; reading them here keeps it that way if one is added.
     */
    @Throws(IOException::class)
    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor
    ) {
        while (data.readNextHeader()) {
            data.skipEntityData()
        }
    }

    /**
     * Discards a full-backup file instead of writing it. The agent is key-value only, so the
     * system does not call this today; it stays as a guard in case the app ever opts into
     * full-data backup, where a restore would otherwise write the excluded files back.
     */
    @Throws(IOException::class)
    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(data).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (input.read(buffer) != -1) {
                // Drop the content.
            }
        }
    }

    companion object {
        private const val REPLACEMENT_MARKER = "backup_replacement_version"

        /** A negative data size in [BackupDataOutput.writeEntityHeader] deletes the entity. */
        private const val DELETE_ENTITY = -1

        /**
         * Entity keys 2.0.470 wrote: [BackupAgentHelper] prefixes the helper key with a colon,
         * [android.app.backup.FileBackupHelper] uses the path it was constructed with and
         * [android.app.backup.SharedPreferencesBackupHelper] the preferences name. 1.15 wrote
         * the same preferences entity and `dreamdroid`.
         */
        private val LEGACY_KEY_VALUE_ENTITY_KEYS = listOf(
            "preferences:net.reichholf.dreamdroid_preferences",
            "database:../databases/dreambox",
            "database:../databases/dreamdroid"
        )

        /**
         * Writes a delete marker per entity through [writeHeader], so a JVM test can pin the
         * keys: [BackupDataOutput] has no public constructor to fake.
         */
        internal fun deleteLegacyKeyValueEntities(
            writeHeader: (key: String, dataSize: Int) -> Unit
        ) {
            for (key in LEGACY_KEY_VALUE_ENTITY_KEYS) {
                writeHeader(key, DELETE_ENTITY)
            }
        }

        /**
         * Requests a backup run after an update, so the delete markers `onBackup` writes reach
         * the transport and drop the entities 2.0.470 stored. Best effort, once per version:
         * the app cannot delete the copy on Google's servers itself, and the run happens only
         * while backup is enabled.
         */
        fun replaceOldCloudBackup(context: Context) {
            val marker = File(context.noBackupFilesDir, REPLACEMENT_MARKER)
            val version = BuildConfig.VERSION_CODE.toString()
            if (runCatching { marker.readText() }.getOrNull() == version) {
                return
            }
            BackupManager(context).dataChanged()
            runCatching { marker.writeText(version) }
        }
    }
}
