package net.reichholf.dreamdroid.tv.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import net.reichholf.dreamdroid.tv.activities.PreferenceActivity;
import net.reichholf.dreamdroid.tv.fragment.abs.BaseHttpBrowseFragment;
import net.reichholf.dreamdroid.tv.presenter.CardPresenter;

import java.util.ArrayList;
import java.util.Collections;

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

	ArrayList<String> mLocations;
	ArrayList<String> mLocationQeue;
	ListRow mLoadingLocation = null;

	ExtendedHashMap mLoadingBouquet;
	ExtendedHashMap mSelectedBouquet;
	ExtendedHashMap mSelectedService;

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
		 	mLocations = new ArrayList<>();
		if (mLocationQeue == null)
			mLocationQeue = new ArrayList<>();
		setHeadersState(HEADERS_ENABLED);

		setBrandColor(ContextCompat.getColor(getContext(), R.color.primary_dreamdroid));
		setBadgeDrawable(ContextCompat.getDrawable(getContext(), R.drawable.dreamdroid_banner));
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
		if (mRequireReload)
			load();
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
		mLocationQeue.clear();
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

	protected void load(int loader, Bundle args) {
		getLoaderManager().restartLoader(loader, args, this);
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

		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter(CardPresenter.ItemMode.MODE_SETTINGS));
		listRowAdapter.add(settings);
		listRowAdapter.add(profile);

		HeaderItem header = new HeaderItem(ROW_SETTINGS_ID, getString(R.string.preferences));
		mRowsAdapter.add(new ListRow(header, listRowAdapter));
	}

	public void onLoadBouquetsFinished(ArrayList<ExtendedHashMap> bouquets) {
		mBouquets.addAll(bouquets);
		mBouquetQueue.addAll(bouquets);

		mLocations.addAll(DreamDroid.getLocations());

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
		for(String location : mLocations) {
			ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter(CardPresenter.ItemMode.MODE_MOVIES));
			HeaderItem header = new HeaderItem(ROW_MOVIE_OFFSET_ID + mRowsAdapter.size(), location);
			//Insert before prefs
			mRowsAdapter.add(mRowsAdapter.size() - 1, new ListRow(header, listRowAdapter));
			mListRowAdapters.add(listRowAdapter);
			mLocationQeue.add(location);
		}
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

	protected void onLoadMoviesFinished(ArrayList<ExtendedHashMap> movies) {
		ListRow listRow = (ListRow) mRowsAdapter.get( mRowsAdapter.indexOf(mLoadingLocation) );
		ArrayObjectAdapter listRowAdapter = (ArrayObjectAdapter) listRow.getAdapter();
		listRowAdapter.clear();
		for (ExtendedHashMap movie : movies) {
			listRowAdapter.add(movie);
		}
		mLocationQeue.remove( listRow.getHeaderItem().getName() );

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
		} else if (isMovieRow(row)) {
			ExtendedHashMap movie = (ExtendedHashMap) item;
			String ref = movie.getString(Movie.KEY_REFERENCE);
			String filename = movie.getString(Movie.KEY_FILE_NAME);
			String title = movie.getString(Movie.KEY_TITLE);
			startActivity(IntentFactory.getStreamFileIntent(getActivity(), ref, filename, title, movie));
			return;
		}

		ExtendedHashMap event = (ExtendedHashMap) item;
		String ref = event.getString(Event.KEY_SERVICE_REFERENCE);
		String name = event.getString(Event.KEY_EVENT_TITLE);
		startActivity(IntentFactory.getStreamServiceIntent(getActivity(), ref, name, mSelectedBouquet.getString(Event.KEY_SERVICE_REFERENCE, null), event));
	}

	protected boolean isMovieRow(Row row) {
		return row.getId() >= ROW_MOVIE_OFFSET_ID;
	}

	@Override
	public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
		if (isSettingsRow(row)) {
			mSelectedService = null;
			mSelectedBouquet = null;
			return;
		}
		if (isMovieRow(row)) {
			String dirname = row.getHeaderItem().getName();
			if (mLocationQeue.indexOf(dirname) >= 0) {
				ArrayList<NameValuePair> params = new ArrayList<>();
				params.add(new NameValuePair("dirname", dirname));
				Bundle args = new Bundle();
				args.putSerializable("params", params);
				mLoadingLocation = (ListRow) row;
				if (mLoadingLocation != null)
					load(LOADER_MOVIELIST_ID, args);
			}
			return;
		}
		mSelectedService = (ExtendedHashMap) item;
		long index = row.getHeaderItem().getId();

		if (index >= 0 && index < mBouquets.size())
			mSelectedBouquet = mBouquets.get((int) index);
		else
			mSelectedBouquet = new ExtendedHashMap();
	}

	@Override
	public void onProfileChanged(Profile p) {
		if(isResumed())
			load();
		else
			mRequireReload = true;
	}
}
