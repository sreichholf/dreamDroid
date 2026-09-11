/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid

import android.app.backup.BackupAgentHelper
import android.app.backup.FileBackupHelper
import android.app.backup.SharedPreferencesBackupHelper
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Cloud backup for SharedPreferences + profile DBs.
 * Backs up Room [AppDatabase.DATABASE_NAME] and legacy [DatabaseHelper.DATABASE_NAME]
 * so pre-cutover cloud snapshots (legacy file only) still restore; [DreamDroid] migrates
 * legacy → Room on first launch after restore.
 */
class DreamDroidBackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        val spbh = SharedPreferencesBackupHelper(this, PREFS)
        addHelper(PREFS_BACKUP_KEY, spbh)
        val dbfbh = FileBackupHelper(
            this,
            "../databases/" + AppDatabase.DATABASE_NAME,
            "../databases/" + DatabaseHelper.DATABASE_NAME,
        )
        addHelper(DATABASE_BACKUP_KEY, dbfbh)
    }

    companion object {
        const val PREFS = "net.reichholf.dreamdroid_preferences"
        const val DATABASE_BACKUP_KEY = "database"
        const val PREFS_BACKUP_KEY = "preferences"
    }
}
