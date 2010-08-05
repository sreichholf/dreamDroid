/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import android.app.Application;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

/**
 * @author sreichholf
 * 
 */
/**
 * @author sre
 *
 */
public class DreamDroid extends Application {
	public static final String ACTION_NEW = "dreamdroid.intent.action.NEW";
	public static final String LOG_TAG = "net.reichholf.dreamdroid";

	public static SQLiteDatabase DB;
	public static SharedPreferences SP;

	public static final String KEY_ID = "_id";
	public static final String KEY_PROFILE = "profile";
	public static final String KEY_HOST = "host";
	public static final String KEY_PORT = "port";
	public static final String KEY_LOGIN = "login";
	public static final String KEY_USER = "user";
	public static final String KEY_PASS = "pass";
	public static final String KEY_SSL = "ssl";

	private static final String DATABASE_NAME = "dreamdroid";
	private static final int DATABASE_VERSION = 2;
	private static final String PROFILES_TABLE_NAME = "profiles";
	private static final String PROFILES_TABLE_CREATE =

	"CREATE TABLE IF NOT EXISTS " + PROFILES_TABLE_NAME + " (" + KEY_ID
			+ " INTEGER PRIMARY KEY, " + KEY_PROFILE + " TEXT, " + KEY_HOST
			+ " TEXT, " + KEY_PORT + " INTEGER, " + KEY_LOGIN + " BOOLEAN, "
			+ KEY_USER + " TEXT, " + KEY_PASS + " TEXT, " + KEY_SSL
			+ " BOOLEAN " + ");";

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		DB = openOrCreateDatabase(DATABASE_NAME, MODE_PRIVATE, null);

		if (DB.needUpgrade(DATABASE_VERSION)) {
			DB.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME);
		}

		// Create the Profiles-Tables if it doesn't exist
		DB.execSQL(PROFILES_TABLE_CREATE);
		DB.setVersion(DATABASE_VERSION);
	}


	/**
	 * @param p
	 */
	public static void addProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE, p.getProfile());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());

		DB.insert(PROFILES_TABLE_NAME, null, values);
	}

	/**
	 * @return Cursor for all Settings
	 */
	public static Cursor getProfiles() {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_PORT,
				KEY_LOGIN, KEY_USER, KEY_PASS, KEY_SSL };
		return DB.query(PROFILES_TABLE_NAME, columns, null, null, null, null,
				KEY_PROFILE);
	}


	/**
	 * @param p
	 */
	public static void updateProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_ID, p.getId());
		values.put(KEY_PROFILE, p.getProfile());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());

		DB.update(PROFILES_TABLE_NAME, values, KEY_ID + "=" + p.getId(), null);
	}

	/**
	 * @param p
	 */
	public static boolean deleteProfile(Profile p) {
		int numRows = DB.delete(PROFILES_TABLE_NAME, KEY_ID + "=" + p.getId(), null);
		if(numRows == 1){
			return true;
		}
		
		return false;
	}
}
