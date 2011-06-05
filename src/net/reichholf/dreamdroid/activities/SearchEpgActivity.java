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
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.impl.EpgSearchRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * Search the EPG for a specific term.<br>
 * Called when search is being requested by the user (by Hard- or Software-Key).
 * 
 * @author sreichholf
 * 
 */
public class SearchEpgActivity extends AbstractHttpEventListActivity {
	private SearchEpgListTask mSearchEpgListTask;
	private String mQuery;

	/**
	 * <code>AsyncTask</code> to get the EPG information for the given search
	 * term
	 * 
	 * @author sreichholf
	 * 
	 */
	private class SearchEpgListTask extends AsyncListUpdateTask {
		public SearchEpgListTask(){
			super(getString(R.string.epg_search), new EpgSearchRequestHandler(), false);
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

		Intent intent = getIntent();

		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			mQuery = intent.getStringExtra(SearchManager.QUERY);
			setAdapter();
			search();
		} else {
			finish();
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
		mCurrentItem = mMapList.get((int) id);

		// if the dialog has been opened before, remove that instance
		removeDialog(DIALOG_EPG_ITEM_ID);
		showDialog(DIALOG_EPG_ITEM_ID);

	}
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#generateTitle()
	 */
	@Override
	protected String genWindowTitle(String title){
		return title + " - '" + mQuery + "'";
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(this, mMapList, R.layout.epg_multi_service_list_item, new String[] {
				Event.KEY_SERVICE_NAME, Event.KEY_EVENT_TITLE, Event.KEY_EVENT_START_READABLE, Event.KEY_EVENT_DURATION_READABLE },
				new int[] { R.id.service_name, R.id.event_title, R.id.event_start, R.id.event_duration });
		setListAdapter(mAdapter);
	}

	/**
	 * Issues the search by executing a new <code>GetSearchEpgListTask</code>
	 */
	@SuppressWarnings("unchecked")
	private void search() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("search", mQuery));

		if (mSearchEpgListTask != null) {
			mSearchEpgListTask.cancel(true);
		}

		mSearchEpgListTask = new SearchEpgListTask();
		mSearchEpgListTask.execute(params);
	}

}
