package net.reichholf.dreamdroid.tv.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

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
			DatabaseHelper.KEY_PROFILE_ENCODER_STREAM,
			DatabaseHelper.KEY_PROFILE_ENCODER_PATH,
			DatabaseHelper.KEY_PROFILE_ENCODER_PORT,
			DatabaseHelper.KEY_PROFILE_ENCODER_LOGIN,
			DatabaseHelper.KEY_PROFILE_ENCODER_USER,
			DatabaseHelper.KEY_PROFILE_ENCODER_PASS,
			DatabaseHelper.KEY_PROFILE_ENCODER_VIDEO_BITRATE,
			DatabaseHelper.KEY_PROFILE_ENCODER_AUDIO_BITRATE,
	};

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		applyCurrentProfile();
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
		CheckBoxPreference use_encoder = (CheckBoxPreference) findPreference(DatabaseHelper.KEY_PROFILE_ENCODER_STREAM);
		use_encoder.setOnPreferenceChangeListener((preference, newValue) -> {
			onUseEncoderChanged((Boolean)newValue);
			return true;
		});
		onUseEncoderChanged(use_encoder.isChecked());
		getActivity().setResult(Activity.RESULT_OK);
	}

	private void onUseEncoderChanged(boolean enabled) {
		Preference categoryEncoder = findPreference("category_encoder");
		categoryEncoder.setEnabled(enabled);
		Preference categoryDirect = findPreference("category_direct");
		categoryDirect.setEnabled(!enabled);
	}

	protected void applyCurrentProfile() {
		Profile p = DreamDroid.getCurrentProfile();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DatabaseHelper.KEY_PROFILE_PROFILE, p.getName());
		editor.putString(DatabaseHelper.KEY_PROFILE_HOST, p.getHost());
		editor.putString(DatabaseHelper.KEY_PROFILE_PORT, p.getPortString());
		editor.putBoolean(DatabaseHelper.KEY_PROFILE_SSL, p.isSsl());
		editor.putBoolean(DatabaseHelper.KEY_PROFILE_LOGIN, p.isLogin());
		editor.putString(DatabaseHelper.KEY_PROFILE_USER, p.getUser());
		editor.putString(DatabaseHelper.KEY_PROFILE_PASS, p.getPass());
		editor.putBoolean(DatabaseHelper.KEY_PROFILE_STREAM_LOGIN, p.isStreamLogin());
		editor.putString(DatabaseHelper.KEY_PROFILE_STREAM_PORT, p.getStreamPortString());
		editor.putBoolean(DatabaseHelper.KEY_PROFILE_FILE_LOGIN, p.isFileLogin());
		editor.putString(DatabaseHelper.KEY_PROFILE_FILE_PORT, p.getFilePortString());
		editor.putBoolean(DatabaseHelper.KEY_PROFILE_FILE_SSL, p.isFileSsl());
		editor.putBoolean(DatabaseHelper.KEY_PROFILE_ENCODER_STREAM, p.isEncoderStream());
		editor.putString(DatabaseHelper.KEY_PROFILE_ENCODER_PATH, p.getEncoderPath());
		editor.putString(DatabaseHelper.KEY_PROFILE_ENCODER_PORT, p.getEncoderPortString());
		editor.putBoolean(DatabaseHelper.KEY_PROFILE_ENCODER_LOGIN, p.isEncoderLogin());
		editor.putString(DatabaseHelper.KEY_PROFILE_ENCODER_USER, p.getEncoderUser());
		editor.putString(DatabaseHelper.KEY_PROFILE_ENCODER_PASS, p.getEncoderPass());
		editor.putString(DatabaseHelper.KEY_PROFILE_ENCODER_VIDEO_BITRATE, p.getEncoderVideoBitrateString());
		editor.putString(DatabaseHelper.KEY_PROFILE_ENCODER_AUDIO_BITRATE, p.getEncoderAudioBitrateString());
		editor.apply();
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
		p.setEncoderStream(prefs.getBoolean(DatabaseHelper.KEY_PROFILE_ENCODER_STREAM, false));
		p.setEncoderPath(prefs.getString(DatabaseHelper.KEY_PROFILE_ENCODER_PATH, "/stream"));
		p.setEncoderPort(prefs.getString(DatabaseHelper.KEY_PROFILE_ENCODER_PORT, "554"));
		p.setEncoderLogin(prefs.getBoolean(DatabaseHelper.KEY_PROFILE_ENCODER_LOGIN, false));
		p.setEncoderUser(prefs.getString(DatabaseHelper.KEY_PROFILE_ENCODER_USER, "root"));
		p.setEncoderPass(prefs.getString(DatabaseHelper.KEY_PROFILE_ENCODER_PASS, "dreambox"));
		p.setEncoderVideoBitrate(prefs.getString(DatabaseHelper.KEY_PROFILE_ENCODER_VIDEO_BITRATE, "6000"));
		p.setEncoderAudioBitrate(prefs.getString(DatabaseHelper.KEY_PROFILE_ENCODER_AUDIO_BITRATE, "128"));

		DatabaseHelper dbh = DatabaseHelper.getInstance(getActivity());
		dbh.updateProfile(p);

		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(DreamDroid.CURRENT_PROFILE, p.getId());
		editor.apply();
		DreamDroid.setCurrentProfile(p);
		super.onPause();
	}
}
