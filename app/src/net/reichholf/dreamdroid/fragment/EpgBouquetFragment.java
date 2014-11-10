package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.SimpleAdapter;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpEventListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;

/**
 * Created by Stephan on 01.11.2014.
 */
public class EpgBouquetFragment extends AbstractHttpEventListFragment {
	private int mTime;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		mEnableReload = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.epg));

		mReference = getArguments().getString(Event.KEY_SERVICE_REFERENCE);
		mName = getArguments().getString(Event.KEY_SERVICE_NAME);
		mTime = getArguments().getInt(Event.KEY_EVENT_START);
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

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		checkMenuReload(menu, inflater);
		inflater.inflate(R.menu.servicelist, menu);
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode != Activity.RESULT_OK)
			return;
		switch(requestCode){
			case Statics.REQUEST_PICK_BOUQUET:
				ExtendedHashMap service = data.getParcelableExtra(PickServiceFragment.KEY_BOUQUET);
				String reference = service.getString(Service.KEY_REFERENCE);
				if(!mReference.equals(reference)) {
					mReference = reference;
					mName = service.getString(Service.KEY_NAME);
					if(Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR_MR1)
						getListView().smoothScrollToPosition(0);
				}
				reload();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(getActionBarActivity(), mMapList, R.layout.epg_multi_service_list_item, new String[] {
				Event.KEY_EVENT_TITLE, Event.KEY_SERVICE_NAME, Event.KEY_EVENT_DESCRIPTION_EXTENDED, Event.KEY_EVENT_START_READABLE,
				Event.KEY_EVENT_DURATION_READABLE }, new int[] { R.id.event_title, R.id.service_name, R.id.event_short, R.id.event_start,
				R.id.event_duration });
		setListAdapter(mAdapter);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("bRef", mReference));
		params.add(new BasicNameValuePair("time", Integer.toString(mTime)));
		return params;
	}

	@Override
	public String getLoadFinishedTitle() {
		return getBaseTitle() + " - " + mName;
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getActionBarActivity(), new EventListRequestHandler(URIStore.EPG_BOUQUET), false, args);
		return loader;
	}

	@Override
	protected boolean onItemSelected(int id) {
		switch (id) {
			case R.id.menu_overview:
				pickBouquet();
				return true;
		}
		return super.onItemSelected(id);
	}

	private void pickBouquet() {
		PickServiceFragment f = new PickServiceFragment();
		Bundle args = new Bundle();

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Service.KEY_REFERENCE, "default");

		args.putSerializable(sData, data);
		args.putString("action", Statics.INTENT_ACTION_PICK_BOUQUET);

		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_PICK_BOUQUET);
		((MultiPaneHandler) getActionBarActivity()).showDetails(f, true);
	}
}
