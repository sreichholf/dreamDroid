/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpEventListFragment;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * Shows the EPG of a service. Timers can be set via integrated detail dialog
 * 
 * @author sreichholf
 * 
 */
public class ServiceEpgListFragment extends AbstractHttpEventListFragment {

	private GetEpgListTask mEpgListTask;

	/**
	 * Fetch the list of EPG-Events async
	 * 
	 * @author sreichholf
	 * 
	 */
	private class GetEpgListTask extends AsyncListUpdateTask{
		public GetEpgListTask() {
			super(new EventListRequestHandler(), false);
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
		getActivity().setProgressBarIndeterminateVisibility(false);
		
		mCurrentTitle = mBaseTitle = getString(R.string.app_name) + "::" + getString(R.string.epg);
		mReference = getDataForKey(Event.KEY_SERVICE_REFERENCE);
		mName = getDataForKey(Event.KEY_SERVICE_NAME);
		mMultiPaneHandler = (MultiPaneHandler) getActivity();

		if (mReference != null) {
			setAdapter();
			reload();
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
		if (mEpgListTask != null) {
			mEpgListTask.cancel(true);
		}

		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mCurrentItem = mMapList.get((int) id);
		// if the dialog has been opened before, remove that instance
		getActivity().removeDialog(Statics.DIALOG_EPG_ITEM_ID);
		getActivity().showDialog(Statics.DIALOG_EPG_ITEM_ID);

	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(getActivity(), mMapList, R.layout.epg_list_item, new String[] { Event.KEY_EVENT_TITLE,
				Event.KEY_EVENT_START_READABLE, Event.KEY_EVENT_DURATION_READABLE }, new int[] { R.id.event_title,
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
