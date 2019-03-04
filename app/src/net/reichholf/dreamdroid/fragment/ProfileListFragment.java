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
import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.SimpleToolbarFragmentActivity;
import net.reichholf.dreamdroid.adapter.recyclerview.ProfileAdapter;
import net.reichholf.dreamdroid.asynctask.DetectDevicesTask;
import net.reichholf.dreamdroid.fragment.abs.BaseRecyclerFragment;
import net.reichholf.dreamdroid.fragment.dialogs.IndeterminateProgress;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.widget.helper.ItemSelectionSupport;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Shows a list of all connection profiles
 *
 * @author sre
 */
public class ProfileListFragment extends BaseRecyclerFragment implements DetectDevicesTask.DetectDevicesTaskHandler {

	private boolean mIsActionMode;
	private boolean mIsActionModeRequired;

	private Profile mProfile;
	private ArrayList<Profile> mProfiles;
	private ArrayList<ExtendedHashMap> mProfileMapList;
	private ArrayList<Profile> mDetectedProfiles;

	private RecyclerView.Adapter mAdapter;
	private DetectDevicesTask mDetectDevicesTask;

	private IndeterminateProgress mProgress;

	private int mCurrentPos;

	public static final String KEY_ACTIVE_PROFILE = "active_profile";

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		switch (action) {
			case Statics.ACTION_DELETE_CONFIRMED:
				DatabaseHelper dbh = DatabaseHelper.getInstance(getAppCompatActivity());
				if (dbh.deleteProfile(mProfile)) {
					showToast(getString(R.string.profile_deleted) + " '" + mProfile.getName() + "'");
				} else {
					showToast(getString(R.string.profile_not_deleted) + " '" + mProfile.getName() + "'");
				}
				// TODO Add error handling
				reloadProfiles();
				mProfile = Profile.getDefault();
				mAdapter.notifyDataSetChanged();
				break;
		}
	}

	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.profilelist_context, menu);
			mIsActionMode = true;
			mIsActionModeRequired = false;
			mSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE);
			return true;
		}

		// Called each time the action mode is shown. Always called after onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return true;
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			mode.finish(); // Action picked, so close the CAB
			return onItemClicked(item.getItemId());
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mIsActionMode = false;
			if (mIsActionModeRequired)
				return;
			final RecyclerView rv = getRecyclerView();
			mSelectionSupport.setItemChecked(mSelectionSupport.getCheckedItemPosition(), false);
			rv.post(() -> mSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.NONE)

			);
		}
	};

	private void detectDevices() {
		if (mDetectedProfiles == null) {
			if (mDetectDevicesTask != null) {
				mDetectDevicesTask.cancel(true);
				mDetectDevicesTask = null;
			}

			if (mProgress != null) {
				mProgress.dismiss();
				mProgress = null;
			}
			MaterialDialog.Builder builder = new MaterialDialog.Builder(getAppCompatActivity());
			builder.progress(true, 0)
					.progressIndeterminateStyle(true)
					.title(R.string.searching)
					.content(R.string.searching_known_devices)
					.cancelable(false);
			mProgress = IndeterminateProgress.newInstance(R.string.searching, R.string.searching_known_devices);
			mProgress.setCancelable(false);
			getMultiPaneHandler().showDialogFragment(mProgress, "dialog_devicesearch_indeterminate");
			mDetectDevicesTask = new DetectDevicesTask(this);
			mDetectDevicesTask.execute();
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
		DatabaseHelper dbh = DatabaseHelper.getInstance(getAppCompatActivity());
		for (Profile p : mDetectedProfiles) {
			if (dbh.addProfile(p)) {
				showToast(getText(R.string.profile_added) + " '" + p.getName() + "'");
			} else {
				showToast(getText(R.string.profile_not_added) + " '" + p.getName() + "'");
			}
		}
		reloadProfiles();
	}

	/**
	 * @param profiles A list of profiles for auto-discovered dreamboxes
	 */
	@Override
	public void onDevicesDetected(ArrayList<Profile> profiles) {
		mProgress = (IndeterminateProgress) getFragmentManager().findFragmentByTag("dialog_devicesearch_indeterminate");
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		mDetectedProfiles = profiles;

		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
		builder.title(R.string.autodiscover_dreamboxes);

		if (mDetectedProfiles.size() > 0) {
			CharSequence[] items = new CharSequence[profiles.size()];

			for (int i = 0; i < profiles.size(); i++) {
				items[i] = String.format("%s (%s)", profiles.get(i).getName(), profiles.get(i).getHost());
			}

			builder.items(items);
			builder.positiveText(R.string.reload);
			builder.negativeText(R.string.add_all);

			builder.itemsCallback((dialog, itemView, which, text) -> {
				mProfile = mDetectedProfiles.get(which);
				editProfile();
			});
			builder.onPositive((dialog, which) -> {
				mDetectedProfiles = null;
				detectDevices();
			});
			builder.onNegative((dialog, which) -> addAllDetectedDevices());
		} else {
			builder.content(R.string.autodiscovery_failed);
			builder.neutralText(android.R.string.ok);
		}
		builder.show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		mHasFabMain = true;
		mEnableReload = false;
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		initTitle(getString(R.string.profiles));

		mCurrentPos = -1;

		mProfiles = new ArrayList<>();
		mProfileMapList = new ArrayList<>();
		mProfile = Profile.getDefault();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.card_recycler_content, container, false);
		registerFab(R.id.fab_main, R.string.profile_add, R.drawable.ic_action_fab_add, v -> createProfile());
		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mAdapter = new ProfileAdapter(getActivity(), mProfileMapList);
		getRecyclerView().setAdapter(mAdapter);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		reloadProfiles();
	}

	@Override
	public void onPause() {
		if (mDetectDevicesTask != null) {
			mDetectDevicesTask.cancel(true);
			mDetectDevicesTask = null;
		}
		IndeterminateProgress progress = (IndeterminateProgress) getFragmentManager().findFragmentByTag("dialog_devicesearch_indeterminate");
		if (progress != null) {
			progress.dismiss();
		}
		mProgress = null;
		super.onPause();
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		mProfile = mProfiles.get(position);
		if (mIsActionMode) {
			mSelectionSupport.setItemChecked(position, true);
			return;
		}
		activateProfile();
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, View view, int position, long id) {
		mProfile = mProfiles.get(position);
		getAppCompatActivity().startSupportActionMode(mActionModeCallback);
		mSelectionSupport.setItemChecked(position, true);
		return true;
	}

	@Override
	public void onRefresh() {
		reloadProfiles();
	}

	private void reloadProfiles() {
		DatabaseHelper dbh = DatabaseHelper.getInstance(getAppCompatActivity());
		mProfiles.clear();
		mProfileMapList.clear();
		mProfiles.addAll(dbh.getProfiles());

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());

		for (Profile m : mProfiles) {
			boolean isActive = false;
			int activeProfileId = sp.getInt(DreamDroid.CURRENT_PROFILE, -1);
			if (activeProfileId > -1 && activeProfileId == m.getId()) {
				isActive = true;
			}
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(DatabaseHelper.KEY_PROFILE_PROFILE, m.getName());
			map.put(DatabaseHelper.KEY_PROFILE_HOST, m.getHost());
			map.put(KEY_ACTIVE_PROFILE, isActive);
			mProfileMapList.add(map);
		}
		mAdapter.notifyDataSetChanged();
		mSwipeRefreshLayout.setRefreshing(false);
	}

	protected boolean onListItemLongClick(AdapterView<?> a, View v, int position, long id) {
		mCurrentPos = position;
		startActionMode();
		return true;
	}

	protected void startActionMode() {
		mProfile = mProfiles.get(mCurrentPos);
		mActionMode = getAppCompatActivity().startSupportActionMode(mActionModeCallback);
		mSelectionSupport.setItemChecked(mCurrentPos, true);
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.profiles, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemClicked(item.getItemId());
	}

	/**
	 * @param id The id of the selected menu item (<code>MENU_*</code> statics)
	 * @return true if click was handled, false otherwise
	 */
	protected boolean onItemClicked(int id) {
		switch (id) {
			case (Statics.ITEM_ADD_PROFILE):
				createProfile();
				break;
			case Statics.ITEM_DETECT_DEVICES:
				detectDevices();
				break;
			case Statics.ITEM_EDIT:
				editProfile();
				break;
			case Statics.ITEM_DELETE:
				getMultiPaneHandler().showDialogFragment(
						PositiveNegativeDialog.newInstance(mProfile.getName(), R.string.confirm_delete_profile,
								android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
								Statics.ACTION_NONE), "dialog_delete_profile_confirm");
				break;
			default:
				return false;
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.support.v4.app.Fragment#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_EDIT_PROFILE) {
			if (resultCode == Activity.RESULT_OK) {
				reloadProfiles();
			}
		}
	}

	/**
	 * Activates the selected profile
	 */
	private void activateProfile() {
		if (DreamDroid.setCurrentProfile(getAppCompatActivity(), mProfile.getId(), true)) {
			showToast(getText(R.string.profile_activated) + " '" + mProfile.getName() + "'");
		} else {
			showToast(getText(R.string.profile_not_activated) + " '" + mProfile.getName() + "'");
		}
		reloadProfiles();
	}

	/**
	 * Opens a <code>ProfileEditActivity</code> for the selected profile
	 */
	private void editProfile() {
		openProfileEditActivity(getActivity(), mProfile);
	}

	/**
	 * Opens a <code>ProfileEditActivity</code> for creating a new profile
	 */
	private void createProfile() {
		openProfileEditActivity(getActivity(), null);
	}

	/**
	 * Opens a <code>ProfileEditActivity</code>.
	 * @param activity The calling activity
	 * @param profile The selected profile that should be edited, null if a new one should be created instead.
	 */
	public static void openProfileEditActivity(Activity activity, Profile profile) {
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

	/**
	 * Shows a toast
	 *
	 * @param text The text to show as toast
	 */
	protected void showToast(String text) {
		Toast toast = Toast.makeText(getAppCompatActivity(), text, Toast.LENGTH_LONG);
		toast.show();
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
