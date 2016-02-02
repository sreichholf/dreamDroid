package net.reichholf.dreamdroid.fragment;

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.ZapAdapter;
import net.reichholf.dreamdroid.asynctask.GetBouquetListTask;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.RecyclerViewPauseOnScrollListener;
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

public class ZapFragment extends BaseHttpRecyclerFragment implements GetBouquetListTask.GetBoquetListTaskHandler {
	public static final String BUNDLE_KEY_BOUQUETLIST = "bouquetList";
	public static String BUNDLE_KEY_CURRENT_BOUQUET = "currentBouquet";

	private GetBouquetListTask mGetBouquetListTask;
	private ArrayList<ExtendedHashMap> mBouquetList;
	private ArrayAdapter<String> mBouquetListAdapter;
	private ExtendedHashMap mCurrentBouquet;
	private int mSelectedBouquetPosition;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitle("");

		mBouquetList = new ArrayList<>();
		mCurrentBouquet = new ExtendedHashMap();
		mCurrentBouquet.put(Service.KEY_REFERENCE, DreamDroid.getCurrentProfile().getDefaultRef());
		mCurrentBouquet.put(Service.KEY_NAME, DreamDroid.getCurrentProfile().getDefaultRefName());

		restoreState(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.card_grid_content, container, false);

		RecyclerViewPauseOnScrollListener listener = new RecyclerViewPauseOnScrollListener(ImageLoader.getInstance(), true, true);
		RecyclerView recyclerView = (RecyclerView) view.findViewById(android.R.id.list);
		recyclerView.setOnScrollListener(listener);

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
	public void onResume() {
		setupListNavigation();
		super.onResume();
	}

	@Override
	public void onPause() {
		if (mGetBouquetListTask != null)
			mGetBouquetListTask.cancel(true);
		mGetBouquetListTask = null;
		getAppCompatActivity().getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		super.onPause();
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
			startActivity(IntentFactory.getStreamServiceIntent(ref, name));
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
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {

		if (mGetBouquetListTask != null) {
			mGetBouquetListTask.cancel(true);
			mGetBouquetListTask = null;
		}
		mGetBouquetListTask = new GetBouquetListTask(this);
		mGetBouquetListTask.execute();

		mMapList.clear();
		mAdapter.notifyDataSetChanged();
		if (result.isError()) {
			setEmptyText(result.getErrorText());
			return;
		}

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
	public void setEmptyText(CharSequence text) {
		TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
		if (emptyView != null) {
			emptyView.setText(text);
			emptyView.setVisibility(View.GONE);
		}
	}

	public void setupListNavigation() {
		ActionBar actionBar = getAppCompatActivity().getSupportActionBar();
		if (actionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_LIST)
			return;
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		mBouquetListAdapter = new ArrayAdapter<>(actionBar.getThemedContext(),
				R.layout.support_simple_spinner_dropdown_item);

		actionBar.setListNavigationCallbacks(mBouquetListAdapter, new ActionBar.OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {
				mSelectedBouquetPosition = itemPosition;
				if (mBouquetList.size() > itemPosition) {
					String selectedBouquet = mBouquetListAdapter.getItem(itemPosition);
					if (mCurrentBouquet == null || !selectedBouquet.equals(mCurrentBouquet.getString(Service.KEY_NAME))) {
						mCurrentBouquet = mBouquetList.get(itemPosition);
						reload();
					}
				}
				return true;
			}
		});
		ArrayList<ExtendedHashMap> list = new ArrayList<>(mBouquetList);
		applyBouquetList(list);
		mBouquetListAdapter.notifyDataSetChanged();
	}

	public void onBouquetListReady(boolean result, ArrayList<ExtendedHashMap> list, String errorText) {
		if (result)
			applyBouquetList(list);
		else
			showToast(errorText);
	}

	private void applyBouquetList(ArrayList<ExtendedHashMap> list) {
		mBouquetListAdapter.clear();
		mBouquetList.clear();

		String defaultRef = DreamDroid.getCurrentProfile().getDefaultRef();
		boolean isDefaultMissing = true;

		int position = mSelectedBouquetPosition = 0;
		for (ExtendedHashMap service : list) {
			mBouquetList.add(service);
			mBouquetListAdapter.add(service.getString(Service.KEY_NAME));
			if (defaultRef != null && !"".equals(defaultRef) && service.getString(Service.KEY_REFERENCE).equals(defaultRef))
				isDefaultMissing = false;
			if (service.getString(Service.KEY_REFERENCE).equals(mCurrentBouquet.getString(Service.KEY_REFERENCE)))
				mSelectedBouquetPosition = position;
			position++;
		}
		if (isDefaultMissing) {
			addDefaultBouquetToList();
		}
		getAppCompatActivity().getSupportActionBar().setSelectedNavigationItem(mSelectedBouquetPosition);
		mBouquetListAdapter.notifyDataSetChanged();
	}

	private void addDefaultBouquetToList() {
		ExtendedHashMap defaultBouquet = new ExtendedHashMap();
		String defaultRef = DreamDroid.getCurrentProfile().getDefaultRef();
		if ("".equals(defaultRef))
			return;
		defaultBouquet.put(Service.KEY_REFERENCE, defaultRef);
		defaultBouquet.put(Service.KEY_NAME, DreamDroid.getCurrentProfile().getDefaultRefName());
		mBouquetList.add(0, defaultBouquet);
		mBouquetListAdapter.insert(defaultBouquet.getString(Service.KEY_NAME), 0);
	}
}
