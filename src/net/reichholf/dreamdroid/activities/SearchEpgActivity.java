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

import android.app.SearchManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * @author sreichholf
 * 
 */
public class SearchEpgActivity extends AbstractHttpEventListActivity {
	private AsyncTask<ArrayList<NameValuePair>, String, Boolean> mSearchEpgListTask;
	private String mQuery;

	/**
	 * @author sreichholf
	 * 
	 */
	private class GetSearchEpgListTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			publishProgress(getText(R.string.app_name) + "::" + getText(R.string.epg_search) + " - "
					+ getText(R.string.fetching_data));

			String xml = Event.search(mShc, params);
			if (xml != null) {
				publishProgress(getText(R.string.app_name) + "::" + getText(R.string.epg_search) + " - "
						+ getText(R.string.parsing));

				mList.clear();

				if ( Event.parseList(xml, mList ) ) {
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

			if (result) {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.epg_search)
						+ " - \"" + mQuery + "\"";
				
				mAdapter.notifyDataSetChanged();				
				if(mList.size() == 0){
					showDialog(DIALOG_EMPTY_LIST_ID);
				}
			} else {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.epg_search)
						+ " - " + getText(R.string.get_content_error);

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
			Intent intent = getIntent();

			if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
				mQuery = intent.getStringExtra(SearchManager.QUERY);
				setAdapter();
				search();
			} else {
				finish();
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
		if (mSearchEpgListTask != null) {
			mSearchEpgListTask.cancel(true);
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
	 * 
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(this, mList, R.layout.epg_multi_service_list_item, new String[] {
				Event.SERVICE_NAME, Event.EVENT_TITLE, Event.EVENT_START_READABLE, Event.EVENT_DURATION_READABLE },
				new int[] { R.id.service_name, R.id.event_title, R.id.event_start, R.id.event_duration });
		setListAdapter(mAdapter);
	}

	/**
	 * 
	 */
	@SuppressWarnings("unchecked")
	private void search() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("search", mQuery));

		if (mSearchEpgListTask != null) {
			mSearchEpgListTask.cancel(true);
		}

		mSearchEpgListTask = new GetSearchEpgListTask();
		mSearchEpgListTask.execute(params);
	}

}
