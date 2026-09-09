/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.BaseActivity;
import net.reichholf.dreamdroid.fragment.abs.BaseFragment;
import net.reichholf.dreamdroid.ui.settings.SettingsScreenKt;
import net.reichholf.dreamdroid.ui.settings.SettingsState;

/**
 * Phone settings. Compose Material 3 preference list; keys match {@code R.xml.preferences}.
 */
public class MyPreferenceFragment extends BaseFragment {

	private SettingsState mState;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mHasFabMain = false;
		mHasFabReload = false;
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.settings));
		PreferenceManager.setDefaultValues(requireContext(), R.xml.preferences, false);
		mState = SettingsState.create(requireContext());
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		requireActivity().setTitle(R.string.settings);

		ComposeView composeView = new ComposeView(requireContext());
		composeView.setLayoutParams(new ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		));
		SettingsScreenKt.bindSettingsScreen(
			composeView,
			mState,
			() -> {
				DreamDroid.setTheme((AppCompatActivity) requireActivity());
				return kotlin.Unit.INSTANCE;
			},
			() -> {
				if (DynamicColors.isDynamicColorAvailable()) {
					new Handler(Looper.getMainLooper()).postDelayed(
						() -> DreamDroid.restart(requireContext()),
						300
					);
				}
				return kotlin.Unit.INSTANCE;
			},
			() -> {
				startPiconSync();
				return kotlin.Unit.INSTANCE;
			}
		);
		return composeView;
	}

	public void startPiconSync() {
		((BaseActivity) requireActivity()).startPiconSync();
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
