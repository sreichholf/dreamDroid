package net.reichholf.dreamdroid.tv.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v14.preference.PreferenceDialogFragment;
import android.support.v14.preference.PreferenceFragment;
import android.support.v17.preference.LeanbackSettingsFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

/**
 * Created by Stephan on 26.10.2016.
 */

public class SettingsFragment extends LeanbackSettingsFragment {
	@Override
	public void onPreferenceStartInitialScreen() {
		startPreferenceFragment(new PrefsFragment());
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

