/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Location;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import android.app.Application;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * @author sre
 * 
 */
public class DreamDroid extends Application {
	public static final String ACTION_NEW = "dreamdroid.intent.action.NEW";
	public static final String LOG_TAG = "net.reichholf.dreamdroid";

	public static SQLiteDatabase DB;
	public static SharedPreferences SP;
	public static Profile PROFILE;
	public static ArrayList<String> LOCATIONS;
	public static ArrayList<String> TAGS;

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
		LOCATIONS = new ArrayList<String>();
		TAGS = new ArrayList<String>();
		
		DB = openOrCreateDatabase(DATABASE_NAME, MODE_PRIVATE, null);

		if (DB.needUpgrade(DATABASE_VERSION)) {
			DB.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME);
		}

		// Create the Profiles-Tables if it doesn't exist
		DB.execSQL(PROFILES_TABLE_CREATE);
		DB.setVersion(DATABASE_VERSION);

		// the profile-table is initial - let's migrate the current config as
		// default Profile
		Cursor c = getProfiles();
		if (c.getCount() == 0) {
			String host = DreamDroid.SP.getString("host", "dm8000");
			int port = new Integer(DreamDroid.SP.getString("port", "80"));
			String user = DreamDroid.SP.getString("user", "root");
			String pass = DreamDroid.SP.getString("pass", "dreambox");

			boolean login = DreamDroid.SP.getBoolean("login", false);
			boolean ssl = DreamDroid.SP.getBoolean("ssl", false);

			String profile = "Default";
			Profile p = new Profile(profile, host, port, login, user, pass, ssl);
			DreamDroid.addProfile(p);
		}

		int profileId = SP.getInt("currentProfile", 1);
		if (setActiveProfile(profileId)) {
			showToast(getText(R.string.profile_activated) + " '"
					+ PROFILE.getProfile() + "'");
		} else {
			showToast(getText(R.string.profile_not_activated) + " '"
					+ PROFILE.getProfile() + "'");
		}
	}

	/**
	 * @param text
	 *            Toast text
	 */
	protected void showToast(String text) {
		Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param p
	 */
	public static boolean addProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE, p.getProfile());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());

		if (DB.insert(PROFILES_TABLE_NAME, null, values) > -1) {
			return true;
		}

		return false;
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

	public static Cursor getProfile(int id) {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_PORT,
				KEY_LOGIN, KEY_USER, KEY_PASS, KEY_SSL };
		return DB.query(PROFILES_TABLE_NAME, columns, KEY_ID + "=" + id, null,
				null, null, KEY_PROFILE);
	}

	/**
	 * @param p
	 */
	public static boolean updateProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE, p.getProfile());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());

		int numRows = DB.update(PROFILES_TABLE_NAME, values,
				KEY_ID + "=" + p.getId(), null);

		if (numRows == 1) {
			return true;
		}

		return false;

	}

	/**
	 * @param p
	 */
	public static boolean deleteProfile(Profile p) {
		int numRows = DB.delete(PROFILES_TABLE_NAME, KEY_ID + "=" + p.getId(),
				null);
		if (numRows == 1) {
			return true;
		}

		return false;
	}

	/**
	 * @param id
	 * @return
	 */
	public static boolean setActiveProfile(int id) {
		Cursor c = getProfile(id);
		if (c.getCount() == 1) {
			c.moveToFirst();
			PROFILE = new Profile(c);
			SharedPreferences.Editor editor = SP.edit();
			editor.putInt("currentProfile", id);
			editor.commit();

			return true;
		}
		return false;
	}
	
	public static boolean reloadActiveProfile(){		
		return setActiveProfile(PROFILE.getId());
	}
	
	/**
	 * @param shc
	 */
	public static boolean loadLocations(SimpleHttpClient shc) {
		LOCATIONS.clear();

		boolean gotLoc = false;
		String xmlLoc = Location.getList(shc);

		if (xmlLoc != null) {
			if (Location.parseList(xmlLoc, LOCATIONS)) {
				gotLoc = true;
			}
		}

		if (!gotLoc) {
			Log.e(LOG_TAG,
					"Error parsing locations, falling back to /hdd/movie");
			LOCATIONS = new ArrayList<String>();
			LOCATIONS.add("/hdd/movie");
		}
		
		return gotLoc;

	}
	
	/**
	 * @param shc
	 */
	public static boolean loadTags(SimpleHttpClient shc) {
		TAGS.clear();

		boolean gotTags = false;
		String xmlLoc = Tag.getList(shc);

		if (xmlLoc != null) {
			if (Tag.parseList(xmlLoc, TAGS)) {
				gotTags = true;
			}
		}

		if (!gotTags) {
			Log.e(LOG_TAG,
					"Error parsing Tags, no more Tags will be available");
			TAGS = new ArrayList<String>();
		}
		
		return gotTags;

	}
}
