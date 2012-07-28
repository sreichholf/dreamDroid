/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpEventListFragment;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.SearchManager;
import android.os.Bundle;
import android.widget.SimpleAdapter;

/**
 * @author sre
 * 
 */
public class EpgSearchFragment extends AbstractHttpEventListFragment {
	private SearchEpgListTask mSearchEpgListTask;

	/**
	 * <code>AsyncTask</code> to get the EPG information for the given search
	 * term
	 * 
	 * @author sreichholf
	 * 
	 */
	private class SearchEpgListTask extends AsyncListUpdateTask {
		public SearchEpgListTask() {
			super(new EventListRequestHandler(URIStore.EPG_SEARCH), false);
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
		mCurrentTitle = getString(R.string.epg_search);
		setAdapter();
		String needle = getArguments().getString(SearchManager.QUERY);
		if(needle != null)
			search(needle);
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

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(getActivity(), mMapList, R.layout.epg_multi_service_list_item, new String[] {
				Event.KEY_SERVICE_NAME, Event.KEY_EVENT_TITLE, Event.KEY_EVENT_START_READABLE,
				Event.KEY_EVENT_DURATION_READABLE }, new int[] { R.id.service_name, R.id.event_title, R.id.event_start,
				R.id.event_duration });
		setListAdapter(mAdapter);
	}

	/**
	 * Issues the search by executing a new <code>GetSearchEpgListTask</code>
	 */
	@SuppressWarnings("unchecked")
	private void search(String needle) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("search", needle));

		if (mSearchEpgListTask != null) {
			mSearchEpgListTask.cancel(true);
		}

		mSearchEpgListTask = new SearchEpgListTask();
		mSearchEpgListTask.execute(params);
	}
}
