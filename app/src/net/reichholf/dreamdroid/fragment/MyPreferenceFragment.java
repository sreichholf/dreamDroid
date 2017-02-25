package net.reichholf.dreamdroid.fragment;


import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.BaseActivity;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.video.VLCPlayer;

/**
 * Created by Stephan on 08.04.2015.
 */
public class MyPreferenceFragment extends PreferenceFragmentCompat implements
		SharedPreferences.OnSharedPreferenceChangeListener, ActivityCallbackHandler, ActionDialog.DialogActionListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);
		getActivity().setTitle(R.string.settings);

		PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		Preference syncPref = findPreference("sync_picons");
		syncPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startPiconSync();
				return true;
			}
		});
		updateThemeSummary();
		updateHwAccelSummary(prefs);
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

		View header = getActivity().findViewById(R.id.content_header);
		if (header != null)
			header.setVisibility(View.GONE);
	}

	@Override
	public void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onPause() {
		PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
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
		if (DreamDroid.PREFS_KEY_THEME_TYPE.equals(key)) {
			updateThemeSummary();
			DreamDroid.setTheme((AppCompatActivity) getActivity());
		} else if (DreamDroid.PREFS_KEY_HWACCEL.equals(key))
			updateHwAccelSummary(prefs);
	}

	protected void updateThemeSummary() {
		if (getActivity() == null)
			return;
		int idx = DreamDroid.getThemeType(getActivity());
		Preference themePref = findPreference(DreamDroid.PREFS_KEY_THEME_TYPE);
		themePref.setSummary(getResources().getStringArray(R.array.theme_option_entries)[idx]);
	}

	protected void updateHwAccelSummary(SharedPreferences prefs) {
		if (getActivity() == null)
			return;
		int idx = Integer.parseInt(prefs.getString(DreamDroid.PREFS_KEY_HWACCEL, Integer.toString(VLCPlayer.MEDIA_HWACCEL_ENABLED)));
		Preference themePref = findPreference(DreamDroid.PREFS_KEY_HWACCEL);
		themePref.setSummary(getString(R.string.use_hw_accel_long, getResources().getStringArray(R.array.hw_accel_entries)[idx]));
	}

	public void startPiconSync() {
		((BaseActivity) getActivity()).startPiconSync();
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

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
	}
}

