/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ActionMode;
import androidx.compose.ui.platform.ComposeView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.SimpleToolbarFragmentActivity;
import net.reichholf.dreamdroid.enigma.ProfileDetectLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseFragment;
import net.reichholf.dreamdroid.fragment.dialogs.IndeterminateProgress;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.room.AppDatabase;
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem;
import net.reichholf.dreamdroid.ui.profiles.ProfilesListState;
import net.reichholf.dreamdroid.ui.profiles.ProfilesListStateKt;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Shows a list of all connection profiles
 *
 * @author sre
 */
public class ProfileListFragment extends BaseFragment {

	private boolean mIsActionMode;
	private boolean mIsActionModeRequired;
	@Nullable
	private ActionMode mActionMode;

	private Profile mProfile;
	private ArrayList<Profile> mProfiles;

	@Nullable
	private ArrayList<Profile> mDetectedProfiles;

	@Nullable
	private Job mDetectDevicesJob;

	@Nullable
	private IndeterminateProgress mProgress;

	private ProfilesListState mListState;

	public static final String KEY_ACTIVE_PROFILE = "active_profile";

	@NonNull
	private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
		@Override
		public boolean onCreateActionMode(@NonNull ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.profilelist_context, menu);
			mIsActionMode = true;
			mIsActionModeRequired = false;
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return true;
		}

		@Override
		public boolean onActionItemClicked(@NonNull ActionMode mode, @NonNull MenuItem item) {
			mode.finish();
			return onItemClicked(item.getItemId());
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mIsActionMode = false;
			mActionMode = null;
			if (mIsActionModeRequired)
				return;
		}
	};

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if (action == Statics.ACTION_DELETE_CONFIRMED) {
			Profile.ProfileDao dao = AppDatabase.profiles(getContext());
			dao.deleteProfile(mProfile);
			showToast(getString(R.string.profile_deleted) + " '" + mProfile.getName() + "'");
			reloadProfiles();
			mProfile = Profile.getDefault();
		}
	}

	private void detectDevices() {
		if (mDetectedProfiles == null) {
			if (mDetectDevicesJob != null) {
				mDetectDevicesJob.cancel(null);
				mDetectDevicesJob = null;
			}

			if (mProgress != null) {
				mProgress.dismiss();
				mProgress = null;
			}

			mProgress = IndeterminateProgress.newInstance(R.string.searching, R.string.searching_known_devices);
			mProgress.setCancelable(false);
			getMultiPaneHandler().showDialogFragment(mProgress, "dialog_devicesearch_indeterminate");
			mDetectDevicesJob = ProfileDetectLoadKt.launchDetectDevicesLoad(this, profiles -> {
				mDetectDevicesJob = null;
				onDevicesDetected(profiles);
				return Unit.INSTANCE;
			});
		} else {
			if (mDetectedProfiles.size() == 0) {
				mDetectedProfiles = null;
				detectDevices();
			} else {
				onDevicesDetected(mDetectedProfiles);
			}
		}
	}

	private void addAllDetectedDevices() {
		Profile.ProfileDao dao = AppDatabase.profiles(getContext());
		for (Profile p : mDetectedProfiles) {
			p.setId(dao.addProfile(p));
			showToast(getText(R.string.profile_added) + " '" + p.getName() + "'");
		}
		reloadProfiles();
	}

	private void onDevicesDetected(@NonNull ArrayList<Profile> profiles) {
		// Dialog was shown on the activity FM via MultiPaneHandler; do not use the
		// fragment's getFragmentManager() (child FM when nested under PhoneNavHost).
		mProgress = (IndeterminateProgress) requireActivity().getSupportFragmentManager()
				.findFragmentByTag("dialog_devicesearch_indeterminate");
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		mDetectedProfiles = profiles;

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
		builder.setTitle(R.string.autodiscover_dreamboxes);

		if (mDetectedProfiles.size() > 0) {
			CharSequence[] items = new CharSequence[profiles.size()];

			for (int i = 0; i < profiles.size(); i++) {
				items[i] = String.format("%s (%s)", profiles.get(i).getName(), profiles.get(i).getHost());
			}

			builder
				.setItems(items, (dialog, which) -> {
					mProfile = mDetectedProfiles.get(which);
					editProfile();
				})
				.setPositiveButton(R.string.reload, (dialog, which) -> {
					mDetectedProfiles = null;
					detectDevices();
				})
				.setNegativeButton(R.string.add_all, (dialog, which) -> addAllDetectedDevices());

		} else {
			builder.setMessage(R.string.autodiscovery_failed);
			builder.setNeutralButton(android.R.string.ok, (dialog, which) -> {
			});
		}
		builder.show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mHasFabMain = true;
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		initTitles(getString(R.string.profiles));
		mProfiles = new ArrayList<>();
		mProfile = Profile.getDefault();
		mListState = new ProfilesListState();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ComposeView composeView = new ComposeView(requireContext());
		composeView.setLayoutParams(new ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		));
		ProfilesListStateKt.bindProfilesScreen(
			composeView,
			mListState,
			item -> {
				onProfileRowClick(item);
				return kotlin.Unit.INSTANCE;
			},
			item -> {
				onProfileRowLongClick(item);
				return kotlin.Unit.INSTANCE;
			}
		);
		return composeView;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		registerFab(R.id.fab_main, R.string.profile_add, R.drawable.ic_action_fab_add, v -> createProfile());
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		reloadProfiles();
	}

	@Override
	public void onPause() {
		if (mDetectDevicesJob != null) {
			mDetectDevicesJob.cancel(null);
			mDetectDevicesJob = null;
		}
		IndeterminateProgress progress = (IndeterminateProgress) requireActivity().getSupportFragmentManager()
				.findFragmentByTag("dialog_devicesearch_indeterminate");
		if (progress != null) {
			progress.dismiss();
		}
		mProgress = null;
		super.onPause();
	}

	private void onProfileRowClick(ProfileListItem item) {
		selectProfile(item);
		if (mIsActionMode) {
			return;
		}
		activateProfile();
	}

	private void onProfileRowLongClick(ProfileListItem item) {
		selectProfile(item);
		mActionMode = getAppCompatActivity().startSupportActionMode(mActionModeCallback);
	}

	private void selectProfile(ProfileListItem item) {
		for (Profile p : mProfiles) {
			if (p.getId() != null && p.getId() == item.getId()) {
				mProfile = p;
				return;
			}
		}
	}

	private void reloadProfiles() {
		Profile.ProfileDao dao = AppDatabase.profiles(getContext());
		mProfiles.clear();
		mProfiles.addAll(dao.getProfiles());

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		int activeProfileId = sp.getInt(DreamDroid.CURRENT_PROFILE, -1);
		List<ProfileListItem> rows = new ArrayList<>();
		for (Profile m : mProfiles) {
			boolean isActive = activeProfileId > -1 && m.getId() != null && activeProfileId == m.getId();
			int id = m.getId() == null ? 0 : m.getId();
			rows.add(new ProfileListItem(id, m.getName(), m.getHost(), isActive));
		}
		mListState.replaceAll(rows);
	}

	@Override
	public void createOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.profiles, menu);
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (onItemClicked(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected boolean onItemClicked(int id) {
		switch (id) {
			case (Statics.ITEM_ADD_PROFILE):
				createProfile();
				return true;
			case Statics.ITEM_DETECT_DEVICES:
				detectDevices();
				return true;
			case Statics.ITEM_EDIT:
				editProfile();
				return true;
			case Statics.ITEM_DELETE:
				getMultiPaneHandler().showDialogFragment(
					PositiveNegativeDialog.newInstance(mProfile.getName(), R.string.confirm_delete_profile,
						android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
						Statics.ACTION_NONE), "dialog_delete_profile_confirm");
				return true;
			default:
				return false;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_EDIT_PROFILE) {
			if (resultCode == Activity.RESULT_OK) {
				reloadProfiles();
			}
		}
	}

	private void activateProfile() {
		if (DreamDroid.setCurrentProfile(getAppCompatActivity(), mProfile.getId(), true)) {
			showToast(getText(R.string.profile_activated) + " '" + mProfile.getName() + "'");
		} else {
			showToast(getText(R.string.profile_not_activated) + " '" + mProfile.getName() + "'");
		}
		reloadProfiles();
	}

	private void editProfile() {
		openProfileEditActivity(getActivity(), mProfile);
	}

	private void createProfile() {
		openProfileEditActivity(getActivity(), null);
	}

	public static void openProfileEditActivity(@NonNull Activity activity, @Nullable Profile profile) {
		ExtendedHashMap data = new ExtendedHashMap();
		data.put("action", Intent.ACTION_EDIT);

		if (profile != null) {
			data.put("profile", profile);
		}

		Intent intent = new Intent(activity, SimpleToolbarFragmentActivity.class);
		intent.putExtra("fragmentClass", ProfileEditFragment.class);
		intent.putExtra("titleResource", profile == null ? R.string.profile_add : R.string.edit_profile);
		intent.putExtra("serializableData", data);
		activity.startActivityForResult(intent, Statics.REQUEST_EDIT_PROFILE);
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
