package net.reichholf.dreamdroid.tv.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;

import net.reichholf.dreamdroid.DatabaseHelper;
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
	};

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		setPreferencesFromResource(R.xml.profile_preferences, rootKey);
		for (String key : sKeys) {
			Preference pref = findPreference(key);
			pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if (DatabaseHelper.KEY_PROFILE_SSL.equals(preference.getKey())) {
						EditTextPreference portPref = (EditTextPreference) findPreference(DatabaseHelper.KEY_PROFILE_PORT);
						String condition = "80";
						String newVal = "443";
						if (!(Boolean) newValue) {
							condition = "443";
							newVal = "80";
						}
						if (portPref.getText().equals(condition)) {
							portPref.setText(newVal);
							updateSummary(portPref, newVal);
						}
					}
					updateSummary(preference, newValue);
					return true;
				}
			});
			updateSummary(pref, null);
		}
		getActivity().setResult(Activity.RESULT_OK);
	}

	protected void updateSummary(Preference pref, Object newValue) {
		if (pref instanceof EditTextPreference && !pref.getKey().equals(DatabaseHelper.KEY_PROFILE_PASS)) {
			if (newValue == null)
				newValue = ((EditTextPreference) pref).getText();
			pref.setSummary((String) newValue);
		}
	}
}
