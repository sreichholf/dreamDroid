/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.LocationListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TagListRequestHandler;
import android.app.Application;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
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
	public static String VERSION_STRING;
	public static final String ACTION_NEW = "dreamdroid.intent.action.NEW";
	public static final String LOG_TAG = "net.reichholf.dreamdroid";

	public static final String PREFS_KEY_QUICKZAP = "quickzap";
	public static final String PREFS_KEY_DEFAULT_BOUQUET_REF = "default_bouquet_ref";
	public static final String PREFS_KEY_DEFAULT_BOUQUET_NAME = "default_bouquet_name";
	public static final String PREFS_KEY_DEFAULT_BOUQUET_IS_LIST = "default_bouquet_is_bouquet_list";

	public static boolean DATE_LOCALE_WO;

	public static final String KEY_ID = "_id";
	public static final String KEY_PROFILE = "profile";
	public static final String KEY_HOST = "host";
	public static final String KEY_STREAM_HOST = "streamhost";
	public static final String KEY_PORT = "port";
	public static final String KEY_LOGIN = "login";
	public static final String KEY_USER = "user";
	public static final String KEY_PASS = "pass";
	public static final String KEY_SSL = "ssl";
	public static final String KEY_SIMPLE_REMOTE = "simpleremote";

	private static final String DATABASE_NAME = "dreamdroid";
	private static final int DATABASE_VERSION = 4;
	private static final String PROFILES_TABLE_NAME = "profiles";

	private static final String PROFILES_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + PROFILES_TABLE_NAME + " ("
			+ KEY_ID + " INTEGER PRIMARY KEY, " + KEY_PROFILE + " TEXT, " + KEY_HOST + " TEXT, " + KEY_STREAM_HOST
			+ " TEXT, " + KEY_PORT + " INTEGER, " + KEY_LOGIN + " BOOLEAN, " + KEY_USER + " TEXT, " + KEY_PASS
			+ " TEXT, " + KEY_SSL + " BOOLEAN, " + KEY_SIMPLE_REMOTE + " BOOLEAN );";

	private static final String PROFILES_TABLE_UPGRADE_2_3 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_STREAM_HOST + " TEXT;";

	private static final String PROFILES_TABLE_UPGRADE_3_4 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_SIMPLE_REMOTE + " BOOLEAN;";

	public static OnActiveProfileChangedListener onActiveProfileChangedListener = null;
	
	private static boolean sFeatureSleeptimer = false;
	private static boolean sFeatureNowNext = false;
	private static Profile sProfile;
	private static SharedPreferences sSp;
	private static SQLiteDatabase sDb;
	private static ArrayList<String> sLocations;
	private static ArrayList<String> sTags;
	

	/**
	 * @param context
	 * @return
	 */
	public static String getVersionString(Context context) {
		try {
			ComponentName comp = new ComponentName(context, context.getClass());
			PackageInfo pinfo = context.getPackageManager().getPackageInfo(comp.getPackageName(), 0);
			return "dreamDroid " + pinfo.versionName + "\n© Stephan Reichholf\nstephan@reichholf.net";
		} catch (android.content.pm.PackageManager.NameNotFoundException e) {
			return "dreamDroid\n© 2010 Stephan Reichholf\nstephan@reichholf.net";
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		// Determine if we require a Date-String-Locale-Missing-Fix
		// for details please see:
		// http://code.google.com/p/android/issues/detail?id=9453
		SimpleDateFormat sdf = new SimpleDateFormat("E");
		Date date = GregorianCalendar.getInstance().getTime();
		VERSION_STRING = getVersionString(this);

		try {
			String s = sdf.format(date);
			Integer.parseInt(s);
			DATE_LOCALE_WO = true;
		} catch (Exception e) {
			DATE_LOCALE_WO = false;
		}

		sSp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		sLocations = new ArrayList<String>();
		sTags = new ArrayList<String>();

		sDb = openOrCreateDatabase(DATABASE_NAME, MODE_PRIVATE, null);

		if (sDb.needUpgrade(DATABASE_VERSION)) {
			if (sDb.getVersion() == 2) {
				sDb.execSQL(PROFILES_TABLE_UPGRADE_2_3);
				sDb.execSQL(PROFILES_TABLE_UPGRADE_3_4);
				sDb.setVersion(DATABASE_VERSION);
			} else if (sDb.getVersion() == 3) {
				sDb.execSQL(PROFILES_TABLE_UPGRADE_3_4);
				sDb.setVersion(DATABASE_VERSION);
			} else {
				sDb.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME);
				sDb.setVersion(0);
			}
		}

		if (sDb.getVersion() != DATABASE_VERSION) {
			// Create the Profiles-Tables if it doesn't exist
			sDb.execSQL(PROFILES_TABLE_CREATE);
			sDb.setVersion(DATABASE_VERSION);
		}

		// the profile-table is initial - let's migrate the current config as
		// default Profile
		Cursor c = getProfiles();
		if (c.getCount() == 0) {
			String host =sSp.getString("host", "dm8000");
			String streamHost = sSp.getString("host", "");

			int port = Integer.valueOf(sSp.getString("port", "80"));
			String user = sSp.getString("user", "root");
			String pass = sSp.getString("pass", "dreambox");

			boolean login = sSp.getBoolean("login", false);
			boolean ssl = sSp.getBoolean("ssl", false);
			
			Profile p = new Profile("Default", host, streamHost, port, login, user, pass, ssl, false);
			DreamDroid.addProfile(p);
			SharedPreferences.Editor editor = sSp.edit();
			editor.remove("currentProfile");
			editor.commit();
		}
		c.close();

		int profileId = sSp.getInt("currentProfile", 1);
		if (setActiveProfile(profileId)) {
			showToast(getText(R.string.profile_activated) + " '" + sProfile.getName() + "'");
		} else {
			showToast(getText(R.string.profile_not_activated));
			// However we got here... we're creating an
			// "do-not-crash-default-profile now
			sProfile = new Profile("Default", "dm8000", "", 80, false, "", "", false, false);
		}
	}
	
	public static SharedPreferences getSharedPreferences(){
		return sSp;
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
	 * @param text
	 */
	protected void showToast(CharSequence text) {
		Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param p
	 */
	public static boolean addProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE, p.getName());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_STREAM_HOST, p.getStreamHostValue());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());
		values.put(KEY_SIMPLE_REMOTE, p.isSimpleRemote());

		if (sDb.insert(PROFILES_TABLE_NAME, null, values) > -1) {
			return true;
		}

		return false;
	}
	
	public static void disableNowNext(){
		sFeatureNowNext = false;
	}
	
	public static void enableNowNext(){
		sFeatureNowNext = true;
	}
	
	public static boolean featureNowNext(){
		return sFeatureNowNext;
	}
		
	public static void disableSleepTimer() {
		sFeatureSleeptimer = false;
	}

	public static void enableSleepTimer() {
		sFeatureSleeptimer = true;
	}

	public static boolean featureSleepTimer() {
		return sFeatureSleeptimer;
	}

	/**
	 * @return Cursor for all Settings
	 */
	public static Cursor getProfiles() {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_STREAM_HOST, KEY_PORT, KEY_LOGIN, KEY_USER, KEY_PASS,
				KEY_SSL, KEY_SIMPLE_REMOTE };
		return sDb.query(PROFILES_TABLE_NAME, columns, null, null, null, null, KEY_PROFILE);
	}

	public static Cursor getProfile(int id) {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_STREAM_HOST, KEY_PORT, KEY_LOGIN, KEY_USER, KEY_PASS,
				KEY_SSL, KEY_SIMPLE_REMOTE };
		return sDb.query(PROFILES_TABLE_NAME, columns, KEY_ID + "=" + id, null, null, null, KEY_PROFILE);
	}

	/**
	 * @param p
	 */
	public static boolean updateProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE, p.getName());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_STREAM_HOST, p.getStreamHostValue());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());
		values.put(KEY_SIMPLE_REMOTE, p.isSimpleRemote());

		int numRows = sDb.update(PROFILES_TABLE_NAME, values, KEY_ID + "=" + p.getId(), null);

		if (numRows == 1) {
			return true;
		}

		return false;

	}
	
	public void onActiveProfileChanged(Profile p){
		
	}

	/**
	 * @param p
	 */
	public static boolean deleteProfile(Profile p) {
		int numRows = sDb.delete(PROFILES_TABLE_NAME, KEY_ID + "=" + p.getId(), null);
		if (numRows == 1) {
			return true;
		}

		return false;
	}
	
	public static Profile getActiveProfile(){
		return sProfile;
	}
	
	/**
	 * @param id
	 * @return
	 */
	public static boolean setActiveProfile(int id) {
		Cursor c = getProfile(id);
		if (c.getCount() == 1) {
			c.moveToFirst();
			sProfile = new Profile(c);
			SharedPreferences.Editor editor = sSp.edit();
			editor.putInt("currentProfile", id);
			editor.commit();
			c.close();
			if(onActiveProfileChangedListener != null){
				onActiveProfileChangedListener.onActiveProfileChanged(sProfile);
			}
			
			return true;
		}
		c.close();
		return false;
	}
	
	public static void setActiveProfileChangedListener(OnActiveProfileChangedListener listener){
		onActiveProfileChangedListener = listener;
	}

	/**
	 * @return
	 */
	public static boolean reloadActiveProfile() {
		return setActiveProfile(sProfile.getId());
	}

	/**
	 * @return
	 */
	public static void checkInterfaceVersion() {

	}

	/**
	 * @param shc
	 */
	public static boolean loadLocations(SimpleHttpClient shc) {
		sLocations.clear();

		boolean gotLoc = false;
		LocationListRequestHandler handler = new LocationListRequestHandler();
		String xml = handler.getList(shc);

		if (xml != null) {
			if (handler.parseList(xml, sLocations)) {
				gotLoc = true;
			}
		}

		if (!gotLoc) {
			Log.e(LOG_TAG, "Error parsing locations, falling back to /hdd/movie");
			sLocations = new ArrayList<String>();
			sLocations.add("/hdd/movie");
		}

		return gotLoc;
	}
	
	public static ArrayList<String> getLocations(){
		return sLocations;
	}

	/**
	 * @param shc
	 */
	public static boolean loadTags(SimpleHttpClient shc) {
		sTags.clear();
		boolean gotTags = false;

		TagListRequestHandler handler = new TagListRequestHandler();

		String xmlLoc = handler.getList(shc);

		if (xmlLoc != null) {
			if (handler.parseList(xmlLoc, sTags)) {
				gotTags = true;
			}
		}

		if (!gotTags) {
			Log.e(LOG_TAG, "Error parsing Tags, no more Tags will be available");
			sTags = new ArrayList<String>();
		}

		return gotTags;
	}
	
	public static ArrayList<String> getTags(){
		return sTags;
	}
}
