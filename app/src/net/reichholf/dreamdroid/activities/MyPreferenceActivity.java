/*
 * Copyright © 2013. Stephan Reichholf
 *
 * Unless stated otherwise in a files head all java and xml-code of this Project is:
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 *
 * All grahpics, except the dreamdroid icon, can be used for any other non-commercial purposes.
 * The dreamdroid icon may not be used in any other projects than dreamdroid itself.
 */

package net.reichholf.dreamdroid.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.ProfileListFragment;
import net.reichholf.dreamdroid.helpers.PiconDownloadTask;
import net.reichholf.dreamdroid.helpers.PiconDownloadTask.DownloadProgress;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;

/**
 * @author sreichholf
 */
public class MyPreferenceActivity extends PreferenceActivity implements
		SharedPreferences.OnSharedPreferenceChangeListener, PiconDownloadTask.PiconDownloadProgressListener {

	private static final String LOG_TAG = "MyPreferenceActivity";

	private ProgressDialog mProgressDialog;
	private PiconDownloadTask mSyncPiconTask;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.preference.MyPreferenceActivity#onCreate(android.os.Bundle)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (Build.VERSION.SDK_INT >= 21)
			DreamDroid.setTheme(this);
		int currentOrientation = getResources().getConfiguration().orientation;
		setRequestedOrientation(currentOrientation);

		super.onCreate(savedInstanceState);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true))
			overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale);
		addPreferencesFromResource(R.xml.preferences);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

		Preference syncPref = findPreference("sync_picons");
		syncPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startPiconSync();
				return true;
			}
		});

		Preference profilesPref = findPreference("profiles");
		profilesPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				openProfileConfig();
				return true;
			}

			;
		});
	}

	@Override
	public void onPause() {
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}
		if (mSyncPiconTask != null) {
			mSyncPiconTask.cancel(true);
			mSyncPiconTask = null;
		}
		super.onPause();
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true))
			overridePendingTransition(R.anim.activity_open_scale, R.anim.activity_close_translate);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#
	 * onSharedPreferenceChanged(android.content.SharedPreferences,
	 * java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.w(DreamDroid.LOG_TAG, key);
		if ("light_theme".equals(key)) {
			setResult(Statics.RESULT_THEME_CHANGED);
		}
	}

	public void openProfileConfig() {
		Intent i = new Intent(MyPreferenceActivity.this, SimpleFragmentActivity.class);
		i.putExtra("fragmentClass", ProfileListFragment.class);
		startActivity(i);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.helpers.PiconDownloadTask.
	 * PiconDownloadProgressListener#updatePiconDownloadProgress(int,
	 * net.reichholf.dreamdroid.helpers.PiconDownloadTask.DownloadProgress)
	 */
	@Override
	public void updatePiconDownloadProgress(int eventid, DownloadProgress progress) {
		String message = "-";
		checkProgress();
		switch (eventid) {
			case DownloadProgress.EVENT_ID_CONNECTING:
				message = getString(R.string.connecting);
				break;
			case DownloadProgress.EVENT_ID_CONNECTED:
				message = getString(R.string.connected);
				break;
			case DownloadProgress.EVENT_ID_LOGIN_SUCCEEDED:
				message = getString(R.string.connected);
				break;
			case DownloadProgress.EVENT_ID_LISTING:
				message = getString(R.string.getting_list_of_files);
				break;
			case DownloadProgress.EVENT_ID_LISTING_READY:
				message = "";
				mProgressDialog.setMax(progress.totalFiles);
				break;
			case DownloadProgress.EVENT_ID_DOWNLOADING_FILE:
				message = progress.currentFile;
				break;
			case DownloadProgress.EVENT_ID_FINISHED:
				mProgressDialog.setCancelable(true);
				mProgressDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				if (!progress.error) {
					message = getString(R.string.picon_sync_finished, progress.downloadedFiles);
				} else {
					message = progress.errorText;
					if (message == null) //TODO I am not happy about this, imo this shouldn't even happen!
						message = progress.currentFile;
				}
				break;
		}

		if (message == null || "".equals(message.trim())) //TODO I am not happy about this, imo this shouldn't even happen!
			message = "-";
		Log.i(LOG_TAG, message);
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.setMessage(message);
			mProgressDialog.setProgress(progress.downloadedFiles);
		} else if (eventid == DownloadProgress.EVENT_ID_FINISHED) {
			Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
		}
	}

	public void startPiconSync() {
		if (mSyncPiconTask != null) {
			if (mSyncPiconTask.getStatus() != AsyncTask.Status.FINISHED)
				mSyncPiconTask.cancel(true);
		}

		mSyncPiconTask = new PiconDownloadTask(this);
		mSyncPiconTask.execute(PreferenceManager.getDefaultSharedPreferences(this).getString(
				DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, "/usr/share/enigma2/picon"), Picon.getBasepath(this));
	}

	public void checkProgress() {
		if (mProgressDialog == null || !mProgressDialog.isShowing()) {
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
					WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setCancelable(false);
			mProgressDialog.setMax(1);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setTitle(getString(R.string.sync_picons));
			mProgressDialog.setMessage(getString(R.string.wait_request_finished));
		}

		if (!mProgressDialog.isShowing())
			mProgressDialog.show();
	}
}
