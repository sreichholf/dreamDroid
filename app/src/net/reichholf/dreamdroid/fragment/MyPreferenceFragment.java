package net.reichholf.dreamdroid.fragment;


import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.PiconSyncService;

/**
 * Created by Stephan on 08.04.2015.
 */
public class MyPreferenceFragment extends PreferenceFragmentCompat implements
		SharedPreferences.OnSharedPreferenceChangeListener, ActivityCallbackHandler {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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

		boolean isDebuggable = (0 != (getActivity().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
		if (!isDebuggable) {
			Preference dev = findPreference("developer");
			if (dev != null) //Already removed?
				getPreferenceScreen().removePreference(dev);
		}
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setFabEnabled(R.id.fab_reload, false);
		setFabEnabled(R.id.fab_main, false);
	}

	protected void setFabEnabled(int id, boolean enabled) {
		FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(id);
		if (fab == null)
			return;
		fab.setTag(R.id.fab_scrolling_view_behavior_enabled, enabled);
		if (enabled)
			fab.show();
		else
			fab.hide();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.w(DreamDroid.LOG_TAG, key);
	}


	public void startPiconSync() {
		if (isSyncServiceRunning()) {
			Toast.makeText(getActivity(), R.string.picon_sync_running, Toast.LENGTH_LONG).show();
			return;
		}
		Intent piconSyncItent = new Intent(getActivity(), PiconSyncService.class);
		getActivity().startService(piconSyncItent);
		Toast.makeText(getActivity(), R.string.picon_sync_started, Toast.LENGTH_LONG).show();
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

	private boolean isSyncServiceRunning() {
		ActivityManager manager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (PiconSyncService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}
}

