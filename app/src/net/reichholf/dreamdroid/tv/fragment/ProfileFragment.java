package net.reichholf.dreamdroid.tv.fragment;

import android.os.Bundle;
import android.provider.ContactsContract;
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
		for(String key : sKeys ) {
			final Preference pref = findPreference(key);
			pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if(pref instanceof EditTextPreference)
						pref.setSummary((String) newValue);
					return true;
				}
			});
			if(pref instanceof EditTextPreference && !pref.getKey().equals(DatabaseHelper.KEY_PROFILE_PASS))
				pref.setSummary(((EditTextPreference)pref).getText());
		}
	}


}
