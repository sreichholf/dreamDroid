/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
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
 * The agent stays declared with no helpers, so a key-value backup written by an older
 * version is not restored either.
 */
class DreamDroidBackupAgent : BackupAgentHelper() {
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
     * Drops a full-backup file instead of writing it. A snapshot from 2.0.470 holds the
     * preferences and databases; the current exclusion rules only keep them out of new
     * snapshots, so the restore side has to discard the old files.
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

        /**
         * Requests one key-value backup after an update, so an empty snapshot replaces the
         * one 2.0.470 wrote with the preferences and databases. Best effort, once per
         * version: the app cannot delete the copy on Google's servers, it can only make the
         * system replace it at the next backup run, and only while backup is enabled.
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
