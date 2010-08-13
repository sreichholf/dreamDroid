/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpEventListActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.RefStore;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * @author sreichholf
 * 
 */
public class ServiceListActivity extends AbstractHttpEventListActivity {
	public static final int MENU_HOME = 0;

	private String mReference;
	private String mName;
	private boolean mIsBouquetList;

	private ArrayList<ExtendedHashMap> mHistory;
	private boolean mPickMode;

	private AsyncTask<ArrayList<NameValuePair>, String, Boolean> mListTask;

	/**
	 * @author sreichholf
	 * 
	 */
	private class GetServiceListTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			publishProgress(getText(R.string.app_name) + "::" + getText(R.string.services) + " - "
					+ getText(R.string.fetching_data));

			String xml;

			if (!mIsBouquetList && !mPickMode) {
				xml = Service.getEpgBouquetList(mShc, params);
			} else {
				xml = Service.getList(mShc, params);
			}

			if (xml != null) {
				publishProgress(getText(R.string.app_name) + "::" + getText(R.string.services) + " - "
						+ getText(R.string.parsing));

				mList.clear();
				boolean result = false;

				if (!mIsBouquetList && !mPickMode) {
					result = Service.parseEpgBouquetList(xml, mList);
				} else {
					result = Service.parseList(xml, mList);
				}

				return result;

			}
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			setTitle(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			String title = null;

			if (result) {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.services) + " - "
						+ mName;
				
				mAdapter.notifyDataSetChanged();				
				if(mList.size() == 0){
					showDialog(DIALOG_EMPTY_LIST_ID);
				}			
			} else {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.services) + " - "
						+ getText(R.string.get_content_error);

				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			setTitle(title);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.activities.AbstractHttpListActivity#onCreate
	 * (android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState == null) {
			String mode = getIntent().getAction();
			if (mode.equals(Intent.ACTION_PICK)) {
				mPickMode = true;
			} else {
				mPickMode = false;
			}

			mIsBouquetList = true;

			mReference = getDataForKey(Event.SERVICE_REFERENCE, "default");
			mName = getDataForKey(Event.SERVICE_NAME, "Overview");

			mHistory = new ArrayList<ExtendedHashMap>();
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(Event.SERVICE_REFERENCE, mReference);
			map.put(Event.SERVICE_NAME, mName);
			mHistory.add(map);
			setAdapter();
			reload();
		}
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

	/*
	 * (non-Javadoc)
	 * 
	 * @seenet.reichholf.dreamdroid.activities.AbstractHttpListActivity#
	 * onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putSerializable("history", mHistory);
		outState.putBoolean("isbouquetlist", mIsBouquetList);
		outState.putBoolean("pickMode", mPickMode);

		super.onSaveInstanceState(outState);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seenet.reichholf.dreamdroid.activities.AbstractHttpListActivity#
	 * onRestoreInstanceState(android.os.Bundle)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		mHistory = (ArrayList<ExtendedHashMap>) savedInstanceState.getSerializable("history");
		mReference = getDataForKey(Event.SERVICE_REFERENCE, "default");
		mName = getDataForKey(Event.SERVICE_NAME, "Overview");
		mIsBouquetList = savedInstanceState.getBoolean("isbouquetlist");
		mPickMode = savedInstanceState.getBoolean("pickMode");

		setAdapter();
	}

	/**
	 * 
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(this, mList, R.layout.service_list_item, new String[] { Event.SERVICE_NAME,
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
		int index = mHistory.size() - 1;
		ExtendedHashMap map = (mHistory.get(index));

		String oldref = (String) map.get(Event.SERVICE_REFERENCE);
		String oldname = (String) map.get(Event.SERVICE_NAME);

		if ((keyCode == KeyEvent.KEYCODE_BACK) && !mReference.equals(oldref)) {
			// there is a download Task running, the list may have already been
			// altered
			// so we let that request finish

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
		mCurrentItem = mList.get(position);
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
						getText(R.string.zap) };

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
		menu.add(0, MENU_HOME, 0, getText(R.string.close)).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_HOME:
			finish();
			return true;
		}
		return false;
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
	 */
	public void zapTo(String ref) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", ref));
		String xml = Service.zap(mShc, params);

		ExtendedHashMap result = Service.parseSimpleResult(xml);

		String resulttext = (String) getText(R.string.get_content_error);
		if (result != null) {
			resulttext = result.getString(SimpleResult.STATE_TEXT);
		}

		Toast toast = Toast.makeText(getApplicationContext(), resulttext, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param ref
	 * @param nam
	 */
	public void openEpg(String ref, String nam) {
		Intent intent = new Intent(this, ServiceEpgListActivity.class);
		ExtendedHashMap map = new ExtendedHashMap();
		map.put(Event.SERVICE_REFERENCE, ref);
		map.put(Event.SERVICE_NAME, nam);

		intent.putExtra(sData, map);

		this.startActivity(intent);
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

		mListTask = new GetServiceListTask().execute(params);
	}

	/**
	 * 
	 */
	public void loadDefault() {
		String title = getText(R.string.app_name) + "::" + getText(R.string.services);
		setTitle(title);

		mList.clear();

		ExtendedHashMap map = new ExtendedHashMap();
		// TV
		map.put(Event.SERVICE_NAME, getText(R.string.tv_bouquets));
		map.put(Event.SERVICE_REFERENCE, RefStore.TV_BOUQUETS);
		mList.add(map.clone());

		map.clear();
		map.put(Event.SERVICE_NAME, getText(R.string.tv_provider));
		map.put(Event.SERVICE_REFERENCE, RefStore.TV_PROVIDER);
		mList.add(map.clone());

		map.clear();
		map.put(Event.SERVICE_NAME, getText(R.string.tv_all));
		map.put(Event.SERVICE_REFERENCE, RefStore.TV_ALL);
		mList.add(map.clone());

		// Radio
		map.clear();
		map.put(Event.SERVICE_NAME, getText(R.string.radio_bouquets));
		map.put(Event.SERVICE_REFERENCE, RefStore.RADIO_BOUQUETS);
		mList.add(map.clone());

		map.clear();
		map.put(Event.SERVICE_NAME, getText(R.string.radio_provider));
		map.put(Event.SERVICE_REFERENCE, RefStore.RADIO_PROVIDERS);
		mList.add(map.clone());

		map.clear();
		map.put(Event.SERVICE_NAME, getText(R.string.radio_all));
		map.put(Event.SERVICE_REFERENCE, RefStore.RADIO_ALL);
		mList.add(map.clone());

		mAdapter.notifyDataSetChanged();
	}
}
