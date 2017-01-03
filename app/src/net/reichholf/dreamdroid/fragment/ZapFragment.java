package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.adapter.recyclerview.ZapAdapter;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.RecyclerViewPauseOnScrollListener;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;


/**
 * Created by reichi on 8/30/13.
 * This fragment is actually based on a GridView, it uses some small hacks to trick the ListFragment into working anyways
 * As a GridView is also using a ListAdapter, this avoids having to copy existing code
 */

public class ZapFragment extends BaseHttpRecyclerFragment {
	public static final String BUNDLE_KEY_BOUQUETLIST = "bouquetList";
	public static String BUNDLE_KEY_CURRENT_BOUQUET = "currentBouquet";

	private ArrayList<ExtendedHashMap> mBouquetList;
	private ExtendedHashMap mCurrentBouquet;
	private boolean mWaitingForPicker;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mEnableReload = false;
		super.onCreate(savedInstanceState);
		initTitle("");

		mBouquetList = new ArrayList<>();
		mCurrentBouquet = new ExtendedHashMap();
		mCurrentBouquet.put(Service.KEY_REFERENCE, DreamDroid.getCurrentProfile().getDefaultRef());
		mCurrentBouquet.put(Service.KEY_NAME, DreamDroid.getCurrentProfile().getDefaultRefName());
		mWaitingForPicker = false;

		restoreState(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.card_grid_content, container, false);

		RecyclerView recyclerView = (RecyclerView) view.findViewById(android.R.id.list);
		recyclerView.setLayoutManager(new GridLayoutManager(getAppCompatActivity(), 3));
		RecyclerViewPauseOnScrollListener listener = new RecyclerViewPauseOnScrollListener(Statics.TAG_PICON, true, true);
		recyclerView.addOnScrollListener(listener);

		restoreState(savedInstanceState);
		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
	}

	private void restoreState(Bundle savedInstanceState) {
		boolean reload = false;
		if (savedInstanceState == null) {
			mReload = true;
		} else {
			ExtendedHashMap currentBouquet = ExtendedHashMapHelper.restoreFromBundle(savedInstanceState, BUNDLE_KEY_CURRENT_BOUQUET);
			if (currentBouquet != null)
				mCurrentBouquet = currentBouquet;
			else
				mReload = true;

			ArrayList<ExtendedHashMap> bouquetList = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_BOUQUETLIST);
			if (bouquetList != null)
				mBouquetList = bouquetList;
			else
				mReload = true;
		}

		if (reload)
			mReload = true;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new ZapAdapter(getContext(), mMapList);
		getRecyclerView().setAdapter(mAdapter);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable(BUNDLE_KEY_CURRENT_BOUQUET, mCurrentBouquet);
		outState.putSerializable(BUNDLE_KEY_BOUQUETLIST, mBouquetList);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.epgbouquet, menu);
	}


	@Override
	public void onItemClick(RecyclerView rv, View v, int position, long id) {
		String ref = mMapList.get(position).getString(Service.KEY_REFERENCE);
		zapTo(ref);
	}

	@Override
	public boolean onItemLongClick(RecyclerView rv, View v, int position, long id) {
		String ref = mMapList.get(position).getString(Service.KEY_REFERENCE);
		String name = mMapList.get(position).getString(Service.KEY_NAME);
		try {
			startActivity(IntentFactory.getStreamServiceIntent(getAppCompatActivity(), ref, name));
		} catch (ActivityNotFoundException e) {
			showToast(getText(R.string.missing_stream_player));
		}
		return true;
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int i, Bundle bundle) {
		return new AsyncListLoader(getAppCompatActivity(), new ServiceListRequestHandler(), false, bundle);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("sRef", mCurrentBouquet.getString(Service.KEY_REFERENCE)));

		return params;
	}

	@Override
	protected void reload() {
		if(mCurrentBouquet != null && !mCurrentBouquet.isEmpty())
			super.reload();
		else if(!mWaitingForPicker)
			pickBouquet();
	}

	@Override
	public String getLoadFinishedTitle() {
		if(mCurrentBouquet != null)
			return mCurrentBouquet.getString(Service.KEY_NAME, super.getLoadFinishedTitle());
		return super.getLoadFinishedTitle();
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {

		mMapList.clear();
		mAdapter.notifyDataSetChanged();
		if (result.isError()) {
			setEmptyText(result.getErrorText());
			return;
		}
		setEmptyText(null);

		ArrayList<ExtendedHashMap> list = result.getResult();
		setCurrentTitle(getLoadFinishedTitle());
		getAppCompatActivity().setTitle(getCurrentTitle());

		if (list.size() == 0) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			for (ExtendedHashMap service : list) {
				if (!Service.isMarker(service.getString(Service.KEY_REFERENCE)))
					mMapList.add(service);
			}
		}
		mAdapter.notifyDataSetChanged();
		mHttpHelper.onLoadFinished();
	}

	@Override
	protected boolean onItemSelected(int id) {
		switch (id) {
			case R.id.menu_pick_bouquet:
				pickBouquet();
				return true;
		}
		return super.onItemSelected(id);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != Activity.RESULT_OK)
			return;
		switch (requestCode) {
			case Statics.REQUEST_PICK_BOUQUET:
				ExtendedHashMap bouquet = data.getParcelableExtra(PickServiceFragment.KEY_BOUQUET);
				String reference = bouquet.getString(Service.KEY_REFERENCE, "");
				if (!reference.equals(mCurrentBouquet.getString(Service.KEY_REFERENCE))) {
					mCurrentBouquet = bouquet;
					getRecyclerView().smoothScrollToPosition(0);
				}
				reload();
				mWaitingForPicker = false;
				break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void pickBouquet() {
		mWaitingForPicker = true;
		PickServiceFragment f = new PickServiceFragment();
		Bundle args = new Bundle();

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Service.KEY_REFERENCE, "default");

		args.putSerializable(sData, data);
		args.putString("action", Statics.INTENT_ACTION_PICK_BOUQUET);

		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_PICK_BOUQUET);
		((MultiPaneHandler) getAppCompatActivity()).showDetails(f, true);
	}
}
