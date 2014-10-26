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
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.os.Bundle;
import android.support.v4.content.Loader;
import android.widget.SimpleAdapter;

/**
 * Shows the EPG of a service. Timers can be set via integrated detail dialog
 * 
 * @author sreichholf
 * 
 */
public class ServiceEpgListFragment extends AbstractHttpEventListFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		mEnableReload = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.epg));

		mReference = getDataForKey(Event.KEY_SERVICE_REFERENCE);
		mName = getDataForKey(Event.KEY_SERVICE_NAME);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (mReference != null) {
			setAdapter();
			if (mMapList.size() <= 0)
				reload();
		} else {
			finish();
		}
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(getActionBarActivity(), mMapList, R.layout.epg_list_item, new String[] {
				Event.KEY_EVENT_TITLE, Event.KEY_EVENT_DESCRIPTION_EXTENDED, Event.KEY_EVENT_START_READABLE,
				Event.KEY_EVENT_DURATION_READABLE }, new int[] { R.id.event_title, R.id.event_short, R.id.event_start,
				R.id.event_duration });
		setListAdapter(mAdapter);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", mReference));
		return params;
	}

	@Override
	public String getLoadFinishedTitle() {
		return getBaseTitle() + " - " + mName;
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getActionBarActivity(), new EventListRequestHandler(), false, args);
		return loader;
	}
}
