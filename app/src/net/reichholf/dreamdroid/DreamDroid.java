/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import android.Manifest;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.Log;

import com.evernote.android.state.StateSaver;
import com.livefront.bridge.Bridge;
import com.livefront.bridge.SavedStateHandler;

import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.LocationListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TagListRequestHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * @author sre
 */
public class DreamDroid extends Application {
	private static volatile DreamDroid instance;

	public static final int INITIAL_SERVICELIST_PANE = 1;
	public static final int INITIAL_VIRTUAL_REMOTE = 2;
    public static final String PREFS_KEY_HWACCEL = "video_hardware_acceleration";
	public static final String PREFS_KEY_PICONS_ONLINE = "picons_online";

	public static String VERSION_STRING;

	public static final String ACTION_CREATE = "dreamdroid.intent.action.NEW";
	public static final String LOG_TAG = "DreamDroid";

	public static final String PREFS_KEY_QUICKZAP = "quickzap";
	public static final String PREFS_KEY_CONFIRM_APP_CLOSE = "confirm_app_close";
	public static final String PREFS_KEY_ENABLE_ANIMATIONS = "enable_animations";
	public static final String PREFS_KEY_FIRST_START = "first_start";
	public static final String PREFS_KEY_SYNC_PICONS_PATH = "sync_picons_path";
	public static final String PREFS_KEY_PICONS_ENABLED = "picons";
	public static final String PREFS_KEY_PICONS_USE_NAME = "use_name_as_picon_filename";
	public static final String PREFS_KEY_INITIALBITS = "initial_bits";
	public static final String PREFS_KEY_GRID_MAX_COLS = "grid_max_cols";
	public static final String PREFS_KEY_SIMPLE_VRM = "simple_vrm";
	public static final String PREFS_KEY_ENABLE_DEVELOPER_SETTINGS = "enable_developer";
	public static final String PREFS_KEY_FAKE_PICON = "fake_picon";
	public static final String PREFS_KEY_XML_DEBUG = "xml_debug";
	public static final String PREFS_KEY_INTEGRATED_PLAYER = "integrated_video_player";
	public static final String PREFS_KEY_THEME_TYPE = "theme_type";
	public static final String PREFS_KEY_INSTANT_ZAP = "instant_zap";
	public static final String PREFS_KEY_VIDEO_ENABLE_GESTURES = "video_enable_gestures";
	public static final String PREFS_KEY_LAST_VERSION_CODE = "last_version_code";

	public static final String IAB_PUB_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkWyCpE79iRAcqWnC+/I5AuahW/wvbGF5SxcZCELP6I6Rs47hYOydmCBDV5e11FXHZyS3BGuuVKEjf9DxkR2skNtKfgbX/UQD0jpnaEk2GnnsZ9OAaso9pKFn1ZJKtLtP7OKVlt2HpHjag3x8NGayjkno0k0gmvf5T8c77tYLtoHY+uLlUTwo0DiXhzxHjTjzTxc0nbEyRDa/5pDPudBCSien4lg+C8D9K8rdcUCI1QcLjkOgBR888CxT7cyhvUnoHcHZQLGbTFZG0XtyJnxop2AqWMiOepT3txAfq6OjOmo0PofuIk+m0jVrPLYs2eNSxmJrfZ5MddocPYD50cj+2QIDAQAB";

	public static final String SKU_DONATE_1 = "donate_1";
	public static final String SKU_DONATE_2 = "donate_2";
	public static final String SKU_DONATE_3 = "donate_3";
	public static final String SKU_DONATE_5 = "donate_5";
	public static final String SKU_DONATE_10 = "donate_10";
	public static final String SKU_DONATE_15 = "donate_15";
	public static final String SKU_DONATE_20 = "donate_20";
	public static final String SKU_DONATE_INSANE = "donate_insane";

	public static final String[] SKU_LIST = {SKU_DONATE_1, SKU_DONATE_2, SKU_DONATE_3, SKU_DONATE_5, SKU_DONATE_10, SKU_DONATE_15, SKU_DONATE_20, SKU_DONATE_INSANE};

	public static final String CURRENT_PROFILE = "currentProfile";

	public static boolean DATE_LOCALE_WO;

	private static boolean sFeatureSleeptimer = true;
	private static boolean sFeatureNowNext = true;
	private static boolean sDumpXml = false;

	private static Profile sProfile;
	private static ArrayList<String> sLocations;
	private static ArrayList<String> sTags;

	private static ProfileChangedListener sCurrentProfileChangedListener = null;

	private static boolean sFeaturePostRequest = true;

	public static Context getAppContext() {
		if (instance != null) return instance;
		else {
			try {
				instance = (DreamDroid) Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
			} catch (IllegalAccessException ignored) {}
			catch (InvocationTargetException ignored) {}
			catch (NoSuchMethodException ignored) {}
			catch (ClassNotFoundException ignored) {}
			catch (ClassCastException ignored) {}
			return instance;
		}

	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		MultiDex.install(this);
	}

	public static String getVersionString() {
		String buildDate = "<build-no-date>";
		if (BuildConfig.BUILD_TIME > 0)
			buildDate = DateTime.getYearDateTimeString(BuildConfig.BUILD_TIME / 1000);
		String abi = Build.CPU_ABI;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			abi = Build.SUPPORTED_ABIS[0];
		return String.format("dreamDroid %s\n%s-%s %s\n%s\n\n© Stephan Reichholf\nstephan@reichholf.net", BuildConfig.VERSION_NAME, BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE, abi, buildDate);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		// Determine if we require a Date-String-Locale-Missing-Fix
		// for details please see:
		// http://code.google.com/p/android/issues/detail?id=9453
		SimpleDateFormat sdf = new SimpleDateFormat("E");
		Date date = GregorianCalendar.getInstance().getTime();
		VERSION_STRING = getVersionString();

		try {
			String s = sdf.format(date);
			//noinspection ResultOfMethodCallIgnored
			Integer.parseInt(s);
			DATE_LOCALE_WO = true;
		} catch (Exception e) {
			DATE_LOCALE_WO = false;
		}
		Bridge.initialize(getApplicationContext(), new SavedStateHandler() {
			@Override
			public void saveInstanceState(@NonNull Object target, @NonNull Bundle state) {
				StateSaver.saveInstanceState(target, state);
			}

			@Override
			public void restoreInstanceState(@NonNull Object target, @Nullable Bundle state) {
				StateSaver.restoreInstanceState(target, state);
			}
		});

		initChannels();
		sLocations = new ArrayList<>();
		sTags = new ArrayList<>();

		loadCurrentProfile(this);
	}

	private void initChannels() {
		if (Build.VERSION.SDK_INT < 26) {
			return;
		}
		NotificationManager notificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationChannel channel = new NotificationChannel("dreamdroid_picon_sync",
				getString(R.string.picons),
				NotificationManager.IMPORTANCE_LOW);
		channel.setDescription(getString(R.string.sync_picons));
		notificationManager.createNotificationChannel(channel);

		channel = new NotificationChannel("dreamdroid_epg_sync",
				getString(R.string.epg),
				NotificationManager.IMPORTANCE_LOW);
		channel.setDescription(getString(R.string.epg_sync));
		notificationManager.createNotificationChannel(channel);
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
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		int profileId = sp.getInt(CURRENT_PROFILE, 1);
		if(sProfile != null && sProfile.getId() == profileId)
			return;

		DatabaseHelper dbh = DatabaseHelper.getInstance(context);
		ArrayList<Profile> profiles = dbh.getProfiles();
		// the profile-table is initial - let's migrate the current config as
		// default Profile
		if (profiles.isEmpty()) {
			String host = sp.getString("host", "dreamdroid.org");
			String streamHost = sp.getString("host", "");

			int port = Integer.valueOf(sp.getString("port", "80"));
			String user = sp.getString("user", "root");
			String pass = sp.getString("pass", "dreambox");

			boolean login = sp.getBoolean("login", false);
			boolean ssl = sp.getBoolean("ssl", false);

			Profile p = new Profile(-1, "Demo", host, streamHost, port, 8001, 80, login, user, pass, ssl, false, false,
					false, false, "", "", "", "");
			dbh.addProfile(p);
			profileId = p.getId();
			SharedPreferences.Editor editor = sp.edit();
			editor.remove(CURRENT_PROFILE);
			editor.apply();
		}

		if (!setCurrentProfile(context, profileId)) {
			// However we got here... we're creating an
			// "do-not-crash-default-profile now
			sProfile = new Profile(-1, "Demo", "dreamdroid.org", "", 80, 8001, 80, false, "", "", false, false, false, false,
					false, "", "", "", "");
		}
	}

	public static boolean setCurrentProfile(Context context, int id) {
		return setCurrentProfile(context, id, false);
	}

	public static boolean dumpXml() {
		return sDumpXml;
	}

	/**
	 * @param id
	 * @return
	 */
	public static boolean setCurrentProfile(Context context, int id, boolean forceEvent) {
		sDumpXml = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("xml_debug", false);

		Profile oldProfile = sProfile;
		if (oldProfile == null)
			oldProfile = Profile.getDefault();


		DatabaseHelper dbh = DatabaseHelper.getInstance(context);
		sProfile = dbh.getProfile(id);

		if (sProfile != null) {
			SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
			editor.putInt(CURRENT_PROFILE, id);
			editor.apply();
			if (!sProfile.equals(oldProfile) || forceEvent) {
				//reset locations and tags, they will be reloaded when needed the next time
				sLocations.clear();
				sTags.clear();
				activeProfileChanged();
			} else if (sProfile.getId() == oldProfile.getId()) {
				sProfile.setSessionId(oldProfile.getSessionId());
			}
			return true;
		} else {
			Log.w(DreamDroid.LOG_TAG, "no profile with given id [" + id + "] found");
		}
		return false;
	}

	public static void setCurrentProfile(Profile profile) {
		sProfile = profile;
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
	public static synchronized boolean loadLocations(SimpleHttpClient shc) {
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
			sLocations = new ArrayList<>();
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
	public static synchronized boolean loadTags(SimpleHttpClient shc) {
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
			sTags = new ArrayList<>();
		}

		return gotTags;
	}

	public static ArrayList<String> getTags() {
		return sTags;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
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

	public static int getThemeType(Context context) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		int type = Integer.parseInt(sp.getString("theme_type", "1"));
		return type > 2 ? 2 : type;
	}

	public static void setTheme(AppCompatActivity activity) {
		int mode;
		switch(getThemeType(activity)){
			case 0:
				mode = AppCompatDelegate.MODE_NIGHT_NO;
				break;
			case 1:
				mode = AppCompatDelegate.MODE_NIGHT_YES;
				break;
			case 2:
				mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
				break;
			default:
				mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
		}
		AppCompatDelegate.setDefaultNightMode(mode);
		activity.getDelegate().setLocalNightMode(mode);
	}

	public static boolean checkInitial(Context context, int which) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		int mask = sp.getInt(PREFS_KEY_INITIALBITS, 0);

		return (mask & which) != which;
	}

	public static void setNotInitial(Context context, int which) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		int mask = sp.getInt(PREFS_KEY_INITIALBITS, 0);
		mask |= which;

		SharedPreferences.Editor editor = sp.edit();
		editor.putInt(PREFS_KEY_INITIALBITS, mask);
		editor.apply();
	}

	public static boolean isTV(Context context) {
		return context.getResources().getBoolean(R.bool.is_television);
	}
}
