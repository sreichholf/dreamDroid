/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.LocationListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TagListRequestHandler;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author sre
 * 
 */
public class DreamDroid extends Application {
	public static String VERSION_STRING;

	public static final String ACTION_CREATE = "dreamdroid.intent.action.NEW";
	public static final String LOG_TAG = "net.reichholf.dreamdroid";

	public static final String PREFS_KEY_QUICKZAP = "quickzap";
	public static final String PREFS_KEY_DEFAULT_BOUQUET_REF = "default_bouquet_ref";
	public static final String PREFS_KEY_DEFAULT_BOUQUET_NAME = "default_bouquet_name";
	public static final String PREFS_KEY_DEFAULT_BOUQUET_IS_LIST = "default_bouquet_is_bouquet_list";
	public static final String PREF_KEY_CONFIRM_APP_CLOSE = "confirm_app_close";
	public static final String PREF_KEY_ENABLE_ANIMATIONS = "enable_animations";
	public static final String PREF_KEY_FIRST_START = "first_start";

	public static boolean DATE_LOCALE_WO;

	private static boolean sFeatureSleeptimer = false;
	private static boolean sFeatureNowNext = false;

	private static Profile sProfile;
	private static ArrayList<String> sLocations;
	private static ArrayList<String> sTags;

	private static ProfileChangedListener sCurrentProfileChangedListener = null;

	private static boolean sFeaturePostRequest = true;

	/**
	 * @param context
	 * @return
	 */
	public static String getVersionString(Context context) {
		try {
			ComponentName comp = new ComponentName(context, context.getClass());
			PackageInfo pinfo = context.getPackageManager().getPackageInfo(comp.getPackageName(), 0);
			return String.format("dreamDroid %s (%s)\n© Stephan Reichholf\nstephan@reichholf.net", pinfo.versionName, pinfo.versionCode);
		} catch (android.content.pm.PackageManager.NameNotFoundException e) {
			return "dreamDroid\n© 2013 Stephan Reichholf\nstephan@reichholf.net";
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

		sLocations = new ArrayList<String>();
		sTags = new ArrayList<String>();
		loadCurrentProfile(this);
	}

	public static void disableNowNext() {
		sFeatureNowNext = false;
	}

	public static void enableNowNext() {
		sFeatureNowNext = true;
	}

	public static boolean featureNowNext() {
		return sFeatureNowNext;
	}

	public static boolean featurePostRequest() {
		return sFeaturePostRequest;
	}

	public static void setFeaturePostRequest(boolean enabled) {
		sFeaturePostRequest = enabled;
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

	public static Profile getCurrentProfile() {
		return sProfile;
	}

	public static void loadCurrentProfile(Context context) {
		// the profile-table is initial - let's migrate the current config as
		// default Profile
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		DatabaseHelper dbh = DatabaseHelper.getInstance(context);
		ArrayList<Profile> profiles = dbh.getProfiles();
		if (profiles.isEmpty()) {
			String host = sp.getString("host", "dm8000");
			String streamHost = sp.getString("host", "");

			int port = Integer.valueOf(sp.getString("port", "80"));
			String user = sp.getString("user", "root");
			String pass = sp.getString("pass", "dreambox");

			boolean login = sp.getBoolean("login", false);
			boolean ssl = sp.getBoolean("ssl", false);

			Profile p = new Profile("Default", host, streamHost, port, 8001, 80, login, user, pass, ssl, false, false,
					false, false);
			dbh.addProfile(p);
			SharedPreferences.Editor editor = sp.edit();
			editor.remove("currentProfile");
			editor.commit();
		}

		int profileId = sp.getInt("currentProfile", 1);
		if (!setCurrentProfile(context, profileId)) {
			// However we got here... we're creating an
			// "do-not-crash-default-profile now
			sProfile = new Profile("Default", "dm8000", "", 80, 8001, 80, false, "", "", false, false, false, false,
					false);
		}
	}

	public static boolean setCurrentProfile(Context context, int id) {
		return setCurrentProfile(context, id, false);
	}

	/**
	 * @param id
	 * @return
	 */
	public static boolean setCurrentProfile(Context context, int id, boolean forceEvent) {
		Profile oldProfile = sProfile;
		if (oldProfile == null)
			oldProfile = new Profile();

		DatabaseHelper dbh = DatabaseHelper.getInstance(context);
		sProfile = dbh.getProfile(id);
		if (sProfile.getId() == id) {
			SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
			editor.putInt("currentProfile", id);
			editor.commit();
			if (!sProfile.equals(oldProfile) || forceEvent) {
				activeProfileChanged();
			}
			return true;
		}
		return false;
	}

	public static void profileChanged(Context context, Profile p) {
		if (p.getId() == sProfile.getId()) {
			reloadCurrentProfile(context);
		}
	}

	private static void activeProfileChanged() {
		if (sCurrentProfileChangedListener != null) {
			sCurrentProfileChangedListener.onProfileChanged(sProfile);
		}
	}

	public static void setCurrentProfileChangedListener(ProfileChangedListener listener) {
		sCurrentProfileChangedListener = listener;
	}

	/**
	 * @return
	 */
	public static boolean reloadCurrentProfile(Context ctx) {
		return setCurrentProfile(ctx, sProfile.getId(), true);
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

	public static ArrayList<String> getLocations() {
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

	public static ArrayList<String> getTags() {
		return sTags;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void scheduleBackup(Context context) {
		Log.d(LOG_TAG, "Scheduling backup");
		try {
			Class managerClass = Class.forName("android.app.backup.BackupManager");
			Constructor managerConstructor = managerClass.getConstructor(Context.class);
			Object manager = managerConstructor.newInstance(context);
			Method m = managerClass.getMethod("dataChanged");
			m.invoke(manager);
			Log.d(LOG_TAG, "Backup requested");
		} catch (ClassNotFoundException e) {
			Log.d(LOG_TAG, "No backup manager found");
		} catch (Throwable t) {
			Log.d(LOG_TAG, "Scheduling backup failed " + t);
			t.printStackTrace();
		}
	}
	
	public static boolean isLightTheme(Context context) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		return sp.getBoolean("light_theme", true);
	}
	
	public static void setTheme(Context context) {
		if (!isLightTheme(context))
			context.setTheme(R.style.Theme_DreamDroid);
	}
}
