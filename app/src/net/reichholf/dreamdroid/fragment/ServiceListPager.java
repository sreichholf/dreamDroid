package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.BouquetListLoadKt;
import net.reichholf.dreamdroid.enigma.Bouquets;
import net.reichholf.dreamdroid.enigma.LocationsAndTagsLoadKt;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination;
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState;
import net.reichholf.dreamdroid.ui.services.TvMoviesHubStateKt;

import java.util.ArrayList;

import kotlin.Unit;
import kotlinx.coroutines.Job;

public class ServiceListPager extends BaseHttpFragment {
	private static final String MODE_TV = "TV";
	private static final String MODE_RADIO = "Radio";
	private static final String MODE_MOVIES = "Movies";
	private static final String MODE_TIMER = "Timer";

	private static final String KEY_MODE = "hub_mode";
	private static final String KEY_CURRENT_TV = "hub_current_tv";
	private static final String KEY_CURRENT_RADIO = "hub_current_radio";
	private static final String KEY_CURRENT_MOVIE = "hub_current_movie";

	public String mMode;
	public String mCurrentTv;
	public String mCurrentRadio;
	public String mCurrentMovie;

	ViewPager2 mPager;

	ServicelistAdapter mTvListAdapter;
	ServicelistAdapter mRadioListAdapter;
	MovieListAdapter mMovielistAdapter;
	TimerListAdapter mTimerListAdapter;

	TvMoviesHubState mHubState;
	@Nullable
	private Job mBouquetLoadJob;
	@Nullable
	private Job mLocationsAndTagsJob;
	@Nullable
	private ComposeView mDestinationNav;

	@Nullable
	private Bouquets mBouquets;

	private void onLocationsAndTagsReady() {
		if (mMode.equals((MODE_MOVIES)))
			onMoviesSelected();
	}

	public class ServicelistAdapter extends FragmentStateAdapter {
		ArrayList<Service> mItems;

		public ServicelistAdapter(@NonNull Fragment fragment) {
			super(fragment);
			mItems = new ArrayList();
		}

		public Service get(int i) {
			return mItems.get(i);
		}

		public void clear() {
			mItems.clear();
		}

		public void add(Service e) {
			mItems.add(e);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position) {
			Fragment f = new ServiceListPageFragment();

			Bundle args = new Bundle();
			Service service = mItems.get(position);
			args.putString(Event.KEY_SERVICE_REFERENCE, service.getReference());
			args.putString(Event.KEY_SERVICE_NAME, service.getName());
			f.setArguments(args);
			return f;
		}

		@Override
		public int getItemCount() {
			if (mItems != null)
				return mItems.size();
			return 0;
		}

		public int indexOf(@NonNull String ref) {
			for (Service bouquet : mItems) {
				if (ref != null && ref.equals(bouquet.getReference()))
					return mItems.indexOf(bouquet);
			}
			return -1;
		}
	}

	public class MovieListAdapter extends FragmentStateAdapter {
		ArrayList<String> mFolders;

		public MovieListAdapter(@NonNull Fragment fragment) {
			super(fragment);
			mFolders = new ArrayList();
		}

		public MovieListAdapter(ArrayList<String> folders, @NonNull Fragment fragment) {
			super(fragment);
			mFolders = folders;
		}

		public String get(int i) {
			return mFolders.get(i);
		}

		public void clear() {
			mFolders.clear();
		}

		public void add(String folder) {
			mFolders.add(folder);
		}

		public void addAll(@NonNull ArrayList<String> folders) {
			mFolders.addAll(folders);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position) {
			Fragment f = new MovieListFragment();
			Bundle args = new Bundle();
			args.putInt(MovieListFragment.ARGUMENT_LOCATION, position);
			f.setArguments(args);
			return f;
		}

		@Override
		public int getItemCount() {
			if (mFolders != null)
				return mFolders.size();
			return 0;
		}

		public int indexOf(@NonNull String ref) {
			return mFolders.indexOf(ref);
		}
	}

	public class TimerListAdapter extends FragmentStateAdapter {
		public TimerListAdapter(@NonNull Fragment fragment) {
			super(fragment);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position) {
			Fragment f = new TimerListFragment();
			return f;
		}

		@Override
		public int getItemCount() {
			return 1;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Nested under PhoneNavHostFragment (non-retained); keep hub non-retained too.
		mShouldRetainInstance = false;
		super.onCreate(savedInstanceState);
		mHasFabReload = false;
		mBouquets = null;
		mHubState = new TvMoviesHubState();
		if (savedInstanceState != null) {
			mMode = savedInstanceState.getString(KEY_MODE);
			mCurrentTv = savedInstanceState.getString(KEY_CURRENT_TV);
			mCurrentRadio = savedInstanceState.getString(KEY_CURRENT_RADIO);
			mCurrentMovie = savedInstanceState.getString(KEY_CURRENT_MOVIE);
		}
		if (mMode == null)
			mMode = MODE_TV;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putString(KEY_MODE, mMode);
		outState.putString(KEY_CURRENT_TV, mCurrentTv);
		outState.putString(KEY_CURRENT_RADIO, mCurrentRadio);
		outState.putString(KEY_CURRENT_MOVIE, mCurrentMovie);
		super.onSaveInstanceState(outState);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.service_list_pager, container, false);
		return v;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ComposeView header = view.findViewById(R.id.tv_movies_header);
		mDestinationNav = requireActivity().findViewById(R.id.tv_movies_nav);
		TvMoviesHubStateKt.bindTvMoviesHeader(header, mHubState, index -> {
			if (mPager.getAdapter() != null && index >= 0 && index < mPager.getAdapter().getItemCount())
				mPager.setCurrentItem(index, false);
			return kotlin.Unit.INSTANCE;
		});
		if (mDestinationNav != null) {
			mDestinationNav.setVisibility(View.VISIBLE);
			TvMoviesHubStateKt.bindTvMoviesDestinationBar(mDestinationNav, mHubState, dest -> {
				onDestinationSelected(dest);
				return kotlin.Unit.INSTANCE;
			});
		}
		mPager = view.findViewById(R.id.viewPager);
		mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				mHubState.setSelectedRow(position);
				switch(mMode) {
					case MODE_TV:
						if (mTvListAdapter.getItemCount() > position)
							mCurrentTv = mTvListAdapter.get(position).getReference();
						break;
					case MODE_RADIO:
						if (mRadioListAdapter.getItemCount() > position)
							mCurrentRadio = mRadioListAdapter.get(position).getReference();
						break;
					case MODE_MOVIES:
						if (mMovielistAdapter.getItemCount() > position)
							mCurrentMovie = mMovielistAdapter.get(position);
						break;
				}
			}
		});
		mTvListAdapter = new ServicelistAdapter(this);
		mRadioListAdapter = new ServicelistAdapter(this);
		mMovielistAdapter = new MovieListAdapter(this);
		mTimerListAdapter = new TimerListAdapter(this);

		if (MODE_MOVIES.equals(mMode)) {
			mPager.setAdapter(mMovielistAdapter);
			mHubState.setSelected(TvMoviesDestination.MOVIES);
		} else if (MODE_RADIO.equals(mMode)){
			mPager.setAdapter(mRadioListAdapter);
			mHubState.setSelected(TvMoviesDestination.RADIO);
		} else if (MODE_TIMER.equals(mMode)) {
			mPager.setAdapter(mTimerListAdapter);
			mHubState.setSelected(TvMoviesDestination.TIMER);
		} else {
			mPager.setAdapter(mTvListAdapter);
			mHubState.setSelected(TvMoviesDestination.TV);
		}

		if (mBouquetLoadJob != null) {
			mBouquetLoadJob.cancel(null);
		}
		mBouquetLoadJob = BouquetListLoadKt.launchBouquetListLoad(this, (result, bouquets, errorText) -> {
			onBouquetListReady(result, bouquets, errorText);
			return Unit.INSTANCE;
		});

		if (mLocationsAndTagsJob != null) {
			mLocationsAndTagsJob.cancel(null);
		}
		mLocationsAndTagsJob = LocationsAndTagsLoadKt.launchLocationsAndTagsLoad(
				this,
				(title, progress) -> Unit.INSTANCE,
				() -> {
					mLocationsAndTagsJob = null;
					onLocationsAndTagsReady();
					return Unit.INSTANCE;
				});
	}

	@Override
	public void onDestroyView() {
		if (mBouquetLoadJob != null) {
			mBouquetLoadJob.cancel(null);
			mBouquetLoadJob = null;
		}
		if (mLocationsAndTagsJob != null) {
			mLocationsAndTagsJob.cancel(null);
			mLocationsAndTagsJob = null;
		}
		if (mDestinationNav != null) {
			mDestinationNav.setVisibility(View.GONE);
			mDestinationNav.disposeComposition();
			mDestinationNav = null;
		}
		super.onDestroyView();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mMode.equals(MODE_TIMER))
			mPager.setAdapter(null);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mMode.equals(MODE_TIMER))
			mPager.setAdapter(mTimerListAdapter);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	private void onBouquetListReady(boolean result, Bouquets bouquets, String errorText) {
		mBouquetLoadJob = null;
		mBouquets = bouquets;
		if (errorText != null && !errorText.isEmpty())
			mHubState.setError(errorText);
		else
			mHubState.setError(null);
		if (mMode.equals(MODE_TV))
			onTvSelected();
		else if (mMode.equals(MODE_RADIO))
			onRadioSelected();
		else if (mMode.equals(MODE_MOVIES))
			onMoviesSelected();
		else if (mMode.equals((MODE_TIMER)))
			onTimerSelected();
	}

	protected void publishHubRows() {
		ArrayList<String> rows = new ArrayList<>();
		int count = 0;
		if (MODE_MOVIES.equals(mMode))
			count = mMovielistAdapter.getItemCount();
		else if (MODE_TV.equals(mMode))
			count = mTvListAdapter.getItemCount();
		else if (MODE_RADIO.equals(mMode))
			count = mRadioListAdapter.getItemCount();
		for (int i = 0; i < count; i++)
			rows.add(getTabText(i));
		mHubState.setRows(rows);
		if (mPager.getAdapter() != null)
			mHubState.setSelectedRow(mPager.getCurrentItem());
	}

	@Nullable
	protected String getTabText(int position) {
		if (MODE_MOVIES.equals(mMode) && mMovielistAdapter.getItemCount() > position)
			return mMovielistAdapter.get(position);
		if (MODE_TV.equals(mMode) && mTvListAdapter.getItemCount() > position)
			return mTvListAdapter.get(position).getName();
		if (MODE_RADIO.equals(mMode) && mRadioListAdapter.getItemCount() > position)
			return mRadioListAdapter.get(position).getName();
		return getString(R.string.not_available);
	}

	public void onDestinationSelected(@NonNull TvMoviesDestination dest) {
		switch (dest) {
			case TV:
				onTvSelected();
				break;
			case RADIO:
				onRadioSelected();
				break;
			case MOVIES:
				onMoviesSelected();
				break;
			case TIMER:
				onTimerSelected();
				break;
		}
	}

	public void onTvSelected() {
		mMode = MODE_TV;
		mHubState.setSelected(TvMoviesDestination.TV);
		mTvListAdapter.clear();
		String[] servicelist = getResources().getStringArray(R.array.servicelist_dedicated);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefstv);
		int start = 0;
		if (mBouquets != null && mBouquets.tv.size() > 0) {
			start = 1;
			for (Service bouquet : mBouquets.tv)
				mTvListAdapter.add(bouquet);
		}
		for (int i = start; i < servicelist.length; i++) {
			mTvListAdapter.add(new Service(servicerefs[i], servicelist[i]));
		}

		mTvListAdapter.notifyDataSetChanged();
		if (!mTvListAdapter.equals(mPager.getAdapter()))
			mPager.setAdapter(mTvListAdapter);

		if (mCurrentTv == null)
			mCurrentTv = DreamDroid.getCurrentProfile().getDefaultBouquetTv();
		int idx = mTvListAdapter.indexOf(mCurrentTv);
		if (idx >= 0)
			mPager.setCurrentItem(idx);
		else
			mPager.setCurrentItem(0);
		publishHubRows();
	}

	public void onRadioSelected() {
		mMode = MODE_RADIO;
		mHubState.setSelected(TvMoviesDestination.RADIO);
		mRadioListAdapter.clear();
		String[] servicelist = getResources().getStringArray(R.array.servicelist_dedicated);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefsradio);
		int start = 0;
		if (mBouquets != null && mBouquets.radio.size() > 0) {
			start = 1;
			for (Service bouquet : mBouquets.radio)
				mRadioListAdapter.add(bouquet);
		}
		for (int i = start; i < servicelist.length; i++) {
			mRadioListAdapter.add(new Service(servicerefs[i], servicelist[i]));
		}

		mRadioListAdapter.notifyDataSetChanged();
		if (!mRadioListAdapter.equals(mPager.getAdapter()))
			mPager.setAdapter(mRadioListAdapter);

		int idx = 0;
		if (mCurrentRadio != null)
			idx = mRadioListAdapter.indexOf(mCurrentRadio);

		if (idx >= 0) {
			mPager.setCurrentItem(idx);
		} else {
			mPager.setCurrentItem(0);
		}
		publishHubRows();
	}

	public void onMoviesSelected() {
		mMode = MODE_MOVIES;
		mHubState.setSelected(TvMoviesDestination.MOVIES);
		if (DreamDroid.getLocations().size() == 0) {
			showToast(getString(R.string.loading));
			publishHubRows();
			return;
		}
		mMovielistAdapter.clear();
		for (String location : DreamDroid.getLocations())
			mMovielistAdapter.add(location);
		mMovielistAdapter.notifyDataSetChanged();
		if (!mPager.getAdapter().equals(mMovielistAdapter))
			mPager.setAdapter(mMovielistAdapter);

		int idx = 0;
		if (mCurrentMovie != null)
			idx = mMovielistAdapter.indexOf(mCurrentMovie);

		if (idx >= 0)
			mPager.setCurrentItem(idx);
		else
			mPager.setCurrentItem(0);
		publishHubRows();
	}

	public void onTimerSelected() {
		mMode = MODE_TIMER;
		mHubState.setSelected(TvMoviesDestination.TIMER);
		mHubState.setRows(new ArrayList<>());
		mPager.setAdapter(mTimerListAdapter);
	}
}
