/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.room.AppDatabase;
import net.reichholf.dreamdroid.ui.profiles.ProfileEditState;
import net.reichholf.dreamdroid.ui.profiles.ProfileEditStateKt;

import static net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment.sData;

/**
 * Used to edit connection profiles
 *
 * @author sre
 */
public class ProfileEditFragment extends BaseFragment {
	@Nullable
	private Profile mCurrentProfile;

	private ProfileEditState mEditState;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mHasFabMain = false;
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		initTitles(getString(R.string.edit_profile));
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ExtendedHashMap extras = (ExtendedHashMap) getArguments().getSerializable(sData);
		assert extras != null;

		if (Intent.ACTION_EDIT.equals(extras.get("action"))) {
			mCurrentProfile = (Profile) extras.get("profile");
			if (mCurrentProfile == null)
				mCurrentProfile = Profile.getDefault();
		} else {
			mCurrentProfile = Profile.getDefault();
		}
		mEditState = ProfileEditState.fromProfile(mCurrentProfile);

		ComposeView composeView = new ComposeView(requireContext());
		composeView.setLayoutParams(new ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		));
		ProfileEditStateKt.bindProfileEditScreen(
			composeView,
			mEditState,
			getString(R.string.save),
			() -> {
				save();
				return kotlin.Unit.INSTANCE;
			}
		);
		return composeView;
	}

	@Override
	public void createOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.save, menu);
	}

	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		switch (item.getItemId()) {
			case Statics.ITEM_SAVE:
				save();
				break;
			case Statics.ITEM_CANCEL:
				finish(Activity.RESULT_CANCELED);
				break;
			default:
				return false;
		}
		return true;
	}

	/**
	 * Save the profile permanently
	 */
	private void save() {
		mEditState.applyTo(mCurrentProfile);

		Context ctx = getContext();
		if (ctx == null) { //FIMXE: why/how does this happen?
			showToast(getText(R.string.profile_not_updated) + " '" + mCurrentProfile.getName() + "'");
			return;
		}
		Profile.ProfileDao dao = AppDatabase.profiles(getContext());
		if (mCurrentProfile.getId() > 0) {
			if (mCurrentProfile.getHost() == null || "".equals(mCurrentProfile.getHost())) {
				showToast(getText(R.string.host_empty));
				return;
			}
			if (mCurrentProfile.getStreamHost() == null) {
				mCurrentProfile.setStreamHost("");
			}
			dao.updateProfile(mCurrentProfile);
			if (mCurrentProfile.getId().equals(DreamDroid.getCurrentProfile().getId()))
				DreamDroid.setCurrentProfile(mCurrentProfile);
			showToast(getText(R.string.profile_updated) + " '" + mCurrentProfile.getName() + "'");
			finish(Activity.RESULT_OK);
		} else {
			mCurrentProfile.setId(dao.addProfile(mCurrentProfile));
			showToast(getText(R.string.profile_added) + " '" + mCurrentProfile.getName() + "'");
			finish(Activity.RESULT_OK);
		}
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
