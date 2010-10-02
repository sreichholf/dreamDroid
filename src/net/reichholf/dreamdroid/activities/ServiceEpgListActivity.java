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

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * Shows the EPG of a service.
 * Timers can be set via integrated detail dialog
 * 
 * @author sreichholf
 * 
 */
public class ServiceEpgListActivity extends AbstractHttpEventListActivity {

	private AsyncTask<ArrayList<NameValuePair>, String, Boolean> mEpgListTask;

	/**
	 * Fetch the list of EPG-Events async
	 * 
	 * @author sreichholf
	 * 
	 */
	private class GetEpgListTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			publishProgress(getText(R.string.app_name) + "::" + getText(R.string.epg) + " - "
					+ getText(R.string.fetching_data));

			String xml = Event.getList(mShc, params);
			if (xml != null) {
				publishProgress(getText(R.string.app_name) + "::" + getText(R.string.epg) + " - "
						+ getText(R.string.parsing));

				mList.clear();

				if (Event.parseList(xml, mList) ) {
					return true;
				}
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
			mAdapter.notifyDataSetChanged();
			
			if (result) {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.epg) + " - "
						+ mName;
				
				if(mList.size() == 0){
					showDialog(DIALOG_EMPTY_LIST_ID);
				}
			} else {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.epg) + " - "
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
			mReference = getDataForKey(Event.SERVICE_REFERENCE);
			mName = getDataForKey(Event.SERVICE_NAME);

			if (mReference != null) {
				setAdapter();
				reload();
			} else {
				this.finish();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		if (mEpgListTask != null) {
			mEpgListTask.cancel(true);
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
		outState.putSerializable("currentItem", mCurrentItem);
		outState.putString(Event.SERVICE_REFERENCE, mReference);
		outState.putString(Event.SERVICE_NAME, mName);
		super.onSaveInstanceState(outState);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seenet.reichholf.dreamdroid.activities.AbstractHttpListActivity#
	 * onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		mCurrentItem = (ExtendedHashMap) savedInstanceState.getSerializable("currentItem");
		mReference = getDataForKey(Event.SERVICE_REFERENCE);
		mName = getDataForKey(Event.SERVICE_NAME);

		setAdapter();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mCurrentItem = mList.get((int) id);

		// if the dialog has been opened before, remove that instance
		removeDialog(DIALOG_EPG_ITEM_ID);
		showDialog(DIALOG_EPG_ITEM_ID);

	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(this, mList, R.layout.epg_list_item, new String[] { Event.EVENT_TITLE,
				Event.EVENT_START_READABLE, Event.EVENT_DURATION_READABLE }, new int[] { R.id.event_title,
				R.id.event_start, R.id.event_duration });
		setListAdapter(mAdapter);
	}

	/**
	 * Reloads the EPG information by calling a <code>GetEpgListTask</code>.
	 */
	@SuppressWarnings("unchecked")
	private void reload() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", mReference));

		if (mEpgListTask != null) {
			mEpgListTask.cancel(true);
		}

		mEpgListTask = new GetEpgListTask();
		mEpgListTask.execute(params);
	}
}
