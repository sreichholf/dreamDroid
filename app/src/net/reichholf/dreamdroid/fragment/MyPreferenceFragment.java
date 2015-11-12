package net.reichholf.dreamdroid.fragment;


import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;


import android.support.v7.preference.PreferenceFragmentCompat;

import com.afollestad.materialdialogs.MaterialDialog;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.PiconDownloadTask;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;

/**
 * Created by Stephan on 08.04.2015.
 */
public class MyPreferenceFragment extends PreferenceFragmentCompat implements
		SharedPreferences.OnSharedPreferenceChangeListener, PiconDownloadTask.PiconDownloadProgressListener, ActivityCallbackHandler {

	private static String LOG_TAG = MyPreferenceFragment.class.getSimpleName();

	private MaterialDialog mProgressDialog;
	private PiconDownloadTask mSyncPiconTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource

	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);
		getActivity().setTitle(R.string.settings);

		PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);
		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

		Preference syncPref = findPreference("sync_picons");
		syncPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startPiconSync();
				return true;
			}
		});
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		TypedValue typedValue = new TypedValue();
		getActivity().getTheme().resolveAttribute(android.R.attr.listSelector, typedValue, true);
/*		if(typedValue.resourceId > 0)
			getListView().setSelector(typedValue.resourceId);
*/

		boolean isDebuggable = (0 != (getActivity().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
		if(!isDebuggable) {
			Preference dev = findPreference("developer");
			if(dev != null) //Already removed?
				getPreferenceScreen().removePreference(dev);
		}
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
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.reichholf.dreamdroid.helpers.PiconDownloadTask.
	 * PiconDownloadProgressListener#updatePiconDownloadProgress(int,
	 * net.reichholf.dreamdroid.helpers.PiconDownloadTask.DownloadProgress)
	 */
	@Override
	public void updatePiconDownloadProgress(int eventid, PiconDownloadTask.DownloadProgress progress) {
		String message = "-";
		checkProgress();
		switch (eventid) {
			case PiconDownloadTask.DownloadProgress.EVENT_ID_CONNECTING:
				message = getString(R.string.connecting);
				break;
			case PiconDownloadTask.DownloadProgress.EVENT_ID_CONNECTED:
				message = getString(R.string.connected);
				break;
			case PiconDownloadTask.DownloadProgress.EVENT_ID_LOGIN_SUCCEEDED:
				message = getString(R.string.connected);
				break;
			case PiconDownloadTask.DownloadProgress.EVENT_ID_LISTING:
				message = getString(R.string.getting_list_of_files);
				break;
			case PiconDownloadTask.DownloadProgress.EVENT_ID_LISTING_READY:
				message = "";
				mProgressDialog.setMaxProgress(progress.totalFiles);
				break;
			case PiconDownloadTask.DownloadProgress.EVENT_ID_DOWNLOADING_FILE:
				message = progress.currentFile;
				break;
			case PiconDownloadTask.DownloadProgress.EVENT_ID_FINISHED:
				mProgressDialog.setCancelable(true);
				mProgressDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				Picon.clearCache();
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
		} else if (eventid == PiconDownloadTask.DownloadProgress.EVENT_ID_FINISHED) {
			Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
		}
	}

	public void startPiconSync() {
		if (mSyncPiconTask != null) {
			if (mSyncPiconTask.getStatus() != AsyncTask.Status.FINISHED)
				mSyncPiconTask.cancel(true);
		}

		mSyncPiconTask = new PiconDownloadTask(this);
		mSyncPiconTask.execute(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(
				DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, "/usr/share/enigma2/picon"), Picon.getBasepath(getActivity()));
	}

	public void checkProgress() {
		if (mProgressDialog == null || !mProgressDialog.isShowing()) {
			mProgressDialog = new MaterialDialog.Builder(getActivity())
					.title(R.string.sync_picons)
					.content(R.string.wait_request_finished)
					.cancelable(false)
					.progress(false, 1, true)
					.build();
		}

		if (!mProgressDialog.isShowing())
			mProgressDialog.show();
	}

	@Override
	public void onDrawerOpened() {
	}

	@Override
	public void onDrawerClosed() {
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}
}

