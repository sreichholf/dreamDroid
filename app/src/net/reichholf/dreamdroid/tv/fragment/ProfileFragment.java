package net.reichholf.dreamdroid.tv.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;

/**
 * Created by Stephan on 29.10.2016.
 */

public class ProfileFragment extends LeanbackPreferenceFragment {
	private static String sKeys[] = {
			DatabaseHelper.KEY_PROFILE_PROFILE,
			DatabaseHelper.KEY_PROFILE_HOST,
			DatabaseHelper.KEY_PROFILE_PORT,
			DatabaseHelper.KEY_PROFILE_SSL,
			DatabaseHelper.KEY_PROFILE_LOGIN,
			DatabaseHelper.KEY_PROFILE_USER,
			DatabaseHelper.KEY_PROFILE_PASS,
			DatabaseHelper.KEY_PROFILE_STREAM_LOGIN,
			DatabaseHelper.KEY_PROFILE_STREAM_PORT,
			DatabaseHelper.KEY_PROFILE_FILE_LOGIN,
			DatabaseHelper.KEY_PROFILE_FILE_PORT,
			DatabaseHelper.KEY_PROFILE_FILE_SSL,
	};

	@Override
	public Context getContext() {
		return getActivity();
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		setPreferencesFromResource(R.xml.profile_preferences, rootKey);
		for (String key : sKeys) {
			Preference pref = findPreference(key);
			pref.setOnPreferenceChangeListener((preference, newValue) -> {
				if (DatabaseHelper.KEY_PROFILE_SSL.equals(preference.getKey())) {
					updatePortPreference((Boolean) newValue, DatabaseHelper.KEY_PROFILE_PORT);
				} else 	if (DatabaseHelper.KEY_PROFILE_FILE_SSL.equals(preference.getKey())) {
					updatePortPreference((Boolean) newValue, DatabaseHelper.KEY_PROFILE_FILE_PORT);
				}
				updateSummary(preference, newValue);
				return true;
			});
			updateSummary(pref, null);
		}
		getActivity().setResult(Activity.RESULT_OK);
	}

	protected void updatePortPreference(boolean newValue, String preferenceKey) {
		EditTextPreference portPref = (EditTextPreference) findPreference(preferenceKey);
		String condition = "80";
		String newVal = "443";
		if (!newValue) {
			condition = "443";
			newVal = "80";
		}
		if (portPref.getText().equals(condition)) {
			portPref.setText(newVal);
			updateSummary(portPref, newVal);
		}
	}

	protected void updateSummary(Preference pref, Object newValue) {
		if (pref instanceof EditTextPreference && !pref.getKey().equals(DatabaseHelper.KEY_PROFILE_PASS)) {
			if (newValue == null)
				newValue = ((EditTextPreference) pref).getText();
			pref.setSummary((String) newValue);
		}
	}

	@Override
	public void onPause() {
		Profile p = DreamDroid.getCurrentProfile();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		p.setName(prefs.getString(DatabaseHelper.KEY_PROFILE_PROFILE, ""));
		p.setHost(prefs.getString(DatabaseHelper.KEY_PROFILE_HOST, ""));
		p.setPort(prefs.getString(DatabaseHelper.KEY_PROFILE_PORT, "80"));
		p.setSsl(prefs.getBoolean(DatabaseHelper.KEY_PROFILE_SSL, false));
		p.setLogin(prefs.getBoolean(DatabaseHelper.KEY_PROFILE_LOGIN, false));
		p.setUser(prefs.getString(DatabaseHelper.KEY_PROFILE_USER, "root"));
		p.setPass(prefs.getString(DatabaseHelper.KEY_PROFILE_PASS, "dreambox"));
		p.setStreamLogin(prefs.getBoolean(DatabaseHelper.KEY_PROFILE_STREAM_LOGIN, false));
		p.setStreamPort(prefs.getString(DatabaseHelper.KEY_PROFILE_STREAM_PORT, "8001"));
		p.setFileLogin(prefs.getBoolean(DatabaseHelper.KEY_PROFILE_FILE_LOGIN, false));
		p.setFilePort(prefs.getString(DatabaseHelper.KEY_PROFILE_FILE_PORT, "80"));
		p.setFileSsl(prefs.getBoolean(DatabaseHelper.KEY_PROFILE_FILE_SSL, false));
		DatabaseHelper dbh = DatabaseHelper.getInstance(getActivity());

		dbh.updateProfile(p);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(DreamDroid.CURRENT_PROFILE, p.getId());
		editor.commit();
		DreamDroid.setCurrentProfile(p);
		super.onPause();
	}
}
