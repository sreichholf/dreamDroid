/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

/**
 * Shows a list of all connection profiles
 * 
 * @author sre
 * 
 */
public class ProfileListFragment extends ListFragment implements ActivityCallbackHandler{
	private Profile mProfile;
	private ArrayList<Profile> mDetectedProfiles;
	
	private SimpleCursorAdapter mAdapter;
	private Cursor mCursor;
	private Activity mActivity;
	private DetectDevicesTask mDetectDevicesTask;
	
	private ProgressDialog mProgress;
	private MultiPaneHandler mMultiPaneHandler;

	private class DetectDevicesTask extends AsyncTask<Void, Void, ArrayList<Profile>>{

		/* (non-Javadoc)
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected ArrayList<Profile> doInBackground(Void... params) {
			ArrayList<Profile> profiles = DeviceDetector.getAvailableHosts();
			return profiles;
		}
		
		@Override
		protected void onPostExecute( ArrayList<Profile> profiles) {
			onDevicesDetected(profiles);
		}
		
	}
	
	
	/**
	 * 
	 */
	private void detectDevices(){
		if(mDetectedProfiles == null){
			if(mDetectDevicesTask != null){
				mDetectDevicesTask.cancel(true);
				mDetectDevicesTask = null;
			}
			
			if(mProgress != null){			
				mProgress.dismiss();
			}
			mProgress = ProgressDialog.show(getActivity(), getText(R.string.searching), getText(R.string.searching_known_devices));
			mProgress.setCancelable(false);
			mDetectDevicesTask = new DetectDevicesTask();
			mDetectDevicesTask.execute();
		} else {	
			if(mDetectedProfiles.size() == 0){
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
	private void addAllDetectedDevices(){
		for(Profile p : mDetectedProfiles){		
			if (DreamDroid.addProfile(p)) {
				showToast(getText(R.string.profile_added) + " '" + p.getName() + "'");
			} else {
				showToast(getText(R.string.profile_not_added) + " '" + p.getName() + "'");
			}
		}
		mCursor.requery();
	}
	
	/**
	 * @param hosts
	 *            A list of profiles for auto-discovered dreamboxes
	 */
	private void onDevicesDetected(ArrayList<Profile> profiles){
		mProgress.dismiss();
		mDetectedProfiles = profiles;
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.autodiscover_dreamboxes);
		
		if(mDetectedProfiles.size() > 0){			
			CharSequence[] items = new CharSequence[profiles.size()];
			
			for(int i = 0; i < profiles.size(); i++){			
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
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		
		mActivity = getActivity();
		mActivity.setTitle( getString(R.string.app_name) + "::" + getString(R.string.profiles) );
		mMultiPaneHandler = (MultiPaneHandler) getActivity();
		
		mCursor = DreamDroid.getProfiles();		
	}
	
	public void onActivityCreated(Bundle savedInstanceState){
		super.onActivityCreated(savedInstanceState);
		
		mAdapter = new SimpleCursorAdapter(mActivity, android.R.layout.two_line_list_item, mCursor, new String[] {
				DreamDroid.KEY_PROFILE, DreamDroid.KEY_HOST }, new int[] { android.R.id.text1, android.R.id.text2 });

		setListAdapter(mAdapter);
		
		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
				return onListItemLongClick(a, v, position, id);
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mProfile = new Profile(mCursor);
		activateProfile();
	}

	protected boolean onListItemLongClick(AdapterView<?> a, View v, int position, long id) {
		mProfile = new Profile(mCursor);
		mActivity.showDialog(Statics.DIALOG_PROFILE_ID);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	public Dialog onCreateDialog(int id) {
		Dialog dialog;
		switch (id) {
		case (Statics.DIALOG_PROFILE_ID):
			CharSequence[] actions = { getText(R.string.edit), getText(R.string.delete) };

			AlertDialog.Builder adBuilder = new AlertDialog.Builder(mActivity);
			adBuilder.setTitle(mProfile.getName());
			adBuilder.setItems(actions, new DialogInterface.OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						editProfile();
						break;

					case 1:
						mActivity.showDialog(Statics.DIALOG_PROFILE_CONFIRM_DELETE_ID);
						break;

					default:
						break;
					}
				}
			});

			dialog = adBuilder.create();
			break;

		case (Statics.DIALOG_PROFILE_CONFIRM_DELETE_ID):
			AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

			builder.setTitle(mProfile.getName()).setMessage(R.string.confirm_delete_profile).setCancelable(false)
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {

							if (DreamDroid.deleteProfile(mProfile)) {
								showToast(getText(R.string.profile_deleted) + " '" + mProfile.getName() + "'");
							} else {
								showToast(getText(R.string.profile_not_deleted) + " '" + mProfile.getName() + "'");
							}
							// TODO Add error handling
							mProfile = null;
							mCursor.requery();
							mAdapter.notifyDataSetChanged();
						}
					}).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}
		return dialog;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_EDIT_PROFILE) {
			mActivity.setResult(resultCode);
			if (resultCode == Activity.RESULT_OK) {
				mCursor.requery();
				mAdapter.notifyDataSetChanged();
				// Reload the current profile as it may have been
				// changed/altered
				DreamDroid.reloadActiveProfile();
				mMultiPaneHandler.onProfileChanged();
			}
		}
	}


	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateOptionsMenu(android.view.Menu, android.view.MenuInflater)
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		menu.add(0, Statics.ITEM_ADD_PROFILE, 1, getText(R.string.profile_add)).setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, Statics.ITEM_DETECT_DEVICES, 2, getText(R.string.autodiscover_dreamboxes)).setIcon(android.R.drawable.ic_menu_search);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
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
		if (DreamDroid.setActiveProfile(mProfile.getId())) {
			showToast(getText(R.string.profile_activated) + " '" + mProfile.getName() + "'");
			mMultiPaneHandler.onProfileChanged();
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
		mMultiPaneHandler.showDetails(f, true);		
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
		mMultiPaneHandler.showDetails(f);
	}

	/**
	 * Shows a toast
	 * 
	 * @param text
	 *            The text to show as toast
	 */
	protected void showToast(String text) {
		Toast toast = Toast.makeText(mActivity, text, Toast.LENGTH_LONG);
		toast.show();
	}

	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.fragment.ActivityCallbackHandler#onKeyDown(int, android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.fragment.ActivityCallbackHandler#onKeyUp(int, android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}
}
