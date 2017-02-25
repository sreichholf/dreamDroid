package net.reichholf.dreamdroid.tv.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EpgNowNextListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.tv.activities.PreferenceActivity;
import net.reichholf.dreamdroid.tv.fragment.abs.BaseHttpBrowseFragment;
import net.reichholf.dreamdroid.tv.presenter.CardPresenter;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by Stephan on 16.10.2016.
 */

public class RootBrowseFragment extends BaseHttpBrowseFragment {
	public static int LOADER_BOUQUETLIST_ID = 1;
	public static int REQUEST_CODE_PROFILE;
	public static int ROW_SETTINGS_ID = 65535;

	public static String BOUQUETS_TV = "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) FROM BOUQUET \\\"bouquets.tv\\\" ORDER BY bouquet";

	ArrayList<ArrayObjectAdapter> mListRowAdapters = new ArrayList<>();

	ArrayList<ExtendedHashMap> mBouquets = null;
	ArrayList<ExtendedHashMap> mBouquetQueue = null;

	ExtendedHashMap mLoadingBouquet;
	ExtendedHashMap mSelectedBouquet;
	ExtendedHashMap mSelectedService;

	public RootBrowseFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setRetainInstance(false);
		super.onCreate(savedInstanceState);
		setOnItemViewClickedListener(this);
		setOnItemViewSelectedListener(this);
		if (mBouquets == null)
			mBouquets = new ArrayList<>();
		if (mBouquetQueue == null)
			mBouquetQueue = new ArrayList<>();
		setHeadersState(HEADERS_ENABLED);

		setBrandColor(ContextCompat.getColor(getContext(), R.color.primary_dreamdroid));
		setBadgeDrawable(ContextCompat.getDrawable(getContext(), R.drawable.dreamdroid_banner));
		addSettingsRow();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		load();
	}

	@Override
	public void onPause() {
		super.onPause();
		getLoaderManager().destroyLoader(LOADER_DEFAULT_ID);
		getLoaderManager().destroyLoader(LOADER_BOUQUETLIST_ID);
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AbstractListRequestHandler handler;
		if (id == LOADER_BOUQUETLIST_ID) {
			handler = new ServiceListRequestHandler();
		} else {
			if (DreamDroid.featureNowNext())
				handler = new EpgNowNextListRequestHandler();
			else
				handler = new EventListRequestHandler(URIStore.EPG_NOW);
		}
		return new AsyncListLoader(getActivity(), handler, true, args);
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader, LoaderResult<ArrayList<ExtendedHashMap>> data) {
		if (data.isError()) {
			Toast.makeText(getContext(), data.getErrorText(), Toast.LENGTH_LONG).show();
			return;
		}
		if (loader.getId() == LOADER_BOUQUETLIST_ID) {
			onLoadBouquetsFinished(data.getResult());
		} else
			onLoadServicesFinished(data.getResult());
	}

	protected void resetRows() {
		//Remove everything but settings
		mRowsAdapter.removeItems(0, mRowsAdapter.size() - 1);
	}

	protected void addSettingsRow() {
		ExtendedHashMap settings = new ExtendedHashMap();
		settings.put("title", getString(R.string.settings));
		settings.put("icon", R.drawable.ic_settings_badge);
		settings.put(SettingsFragment.KEY_PREFS_TYPE, SettingsFragment.PREFS_TYPE_GENERIC);

		ExtendedHashMap profile = new ExtendedHashMap();
		profile.put("title", getString(R.string.profile));
		profile.put("icon", R.drawable.ic_profiles_badge);
		profile.put(SettingsFragment.KEY_PREFS_TYPE, SettingsFragment.PREFS_TYPE_PROFILE);

		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter(true));
		listRowAdapter.add(settings);
		listRowAdapter.add(profile);

		HeaderItem header = new HeaderItem(ROW_SETTINGS_ID, getString(R.string.preferences));
		mRowsAdapter.add(new ListRow(header, listRowAdapter));
	}

	protected void load() {
		resetRows();
		Toast.makeText(getContext(), R.string.loading, Toast.LENGTH_LONG).show();
		mBouquetQueue.clear();
		mBouquets.clear();
		mListRowAdapters.clear();
		mLoadingBouquet = null;
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("bRef", BOUQUETS_TV));
		Bundle args = new Bundle();
		args.putSerializable("params", params);
		load(LOADER_BOUQUETLIST_ID, args);
	}

	protected void load(int loader, Bundle args) {
		getLoaderManager().restartLoader(loader, args, this);
	}

	public void onLoadBouquetsFinished(ArrayList<ExtendedHashMap> bouquets) {
		mBouquets.addAll(bouquets);
		mBouquetQueue.addAll(bouquets);
		Collections.reverse(mBouquetQueue);
		loadNextBouquet();
	}

	public void loadNextBouquet() {
		if (mBouquetQueue.isEmpty()) {
			setSelectedPosition(0);
			return;
		}
		mLoadingBouquet = mBouquetQueue.get(0);
		mBouquetQueue.remove(0);
		ArrayList<NameValuePair> params = new ArrayList<>();
		String ref = mLoadingBouquet.getString(Service.KEY_REFERENCE);
		params.add(new NameValuePair("bRef", ref));
		Bundle args = new Bundle();
		args.putSerializable("params", params);
		load(LOADER_DEFAULT_ID, args);
	}

	public void onLoadServicesFinished(ArrayList<ExtendedHashMap> services) {
		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
		for (ExtendedHashMap service : services) {
			listRowAdapter.add(service);
		}
		HeaderItem header = new HeaderItem(mRowsAdapter.size(), mLoadingBouquet.getString(Service.KEY_NAME));
		mRowsAdapter.add(0, new ListRow(header, listRowAdapter));
		mListRowAdapters.add(listRowAdapter);
		loadNextBouquet();
	}

	protected boolean isSettingsRow(Row row) {
		return row.getHeaderItem().getId() == ROW_SETTINGS_ID;
	}

	@Override
	public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
		if (isSettingsRow(row)) {
			ExtendedHashMap it = (ExtendedHashMap) item;
			String type = it.getString(SettingsFragment.KEY_PREFS_TYPE);
			Intent intent = new Intent(getContext(), PreferenceActivity.class);
			intent.putExtra(SettingsFragment.KEY_PREFS_TYPE, type);
			startActivityForResult(intent, REQUEST_CODE_PROFILE);
			return;
		}
		ExtendedHashMap event = (ExtendedHashMap) item;
		String ref = event.getString(Event.KEY_SERVICE_REFERENCE);
		String name = event.getString(Event.KEY_EVENT_TITLE);
		startActivity(IntentFactory.getStreamServiceIntent(getActivity(), ref, name, mSelectedBouquet.getString(Event.KEY_SERVICE_REFERENCE, null), event));
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (REQUEST_CODE_PROFILE == requestCode)
			load();
		else
			super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
		if (isSettingsRow(row)) {
			mSelectedService = null;
			mSelectedBouquet = null;
			return;
		}
		mSelectedService = (ExtendedHashMap) item;
		long index = row.getHeaderItem().getId();

		if (index >= 0 && index < mBouquets.size())
			mSelectedBouquet = mBouquets.get((int) index);
		else
			mSelectedBouquet = new ExtendedHashMap();
	}
}
