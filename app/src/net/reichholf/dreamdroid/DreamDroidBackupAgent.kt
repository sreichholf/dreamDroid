/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid

import android.app.backup.BackupAgentHelper
import android.app.backup.FileBackupHelper
import android.app.backup.SharedPreferencesBackupHelper
import net.reichholf.dreamdroid.helpers.backup.CloudProfilesSidecar

/**
 * Key/Value cloud backup for SharedPreferences and the profiles sidecar.
 *
 * The full Room DB is not included: EPG/movie/timer cache stays off Google Auto Backup.
 * Leftover `dreamdroid` SQLite is still imported by [DatabaseHelper.migrateIntoRoomIfNeeded]
 * when present (device-to-device transfer and old snapshots).
 */
class DreamDroidBackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        val spbh = SharedPreferencesBackupHelper(this, PREFS)
        addHelper(PREFS_BACKUP_KEY, spbh)
        val profilesHelper = FileBackupHelper(this, CloudProfilesSidecar.FILE_NAME)
        addHelper(PROFILES_BACKUP_KEY, profilesHelper)
    }

    companion object {
        const val PREFS = "net.reichholf.dreamdroid_preferences"
        const val PROFILES_BACKUP_KEY = "cloud_profiles"
        const val PREFS_BACKUP_KEY = "preferences"
    }
}
