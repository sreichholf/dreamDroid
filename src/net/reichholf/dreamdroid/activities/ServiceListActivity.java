/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpEventListActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * Handles ServiceLists of (based on service references).
 * 
 * If called with Intent.ACTION_PICK it can be used for selecting services (e.g.
 * to set a timer).<br/>
 * In Pick-Mode no EPG will be loaded/shown.<br/>
 * For any other action it will be a full-featured ServiceList Browser capable
 * of showing EPG of running events or calling a
 * <code>ServiceEpgListActivity</code> to show the whole EPG of a service
 * 
 * @author sreichholf
 * 
 */
public class ServiceListActivity extends AbstractHttpEventListActivity {
//	public static final int MENU_CLOSE = 0;
	public static final int MENU_OVERVIEW = 1;
	public static final int MENU_SET_AS_DEFAULT = 2;
	public static final int MENU_RELOAD = 3;

	private String mReference;
	private String mName;
	private boolean mIsBouquetList;

	private ArrayList<ExtendedHashMap> mHistory;
	private boolean mPickMode;

	private GetServiceListTask mListTask;

	/**
	 * @author sreichholf Fetches a service list async. Does all the
	 *         error-handling, refreshing and title-setting
	 */
	private class GetServiceListTask extends AsyncListUpdateTask {		
		public GetServiceListTask(){
			super(getString(R.string.services));
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			mTaskList = new ArrayList<ExtendedHashMap>();
			publishProgress(mBaseTitle + " - " + getText(R.string.fetching_data));

			String xml;

			if (!mIsBouquetList && !mPickMode) {
				xml = Service.getEpgBouquetList(mShc, params);
			} else {
				xml = Service.getList(mShc, params);
			}

			if (xml != null) {
				publishProgress(mBaseTitle + " - " + getText(R.string.parsing));
				boolean result = false;

				if (!mIsBouquetList && !mPickMode) {
					result = Service.parseEpgBouquetList(xml, mTaskList);
				} else {
					result = Service.parseList(xml, mTaskList);
				}

				return result;

			}
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.activities.AbstractHttpListActivity#onCreate
	 * (android.os.Bundle)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		String mode = getIntent().getAction();
		if (mode.equals(Intent.ACTION_PICK)) {
			mPickMode = true;
		} else {
			mPickMode = false;
		}

		String ref = DreamDroid.SP.getString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_REF, "default");
		String name = DreamDroid.SP.getString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_NAME,
				(String) getText(R.string.bouquet_overview));

		mReference = getDataForKey(Event.SERVICE_REFERENCE, ref);
		mName = getDataForKey(Event.SERVICE_NAME, name);

		if (savedInstanceState != null) {
			mIsBouquetList = savedInstanceState.getBoolean("isBouquetList", true);
			mHistory = (ArrayList<ExtendedHashMap>) savedInstanceState.getSerializable("history");

		} else {
			mIsBouquetList = DreamDroid.SP.getBoolean(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_IS_LIST, true);
			mHistory = new ArrayList<ExtendedHashMap>();

			ExtendedHashMap map = new ExtendedHashMap();
			map.put(Event.SERVICE_REFERENCE, mReference);
			map.put(Event.SERVICE_NAME, mName);

			mHistory.add(map);
		}

		setAdapter();
		reload();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#
	 * onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable("history", mHistory);
		outState.putBoolean("isBouquetList", mIsBouquetList);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		if (mListTask != null) {
			mListTask.cancel(true);
		}

		super.onPause();
	}

	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#generateTitle()
	 */
	@Override
	protected String genWindowTitle(String title){
		return title + " - " + mName;
	}
	
	/**
	 * 
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(this, mMapList, R.layout.service_list_item, new String[] { Event.SERVICE_NAME,
				Event.EVENT_TITLE, Event.EVENT_START_TIME_READABLE, Event.EVENT_DURATION_READABLE }, new int[] {
				R.id.service_name, R.id.event_title, R.id.event_start, R.id.event_duration });

		setListAdapter(mAdapter);
	}

	/**
	 * @return
	 */
	private boolean isListTaskRunning() {

		if (mListTask != null) {
			if (mListTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
				return true;
			}
		}

		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.activities.AbstractHttpListActivity#onKeyDown
	 * (int, android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// back to standard back-button-behaviour when we're already at root
		// level
		if (!"default".equals(mReference)) {
			int index = mHistory.size() - 1;
			if (index >= 0) {
				ExtendedHashMap map = (mHistory.get(index));

				String oldref = (String) map.get(Event.SERVICE_REFERENCE);
				String oldname = (String) map.get(Event.SERVICE_NAME);

				if ((keyCode == KeyEvent.KEYCODE_BACK) && !mReference.equals(oldref)) {
					// there is a download Task running, the list may have
					// already been altered so we let that request finish

					if (!isListTaskRunning()) {
						mReference = String.valueOf(oldref);
						mName = String.valueOf(oldname);

						mHistory.remove(index);

						if (isBouquetReference(mReference)) {
							mIsBouquetList = true;
						}
						reload();

					} else {
						showToast(getText(R.string.wait_request_finished));
					}
					return true;
				}
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mCurrentItem = mMapList.get(position);
		final String ref = mCurrentItem.getString(Event.SERVICE_REFERENCE);
		final String nam = mCurrentItem.getString(Event.SERVICE_NAME);

		if (isBouquetReference(ref)) {
			if (!isListTaskRunning()) {
				mIsBouquetList = true;

				// Second hierarchy level -> we get a List of Services now
				if (isBouquetReference(mReference)) {
					mIsBouquetList = false;
				}

				ExtendedHashMap map = new ExtendedHashMap();
				map.put(Event.SERVICE_REFERENCE, String.valueOf(mReference));
				map.put(Event.SERVICE_NAME, String.valueOf(mName));
				mHistory.add(map);

				mReference = ref;
				mName = nam;

				reload();
			} else {
				showToast(getText(R.string.wait_request_finished));
			}
		} else {
			if (mPickMode) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.put(Event.SERVICE_REFERENCE, ref);
				map.put(Event.SERVICE_NAME, nam);

				Intent intent = new Intent();
				intent.putExtra(sData, map);

				setResult(RESULT_OK, intent);
				finish();
			} else {
				CharSequence[] actions = { getText(R.string.current_event), getText(R.string.browse_epg),
						getText(R.string.zap), getText(R.string.stream) };

				AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
				adBuilder.setTitle(getText(R.string.pick_action));
				adBuilder.setItems(actions, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						switch (which) {
						case 0:
							removeDialog(DIALOG_EPG_ITEM_ID);
							showDialog(DIALOG_EPG_ITEM_ID);
							break;

						case 1:
							openEpg(ref, nam);
							break;

						case 2:
							zapTo(ref);
							break;
						case 3:
							streamService(ref);
							break;
						}
					}
				});

				AlertDialog alert = adBuilder.create();
				alert.show();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		menu.add(0, MENU_SET_AS_DEFAULT, 0, getText(R.string.set_default)).setIcon(android.R.drawable.ic_menu_set_as);
		menu.add(0, MENU_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, MENU_OVERVIEW, 0, getText(R.string.bouquet_overview)).setIcon(R.drawable.ic_menu_list_overview);

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem overview = menu.getItem(2);

		if (mReference.equals("default")) {
			overview.setEnabled(false);
		} else {
			overview.setEnabled(true);
		}
		
		MenuItem reload = menu.getItem(1);
		if(!mIsBouquetList && !mPickMode){
			reload.setEnabled(true);
		} else {
			reload.setEnabled(false);
		}
		return true;
	}

	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onItemClicked(int)
	 */
	@Override
	protected boolean onItemClicked(int id){
		switch (id) {
		case MENU_OVERVIEW:
			mReference = "default";
			mName = (String) getText(R.string.bouquet_overview);
			reload();
			return true;
		case MENU_SET_AS_DEFAULT:
			Editor editor = DreamDroid.SP.edit();
			editor.putString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_REF, mReference);
			editor.putString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_NAME, mName);
			editor.putBoolean(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_IS_LIST, mIsBouquetList);

			if (editor.commit()) {
				showToast(getText(R.string.default_bouquet_set_to) + " '" + mName + "'");
			} else {
				showToast(getText(R.string.default_bouquet_not_set));
			}
			return true;
		case MENU_RELOAD:
			reload();
			return true;
		default:
			return super.onItemClicked(id);
		}	
	}

	/**
	 * @param ref
	 * @return
	 */
	private boolean isBouquetReference(String ref) {
		if (ref.startsWith("1:7:")) {
			return true;
		}
		return false;
	}

	/**
	 * @param ref
	 *            The ServiceReference to catch the EPG for
	 * @param nam
	 *            The name of the Service for the reference
	 */
	public void openEpg(String ref, String nam) {
		Intent intent = new Intent(this, ServiceEpgListActivity.class);
		ExtendedHashMap map = new ExtendedHashMap();
		map.put(Event.SERVICE_REFERENCE, ref);
		map.put(Event.SERVICE_NAME, nam);

		intent.putExtra(sData, map);

		startActivity(intent);
	}
	
	/**
	 * @param ref
	 * 			A ServiceReference
	 */
	private void streamService(String ref){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String uriString = "http://" + DreamDroid.PROFILE.getStreamHost().trim() + ":8001/" + ref;
		Log.i(DreamDroid.LOG_TAG, "Streaming URL set to '" + uriString + "'");
		
		intent.setDataAndType(Uri.parse(uriString) , "video/*");		
		startActivity(intent);
	}
	
	/**
	 * 
	 */
	@SuppressWarnings("unchecked")
	public void reload() {
		if (mListTask != null) {
			mListTask.cancel(true);
		}

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Event.SERVICE_REFERENCE, String.valueOf(mReference));
		data.put(Event.SERVICE_NAME, String.valueOf(mName));
		mExtras.putSerializable(sData, data);

		if (mReference.equals("default")) {
			loadDefault();
			return;
		}

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		if (!mIsBouquetList && !mPickMode) {
			params.add(new BasicNameValuePair("bRef", mReference));
		} else {
			params.add(new BasicNameValuePair("sRef", mReference));
		}

		mListTask = new GetServiceListTask();
		mListTask.execute(params);
	}

	/**
	 * 
	 */
	public void loadDefault() {
		String title = getText(R.string.app_name) + "::" + getText(R.string.services);
		setTitle(title);
		mIsBouquetList = true;
		
		mMapList.clear();

		String[] servicelist = getResources().getStringArray(R.array.servicelist);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefs);

		for (int i = 0; i < servicelist.length; i++) {
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(Event.SERVICE_NAME, servicelist[i]);
			map.put(Event.SERVICE_REFERENCE, servicerefs[i]);
			mMapList.add(map);
		}
		
		
		mAdapter.notifyDataSetChanged();
	}
}
