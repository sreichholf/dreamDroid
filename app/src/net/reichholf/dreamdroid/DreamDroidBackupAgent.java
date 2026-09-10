/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;

import net.reichholf.dreamdroid.room.AppDatabase;

/**
 * Cloud backup for SharedPreferences + profile DBs.
 * Backs up Room {@link AppDatabase#DATABASE_NAME} and legacy {@link DatabaseHelper#DATABASE_NAME}
 * so pre-cutover cloud snapshots (legacy file only) still restore; {@link DreamDroid} migrates
 * legacy → Room on first launch after restore.
 */
public class DreamDroidBackupAgent extends BackupAgentHelper {
	public static final String PREFS = "net.reichholf.dreamdroid_preferences";
	
	public static final String DATABASE_BACKUP_KEY = "database";
	public static final String PREFS_BACKUP_KEY ="preferences";
	public void onCreate(){
		SharedPreferencesBackupHelper spbh = new SharedPreferencesBackupHelper(this, PREFS);
		addHelper(PREFS_BACKUP_KEY, spbh);
		FileBackupHelper dbfbh = new FileBackupHelper(this,
				"../databases/" + AppDatabase.DATABASE_NAME,
				"../databases/" + DatabaseHelper.DATABASE_NAME);
		addHelper(DATABASE_BACKUP_KEY, dbfbh);
	}
}
