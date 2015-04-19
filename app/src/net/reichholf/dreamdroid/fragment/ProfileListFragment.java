/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.ProfileListSimpleAdapter;
import net.reichholf.dreamdroid.fragment.abs.DreamDroidListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector;

import java.util.ArrayList;

/**
 * Shows a list of all connection profiles
 *
 * @author sre
 */
public class ProfileListFragment extends DreamDroidListFragment implements ActionDialog.DialogActionListener {
	private Profile mProfile;
	private ArrayList<Profile> mProfiles;
	private ArrayList<ExtendedHashMap> mProfileMapList;
	private ArrayList<Profile> mDetectedProfiles;

	private SimpleAdapter mAdapter;
	private DetectDevicesTask mDetectDevicesTask;

	private ProgressDialog mProgress;

	public static final String KEY_ACTIVE_PROFILE = "active_profile";

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		switch(action){
			case Statics.ACTION_DELETE_CONFIRMED:
				DatabaseHelper dbh = DatabaseHelper.getInstance(getActionBarActivity());
				if (dbh.deleteProfile(mProfile)) {
					showToast(getString(R.string.profile_deleted) + " '" + mProfile.getName() + "'");
				} else {
					showToast(getString(R.string.profile_not_deleted) + " '" + mProfile.getName() + "'");
				}
				// TODO Add error handling
				reloadProfiles();
				mProfile = Profile.DEFAULT;
				mAdapter.notifyDataSetChanged();
				break;
		}
	}

	private class DetectDevicesTask extends AsyncTask<Void, Void, ArrayList<Profile>> {

		@Override
		protected ArrayList<Profile> doInBackground(Void... params) {
			return DeviceDetector.getAvailableHosts();
		}

		@Override
		protected void onPostExecute(ArrayList<Profile> profiles) {
			onDevicesDetected(profiles);
		}
	}


	protected boolean mIsActionMode;
	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.profilelist_context, menu);
			mIsActionMode = true;
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
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
			final ListView lv = getListView();
			lv.setItemChecked(lv.getCheckedItemPosition(), false);
			getListView().post(new Runnable() {
				@Override
				public void run() {
					lv.setChoiceMode(ListView.CHOICE_MODE_NONE);
				}
			});
			mIsActionMode = false;
		}
	};


	/**
	 *
	 */
	private void detectDevices() {
		if (mDetectedProfiles == null) {
			if (mDetectDevicesTask != null) {
				mDetectDevicesTask.cancel(true);
				mDetectDevicesTask = null;
			}

			if (mProgress != null) {
				mProgress.dismiss();
			}
			mProgress = ProgressDialog.show(getActionBarActivity(), getText(R.string.searching),
					getText(R.string.searching_known_devices));
			mProgress.setCancelable(false);
			mDetectDevicesTask = new DetectDevicesTask();
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

	/**
	 *
	 */
	private void addAllDetectedDevices() {
		DatabaseHelper dbh = DatabaseHelper.getInstance(getActionBarActivity());
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
	private void onDevicesDetected(ArrayList<Profile> profiles) {
		mProgress.dismiss();
		mDetectedProfiles = profiles;

		AlertDialog.Builder builder;
		if(Build.VERSION.SDK_INT >= 11)
			builder = new AlertDialog.Builder(getActionBarActivity(), DreamDroid.getDialogTheme(getActionBarActivity()));
		else
			builder = new AlertDialog.Builder(getActionBarActivity());
		builder.setTitle(R.string.autodiscover_dreamboxes);

		if (mDetectedProfiles.size() > 0) {
			CharSequence[] items = new CharSequence[profiles.size()];

			for (int i = 0; i < profiles.size(); i++) {
				items[i] = String.format("%s (%s)", profiles.get(i).getName(), profiles.get(i).getHost());
			}

			builder.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mProfile = mDetectedProfiles.get(which);
					editProfile();
					dialog.dismiss();
				}

			});

			builder.setPositiveButton(R.string.reload, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					mDetectedProfiles = null;
					dialog.dismiss();
					detectDevices();
				}
			});

			builder.setNegativeButton(R.string.add_all, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					addAllDetectedDevices();
				}
			});
		} else {
			builder.setMessage(R.string.autodiscovery_failed);
			builder.setNeutralButton(android.R.string.ok, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});
		}
		builder.show();
	}

	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		initTitle(getString(R.string.profiles));

		mProfiles = new ArrayList<>();
		mProfileMapList = new ArrayList<>();
		mProfile = Profile.DEFAULT;

		mAdapter = new ProfileListSimpleAdapter(getActionBarActivity(), mProfileMapList, R.layout.two_line_card_list_item,
				new String[]{DatabaseHelper.KEY_PROFILE_PROFILE, DatabaseHelper.KEY_PROFILE_HOST}, new int[]{android.R.id.text1, android.R.id.text2});
		setListAdapter(mAdapter);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.card_list_content_fab, container, false);

		FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab_add);
		fab.setContentDescription(getString(R.string.profile_add));
		ListView listView = (ListView) view.findViewById(android.R.id.list);
		registerFab(R.id.fab_add, view, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createProfile();
			}
		}, listView);

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		reloadProfiles();
		if (savedInstanceState != null) {
			int pos = savedInstanceState.getInt("cursorPosition");
			mProfile = mProfiles.get(pos);
		}

		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
				return onListItemLongClick(a, v, position, id);
			}
		});
		SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) getActionBarActivity().findViewById(R.id.ptr_layout);
		swipeRefreshLayout.setEnabled(false);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mProfile = mProfiles.get(position);
		if (mIsActionMode) {
			getListView().setItemChecked(position, true);
			return;
		}
		activateProfile();
	}

	private void reloadProfiles() {
		DatabaseHelper dbh = DatabaseHelper.getInstance(getActionBarActivity());
		mProfiles.clear();
		mProfileMapList.clear();
		mProfiles.addAll(dbh.getProfiles());

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActionBarActivity());

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
	}

	protected boolean onListItemLongClick(AdapterView<?> a, View v, int position, long id) {
		mProfile = mProfiles.get(position);
		getActionBarActivity().startSupportActionMode(mActionModeCallback);
		getListView().setItemChecked(position, true);
		return true;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("profileId", mProfile.getId());
		super.onSaveInstanceState(outState);
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
	 * @return
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

	/**
	 * Activates the selected profile
	 */
	private void activateProfile() {
		if (DreamDroid.setCurrentProfile(getActionBarActivity(), mProfile.getId(), true)) {
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
		Bundle args = new Bundle();
		args.putString("action", Intent.ACTION_EDIT);
		args.putSerializable("profile", mProfile);

		Fragment f = new ProfileEditFragment();
		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_EDIT_PROFILE);
		getMultiPaneHandler().showDetails(f, true);
	}

	/**
	 * Opens a <code>ProfileEditActivity</code> for creating a new profile
	 */
	private void createProfile() {
		Bundle args = new Bundle();
		args.putString("action", Intent.ACTION_EDIT);

		Fragment f = new ProfileEditFragment();
		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_EDIT_PROFILE);
		getMultiPaneHandler().showDetails(f, true);
	}

	/**
	 * Shows a toast
	 *
	 * @param text The text to show as toast
	 */
	protected void showToast(String text) {
		Toast toast = Toast.makeText(getActionBarActivity(), text, Toast.LENGTH_LONG);
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
