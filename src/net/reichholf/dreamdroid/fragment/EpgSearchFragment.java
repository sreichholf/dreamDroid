/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpEventListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.SearchManager;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.widget.SimpleAdapter;

/**
 * @author sre
 * 
 */
public class EpgSearchFragment extends AbstractHttpEventListFragment {
	private String mNeedle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.epg_search));
		setAdapter();
		String needle = getArguments().getString(SearchManager.QUERY);
		if (needle != null) {
			mNeedle = needle;
			if (mMapList.size() == 0)
				reload();
		}
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(getSherlockActivity(), mMapList, R.layout.epg_multi_service_list_item,
				new String[] { Event.KEY_SERVICE_NAME, Event.KEY_EVENT_TITLE, Event.KEY_EVENT_DESCRIPTION_EXTENDED,
						Event.KEY_EVENT_START_READABLE, Event.KEY_EVENT_DURATION_READABLE }, new int[] {
						R.id.service_name, R.id.event_title, R.id.event_short, R.id.event_start, R.id.event_duration });
		setListAdapter(mAdapter);
	}

	@Override
	protected ArrayList<NameValuePair> getHttpParams() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("search", mNeedle));

		return params;
	}

	@Override
	public String getLoadFinishedTitle() {
		return getBaseTitle() + " - '" + mNeedle + "'";
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getSherlockActivity(), new EventListRequestHandler(
				URIStore.EPG_SEARCH), false, args);
		return loader;
	}
}
