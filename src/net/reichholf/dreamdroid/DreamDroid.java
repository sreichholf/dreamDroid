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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
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

	public static ActiveProfileChangedListener onActiveProfileChangedListener = null;

	private static boolean sFeatureSleeptimer = false;
	private static boolean sFeatureNowNext = false;

	private static Profile sProfile;
	private static SharedPreferences sSp;
	private static ArrayList<String> sLocations;
	private static ArrayList<String> sTags;

	private static EpgSearchListener sSearchListener;

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

		sSp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		sLocations = new ArrayList<String>();
		sTags = new ArrayList<String>();

		// the profile-table is initial - let's migrate the current config as
		// default Profile
		DatabaseHelper dbh = DatabaseHelper.getInstance(getBaseContext());
		ArrayList<Profile> profiles = dbh.getProfiles();
		if (profiles.isEmpty()) {
			String host = sSp.getString("host", "dm8000");
			String streamHost = sSp.getString("host", "");

			int port = Integer.valueOf(sSp.getString("port", "80"));
			String user = sSp.getString("user", "root");
			String pass = sSp.getString("pass", "dreambox");

			boolean login = sSp.getBoolean("login", false);
			boolean ssl = sSp.getBoolean("ssl", false);

			Profile p = new Profile("Default", host, streamHost, port, 8001, 80, login, user, pass, ssl, false);
			dbh.addProfile(p);
			SharedPreferences.Editor editor = sSp.edit();
			editor.remove("currentProfile");
			editor.commit();
		}

		int profileId = sSp.getInt("currentProfile", 1);
		if (setActiveProfile(getBaseContext(), profileId)) {
			showToast(getText(R.string.profile_activated) + " '" + sProfile.getName() + "'");
		} else {
			showToast(getText(R.string.profile_not_activated));
			// However we got here... we're creating an
			// "do-not-crash-default-profile now
			sProfile = new Profile("Default", "dm8000", "", 80, 8001, 80, false, "", "", false, false);
		}
	}

	public static SharedPreferences getSharedPreferences() {
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

	public static void disableNowNext() {
		sFeatureNowNext = false;
	}

	public static void enableNowNext() {
		sFeatureNowNext = true;
	}

	public static boolean featureNowNext() {
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

	public void onActiveProfileChanged(Profile p) {

	}

	public static Profile getActiveProfile() {
		return sProfile;
	}

	/**
	 * @param id
	 * @return
	 */
	public static boolean setActiveProfile(Context ctx, int id) {
		DatabaseHelper dbh = DatabaseHelper.getInstance(ctx);
		sProfile = dbh.getProfile(id);
		if (sProfile.getId() == id) {
			SharedPreferences.Editor editor = sSp.edit();
			editor.putInt("currentProfile", id);
			editor.commit();
			if (onActiveProfileChangedListener != null) {
				onActiveProfileChangedListener.onActiveProfileChanged(sProfile);
			}
			return true;
		}
		return false;
	}

	public static void setActiveProfileChangedListener(ActiveProfileChangedListener listener) {
		onActiveProfileChangedListener = listener;
	}

	/**
	 * @return
	 */
	public static boolean reloadActiveProfile(Context ctx) {
		return setActiveProfile(ctx, sProfile.getId());
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

	public static boolean search(Context context, Bundle args) {
		if (sSearchListener != null) {
			sSearchListener.onEpgSearch(args);
			return true;
		} else {
			return false;
		}
	}

	public interface EpgSearchListener {
		public void onEpgSearch(Bundle args);
	}

	public static void registerEpgSearchListener(EpgSearchListener listener) {
		sSearchListener = listener;
	}

	public static void unregisterEpgSearchListener(EpgSearchListener listener) {
		if (listener == sSearchListener)
			sSearchListener = null;
	}
}
