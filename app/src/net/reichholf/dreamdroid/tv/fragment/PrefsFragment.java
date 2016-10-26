package net.reichholf.dreamdroid.tv.fragment;

import android.os.Bundle;
import android.support.v17.preference.LeanbackPreferenceFragment;

import net.reichholf.dreamdroid.R;

public class PrefsFragment extends LeanbackPreferenceFragment {

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		// Load the preferences from an XML resource
		setPreferencesFromResource(R.xml.preferences, rootKey);
	}
}
