/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.DreamDroidListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * Shows a list of all connection profiles
 * 
 * @author sre
 * 
 */
public class ProfileListFragment extends DreamDroidListFragment implements ActionDialog.DialogActionListener {
	private Profile mProfile;
	private ArrayList<Profile> mProfiles;
	private ArrayList<ExtendedHashMap> mProfileMapList;
	private ArrayList<Profile> mDetectedProfiles;

	private SimpleAdapter mAdapter;
	private DetectDevicesTask mDetectDevicesTask;

	private ProgressDialog mProgress;

	private class DetectDevicesTask extends AsyncTask<Void, Void, ArrayList<Profile>> {

		@Override
		protected ArrayList<Profile> doInBackground(Void... params) {
			ArrayList<Profile> profiles = DeviceDetector.getAvailableHosts();
			return profiles;
		}

		@Override
		protected void onPostExecute(ArrayList<Profile> profiles) {
			onDevicesDetected(profiles);
		}
	}

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
			mProgress = ProgressDialog.show(getSherlockActivity(), getText(R.string.searching),
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
		DatabaseHelper dbh = DatabaseHelper.getInstance(getSherlockActivity());
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
	 * @param hosts
	 *            A list of profiles for auto-discovered dreamboxes
	 */
	private void onDevicesDetected(ArrayList<Profile> profiles) {
		mProgress.dismiss();
		mDetectedProfiles = profiles;

		AlertDialog.Builder builder = new AlertDialog.Builder(getSherlockActivity());
		builder.setTitle(R.string.autodiscover_dreamboxes);

		if (mDetectedProfiles.size() > 0) {
			CharSequence[] items = new CharSequence[profiles.size()];

			for (int i = 0; i < profiles.size(); i++) {
				items[i] = profiles.get(i).getName();
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
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		initTitle(getString(R.string.profiles));

		mProfiles = new ArrayList<Profile>();
		mProfileMapList = new ArrayList<ExtendedHashMap>();
		mProfile = new Profile();

		mAdapter = new SimpleAdapter(getSherlockActivity(), mProfileMapList, android.R.layout.two_line_list_item,
				new String[] { DatabaseHelper.KEY_PROFILE, DatabaseHelper.KEY_HOST }, new int[] { android.R.id.text1,
						android.R.id.text2 });
		setListAdapter(mAdapter);
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
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mProfile = mProfiles.get(position);

		CharSequence[] actions = { getText(R.string.activate), getText(R.string.edit), getText(R.string.delete) };
		int[] actionIds = { Statics.ACTION_ACTIVATE, Statics.ACTION_EDIT, Statics.ACTION_DELETE };
		getMultiPaneHandler().showDialogFragment(
				SimpleChoiceDialog.newInstance(mProfile.getName(), actions, actionIds), "dialog_profile_selected");
	}

	private void reloadProfiles() {
		DatabaseHelper dbh = DatabaseHelper.getInstance(getSherlockActivity());
		mProfiles.clear();
		mProfileMapList.clear();
		mProfiles.addAll(dbh.getProfiles());
		for (Profile m : mProfiles) {
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(DatabaseHelper.KEY_PROFILE, m.getName());
			map.put(DatabaseHelper.KEY_HOST, m.getHost());
			mProfileMapList.add(map);
		}
		mAdapter.notifyDataSetChanged();
	}

	protected boolean onListItemLongClick(AdapterView<?> a, View v, int position, long id) {
		mProfile = mProfiles.get(position);
		activateProfile();

		return true;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("profileId", mProfile.getId());
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.profiles, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemClicked(item.getItemId());
	}

	/**
	 * @param id
	 *            The id of the selected menu item (<code>MENU_*</code> statics)
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
		default:
			return false;
		}

		return true;
	}

	/**
	 * Activates the selected profile
	 */
	private void activateProfile() {
		if (DreamDroid.setCurrentProfile(getSherlockActivity(), mProfile.getId(), true)) {
			showToast(getText(R.string.profile_activated) + " '" + mProfile.getName() + "'");
		} else {
			showToast(getText(R.string.profile_not_activated) + " '" + mProfile.getName() + "'");
		}
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
		getMultiPaneHandler().showDetails(f);
	}

	/**
	 * Shows a toast
	 * 
	 * @param text
	 *            The text to show as toast
	 */
	protected void showToast(String text) {
		Toast toast = Toast.makeText(getSherlockActivity(), text, Toast.LENGTH_LONG);
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

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		switch (action) {
		case Statics.ACTION_ACTIVATE:
			activateProfile();
			break;
		case Statics.ACTION_EDIT:
			editProfile();
			break;
		case Statics.ACTION_DELETE:
			getMultiPaneHandler().showDialogFragment(
					PositiveNegativeDialog.newInstance(mProfile.getName(), R.string.confirm_delete_profile,
							android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
							Statics.ACTION_NONE), "dialog_delete_profile_confirm");
			break;
		case Statics.ACTION_DELETE_CONFIRMED:
			DatabaseHelper dbh = DatabaseHelper.getInstance(getSherlockActivity());
			if (dbh.deleteProfile(mProfile)) {
				showToast(getString(R.string.profile_deleted) + " '" + mProfile.getName() + "'");
			} else {
				showToast(getString(R.string.profile_not_deleted) + " '" + mProfile.getName() + "'");
			}
			// TODO Add error handling
			reloadProfiles();
			mProfile = new Profile();
			mAdapter.notifyDataSetChanged();
			break;
		}
	}
}
