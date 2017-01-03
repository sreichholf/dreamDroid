/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.SearchManager;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.EpgAdapter;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerEventFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;

/**
 * @author sre
 * 
 */
public class EpgSearchFragment extends BaseHttpRecyclerEventFragment {
	private String mNeedle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.epg_search));

		String needle = getArguments().getString(SearchManager.QUERY);
		if (needle != null) {
			mNeedle = needle;
			if (mMapList.size() == 0)
				mReload = true;
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setAdapter();
	}

	/**
	 * Initializes the <code>SimpleTextAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new EpgAdapter(mMapList, R.layout.epg_multi_service_list_item,
				new String[] { Event.KEY_SERVICE_NAME, Event.KEY_EVENT_TITLE, Event.KEY_EVENT_DESCRIPTION_EXTENDED,
						Event.KEY_EVENT_START_READABLE, Event.KEY_EVENT_DURATION_READABLE }, new int[] {
						R.id.service_name, R.id.event_title, R.id.event_short, R.id.event_start, R.id.event_duration });
		getRecyclerView().setAdapter(mAdapter);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("search", mNeedle));

		return params;
	}

	@Override
	public String getLoadFinishedTitle() {
		return getBaseTitle() + " - '" + mNeedle + "'";
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		return new AsyncListLoader(getAppCompatActivity(), new EventListRequestHandler(
				URIStore.EPG_SEARCH), false, args);
	}
}
