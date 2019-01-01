package net.reichholf.dreamdroid.tv.fragment;

import android.app.Fragment;
import android.os.Bundle;
import androidx.preference.PreferenceDialogFragment;
import androidx.preference.PreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

/**
 * Created by Stephan on 26.10.2016.
 */

public class SettingsFragment extends LeanbackSettingsFragment {
	public static String PREFS_TYPE_GENERIC = "generic";
	public static String PREFS_TYPE_PROFILE = "profile";
	public static String KEY_PREFS_TYPE = "type";

	@Override
	public void onPreferenceStartInitialScreen() {
		String type = getActivity().getIntent().getStringExtra(KEY_PREFS_TYPE);
		if(PREFS_TYPE_GENERIC.equals(type))
			startPreferenceFragment(new PrefsFragment());
		else
			startPreferenceFragment(new ProfileFragment());
	}

	@Override
	public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
		final Fragment f =
				Fragment.instantiate(getActivity(), pref.getFragment(), pref.getExtras());
		f.setTargetFragment(caller, 0);
		if (f instanceof PreferenceFragment || f instanceof PreferenceDialogFragment) {
			startPreferenceFragment(f);
		} else {
			startImmersiveFragment(f);
		}
		return true;
	}

	@Override
	public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
		final Fragment f = new PrefsFragment();
		final Bundle args = new Bundle(1);
		args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
		f.setArguments(args);
		startPreferenceFragment(f);
		return true;
	}
}

