package net.reichholf.dreamdroid.tv.fragment;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.content.ContextCompat;
import androidx.loader.content.Loader;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.ProfileChangedListener;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EpgNowNextListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.tv.BrowseItem;
import net.reichholf.dreamdroid.tv.activities.PreferenceActivity;
import net.reichholf.dreamdroid.tv.fragment.abs.BaseHttpBrowseFragment;
import net.reichholf.dreamdroid.tv.presenter.CardPresenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Created by Stephan on 16.10.2016.
 */

public class RootBrowseFragment extends BaseHttpBrowseFragment implements ProfileChangedListener {

	public static final int LOADER_BOUQUETLIST_ID = 1;
	public static final int LOADER_SERVICELIST_ID = 2;
	public static final int LOADER_MOVIELIST_ID = 3;

	public static final int REQUEST_CODE_PROFILE = 0x1337;

	public static final int ROW_SETTINGS_ID = 65535;
	public static final int ROW_MOVIE_OFFSET_ID = 0x1000;

	public static String BOUQUETS_TV = "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) FROM BOUQUET \\\"bouquets.tv\\\" ORDER BY bouquet";

	boolean mRequireReload;

	ArrayList<ArrayObjectAdapter> mListRowAdapters = new ArrayList<>();

	ArrayList<ExtendedHashMap> mBouquets = null;
	ArrayList<ExtendedHashMap> mBouquetQueue = null;

	HashMap<String, ArrayList<ExtendedHashMap>> mLocations;
	ListRow mLoadingLocation = null;

	ExtendedHashMap mLoadingBouquet;
	ExtendedHashMap mSelectedBouquet;
	ExtendedHashMap mSelectedService;

	private class BouquetHeaderItem extends HeaderItem {
		private ExtendedHashMap mBouquet;

		public BouquetHeaderItem(long id, ExtendedHashMap bouquet) {
			super(id, bouquet.getString(Service.KEY_NAME));
			mBouquet = bouquet;
		}

		public ExtendedHashMap bouquet() {
			return mBouquet;
		}
	}

	private class SettingsRow extends ListRow {
		public SettingsRow(HeaderItem header, ObjectAdapter adapter) {
			super(header, adapter);
		}
	}

	private class ServiceRow extends ListRow {
		public ServiceRow(HeaderItem header, ObjectAdapter adapter) {
			super(header, adapter);
		}
	}

	private class MovieRow extends ListRow {
		public MovieRow(HeaderItem header, ObjectAdapter adapter) {
			super(header, adapter);
		}
	}

	public RootBrowseFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setRetainInstance(true);
		super.onCreate(savedInstanceState);
		setOnItemViewClickedListener(this);
		setOnItemViewSelectedListener(this);
		if (mBouquets == null)
			mBouquets = new ArrayList<>();
		if (mBouquetQueue == null)
			mBouquetQueue = new ArrayList<>();
		if (mLocations == null);
		 	mLocations = new HashMap<>();

		setHeadersState(HEADERS_ENABLED);
		setBrandColor(ContextCompat.getColor(getContext(), R.color.primary_dreamdroid));
		setBadgeDrawable(ContextCompat.getDrawable(getContext(), R.drawable.dreamdroid_header));
		addSettingsRow();
		mRequireReload = true;
	}

	@Override
	public void onPause() {
		super.onPause();
		getLoaderManager().destroyLoader(LOADER_SERVICELIST_ID);
		getLoaderManager().destroyLoader(LOADER_BOUQUETLIST_ID);
	}

	@Override
	public void onResume() {
		super.onResume();
		mRequireReload = mRequireReload || mBouquets == null || mBouquets.isEmpty() || mLocations == null || mLocations.isEmpty();
		if (mRequireReload)
			load();
	}

	@Override
	public void onStart() {
		super.onStart();
		DreamDroid.setCurrentProfileChangedListener(this);
	}

	@Override
	public void onStop() {
		DreamDroid.setCurrentProfileChangedListener(null);
		super.onStop();
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AbstractListRequestHandler handler = null;
		switch(id) {
		case LOADER_BOUQUETLIST_ID:
			handler = new ServiceListRequestHandler();
			break;
		case LOADER_SERVICELIST_ID:
			if (DreamDroid.featureNowNext())
				handler = new EpgNowNextListRequestHandler();
			else
				handler = new EventListRequestHandler(URIStore.EPG_NOW);
			break;
		case LOADER_MOVIELIST_ID:
			handler = new MovieListRequestHandler();
		}
		if (handler != null)
			return new AsyncListLoader(getActivity(), handler, true, args);
		return null;
	}

	@Override
	public void onLoadFinished(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader, LoaderResult<ArrayList<ExtendedHashMap>> data) {
		if (data.isError()) {
			Toast.makeText(getContext(), data.getErrorText(), Toast.LENGTH_LONG).show();
			return;
		}
		switch(loader.getId()) {
			case LOADER_BOUQUETLIST_ID:
				onLoadBouquetsFinished(data.getResult());
				break;
			case LOADER_SERVICELIST_ID:
				onLoadServicesFinished(data.getResult());
				break;
			case LOADER_MOVIELIST_ID:
				onLoadMoviesFinished(data.getResult());
		}

	}

	protected void load() {
		mRequireReload = false;
		resetRows();
		Toast.makeText(getContext(), R.string.loading, Toast.LENGTH_LONG).show();
		mBouquetQueue.clear();
		mBouquets.clear();
		mLocations.clear();
		mListRowAdapters.clear();
		mLoadingBouquet = null;
		mLoadingLocation = null;
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("bRef", BOUQUETS_TV));
		Bundle args = new Bundle();
		args.putSerializable("params", params);
		load(LOADER_BOUQUETLIST_ID, args);
	}

	protected void reload() {

	}

	protected void load(int loader, Bundle args) {
		getLoaderManager().restartLoader(loader, args, this);
	}


	protected void resetRows() {
		//Remove everything but settings
		mRowsAdapter.removeItems(0, mRowsAdapter.size() - 1);
	}

	protected void addSettingsRow() {
		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter(CardPresenter.ItemMode.MODE_IMAGE));

		ExtendedHashMap reload = new ExtendedHashMap();
		reload.put("title", getString(R.string.reload));
		reload.put("icon", R.drawable.ic_badge_reload);
		listRowAdapter.add(new BrowseItem(BrowseItem.Type.Reload, reload));

		ExtendedHashMap preferences = new ExtendedHashMap();
		preferences.put("title", getString(R.string.settings));
		preferences.put("icon", R.drawable.ic_badge_settings);
		listRowAdapter.add(new BrowseItem(BrowseItem.Type.Preferences, preferences));

		ExtendedHashMap profile = new ExtendedHashMap();
		profile.put("title", getString(R.string.profile));
		profile.put("icon", R.drawable.ic_badge_profiles);
		listRowAdapter.add(new BrowseItem(BrowseItem.Type.Profile, profile));

		HeaderItem header = new HeaderItem(ROW_SETTINGS_ID, getString(R.string.preferences));
		mRowsAdapter.add(new SettingsRow(header, listRowAdapter));
	}

	public void onLoadBouquetsFinished(ArrayList<ExtendedHashMap> bouquets) {
		mBouquets.addAll(bouquets);
		mBouquetQueue.addAll(bouquets);

		for(String location : DreamDroid.getLocations()) {
			mLocations.put(location, new ArrayList<>());
		}

		Collections.reverse(mBouquetQueue);
		loadNextBouquet();
	}

	public void loadNextBouquet() {
		if (mBouquetQueue.isEmpty()) {
			setSelectedPosition(0);
			addLocations();
			return;
		}
		mLoadingBouquet = mBouquetQueue.get(0);
		mBouquetQueue.remove(0);
		ArrayList<NameValuePair> params = new ArrayList<>();
		String ref = mLoadingBouquet.getString(Service.KEY_REFERENCE);
		params.add(new NameValuePair("bRef", ref));
		Bundle args = new Bundle();
		args.putSerializable("params", params);
		load(LOADER_SERVICELIST_ID, args);
	}

	protected void addLocations() {
		for(String location : mLocations.keySet()) {
			ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter(CardPresenter.ItemMode.MODE_TEXT));
			HeaderItem header = new HeaderItem(ROW_MOVIE_OFFSET_ID + mRowsAdapter.size(), location);
			//Insert before prefs
			mRowsAdapter.add(mRowsAdapter.size() - 1, new MovieRow(header, listRowAdapter));
			mListRowAdapters.add(listRowAdapter);
		}
	}

	public void onLoadServicesFinished(ArrayList<ExtendedHashMap> services) {
		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter(CardPresenter.ItemMode.MODE_IMAGE));
		for (ExtendedHashMap service : services) {
			listRowAdapter.add(new BrowseItem(BrowseItem.Type.Service, service));
		}
		HeaderItem header = new BouquetHeaderItem(mRowsAdapter.size(), mLoadingBouquet);
		mRowsAdapter.add(0, new ServiceRow(header, listRowAdapter));
		mListRowAdapters.add(listRowAdapter);
		loadNextBouquet();
	}

	protected void onLoadMoviesFinished(ArrayList<ExtendedHashMap> movies) {
		if (mLoadingLocation == null)
			return;
		ListRow listRow = (ListRow) mRowsAdapter.get( mRowsAdapter.indexOf(mLoadingLocation) );
		ArrayObjectAdapter listRowAdapter = (ArrayObjectAdapter) listRow.getAdapter();
		listRowAdapter.clear();
		ArrayList<ExtendedHashMap> locs = mLocations.get(mLoadingLocation.getHeaderItem().getName());
		for (ExtendedHashMap movie : movies) {
			listRowAdapter.add(new BrowseItem(BrowseItem.Type.Movie, movie));
			locs.add(movie);
		}
	}



	@Override
	public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object it, RowPresenter.ViewHolder rowViewHolder, Row row) {
		BrowseItem item = (BrowseItem) it;
		switch (item.type) {
			case Reload:
				load();
				break;
			case Preferences:
			case Profile:
				{
					String type = item.type == BrowseItem.Type.Preferences ? SettingsFragment.PREFS_TYPE_GENERIC : SettingsFragment.PREFS_TYPE_PROFILE;
					Intent intent = new Intent(getContext(), PreferenceActivity.class);
					intent.putExtra(SettingsFragment.KEY_PREFS_TYPE, type);
					startActivityForResult(intent, REQUEST_CODE_PROFILE);
					break;
				}
			case Movie:
				{
					String ref = item.data.getString(Movie.KEY_REFERENCE);
					String filename = item.data.getString(Movie.KEY_FILE_NAME);
					String title = item.data.getString(Movie.KEY_TITLE);
					startActivity(IntentFactory.getStreamFileIntent(getActivity(), ref, filename, title, item.data));
					break;
				}
			case Service:
				{
					String ref = item.data.getString(Event.KEY_SERVICE_REFERENCE);
					String name = item.data.getString(Event.KEY_EVENT_TITLE);
					startActivity(IntentFactory.getStreamServiceIntent(getActivity(), ref, name, mSelectedBouquet.getString(Event.KEY_SERVICE_REFERENCE, null), item.data));
					break;
				}
		}
	}

	@Override
	public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object it, RowPresenter.ViewHolder rowViewHolder, Row row) {
		mLoadingLocation = null;
		mSelectedService = null;
		mSelectedBouquet = null;

		if (row instanceof MovieRow) {
			String dirname = row.getHeaderItem().getName();
			if (mLocations.get(dirname).isEmpty()) {
				ArrayList<NameValuePair> params = new ArrayList<>();
				params.add(new NameValuePair("dirname", dirname));
				Bundle args = new Bundle();
				args.putSerializable("params", params);
				mLoadingLocation = (ListRow) row;
				if (mLoadingLocation != null)
					load(LOADER_MOVIELIST_ID, args);
			}
			return;
		} else if (row instanceof ServiceRow) {
			BrowseItem item = (BrowseItem) it;
			mSelectedService = item != null ? item.data : null;
			BouquetHeaderItem headerItem = (BouquetHeaderItem) row.getHeaderItem();
			mSelectedBouquet = headerItem.bouquet();
		}
	}

	@Override
	public void onProfileChanged(Profile p) {
		if(isResumed())
			load();
		else
			mRequireReload = true;
	}
}

