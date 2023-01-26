package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.evernote.android.state.State;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.asynctask.GetBouquetListTask;
import net.reichholf.dreamdroid.asynctask.GetLocationsAndTagsTask;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import java.util.ArrayList;

public class ServiceListPager extends BaseHttpFragment implements GetBouquetListTask.GetBouquetListTaskHandler, GetLocationsAndTagsTask.GetLocationsAndTagsTaskHandler {
	private static final String MODE_TV = "TV";
	private static final String MODE_RADIO = "Radio";
	private static final String MODE_MOVIES = "Movies";
	private static final String MODE_TIMER = "Timer";


	@State
	public String mMode;

	@State
	public String mCurrentTv;

	@State
	public String mCurrentRadio;

	@State
	public String mCurrentMovie;

	ViewPager2 mPager;

	ServicelistAdapter mTvListAdapter;
	ServicelistAdapter mRadioListAdapter;
	MovieListAdapter mMovielistAdapter;
	TimerListAdapter mTimerListAdapter;

	NavigationBarView mNavigation;
	TabLayout mTabLayout;
	@Nullable
	TabLayoutMediator mTabLayoutMediator;
	GetBouquetListTask mBouquetListTask;
	GetLocationsAndTagsTask mLocationsAndTagsTask;

	int mSelectedItemId;

	@Nullable
	private GetBouquetListTask.Bouquets mBouquets;

	@Override
	public void onGetLocationsAndTagsProgress(String title, String progress) {
		// skip
	}

	@Override
	public void onLocationsAndTagsReady() {
		if (mMode.equals((MODE_MOVIES)))
			onMoviesSelected();
	}

	public class ServicelistAdapter extends FragmentStateAdapter {
		ArrayList<ExtendedHashMap> mItems;

		public ServicelistAdapter(@NonNull Fragment fragment) {
			super(fragment);
			mItems = new ArrayList();
		}

		public ExtendedHashMap get(int i) {
			return mItems.get(i);
		}

		public void clear() {
			mItems.clear();
		}

		public void add(ExtendedHashMap e) {
			mItems.add(e);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position) {
			Fragment f = new ServiceListPageFragment();

			Bundle args = new Bundle();
			ExtendedHashMap service = mItems.get(position);
			args.putString(Service.KEY_REFERENCE, service.getString(Service.KEY_REFERENCE));
			args.putString(Service.KEY_NAME, service.getString(Service.KEY_NAME));
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
			for (ExtendedHashMap bouquet : mItems) {
				if (ref != null && ref.equals(bouquet.get(Service.KEY_REFERENCE)))
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
		super.onCreate(savedInstanceState);
		mHasFabReload = false;
		mBouquets = null;
		if (mMode == null)
			mMode = MODE_TV;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.service_list_pager, container, false);
		return v;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mNavigation = getAppCompatActivity().findViewById(R.id.bottom_navigation);
		mNavigation.setVisibility(View.VISIBLE);
		mNavigation.getMenu().clear();
		mNavigation.inflateMenu(R.menu.bottom_navigation_services);
		mNavigation.setOnItemSelectedListener(item -> {
			onItemSelected(item);
			return true;
		});

		mTabLayout = getView().findViewById(R.id.tab_layout);
		mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab) {
			}

			@Override
			public void onTabReselected(TabLayout.Tab tab) {
				if (mMode.equals(MODE_TV) || mMode.equals(MODE_RADIO)) {
					ServiceListPageFragment f = (ServiceListPageFragment) getChildFragmentManager().findFragmentByTag("f" + mPager.getCurrentItem());
					f.upOrReload();
				}
			}
		});

		mPager = getView().findViewById(R.id.viewPager);
		mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				switch(mMode) {
					case MODE_TV:
						mCurrentTv = mTvListAdapter.get(position).getString(Service.KEY_REFERENCE, null);
						break;
					case MODE_RADIO:
						mCurrentRadio = mRadioListAdapter.get(position).getString(Service.KEY_REFERENCE, null);
						break;
					case MODE_MOVIES:
						mCurrentMovie = mMovielistAdapter.get(position);
						break;
				}
			}
		});
		mTvListAdapter = new ServicelistAdapter(this);
		mRadioListAdapter = new ServicelistAdapter(this);
		mMovielistAdapter = new MovieListAdapter(this);
		mTimerListAdapter = new TimerListAdapter(this);

		//selectedItemId = mTabLayout.getSelectedTabPosition()
		if (MODE_MOVIES.equals(mMode)) {
			mPager.setAdapter(mMovielistAdapter);
		} else if (MODE_RADIO.equals(mMode)){
			mPager.setAdapter(mRadioListAdapter);
		} else if (MODE_TIMER.equals(mMode)) {
			mPager.setAdapter(mTimerListAdapter);
		} else {
			mPager.setAdapter(mTvListAdapter);
		}

		if (mMode != MODE_TIMER)
			attachTabLayoutMediator();
		if (mBouquetListTask != null) {
			mBouquetListTask.cancel(true);
		}
		mBouquetListTask = new GetBouquetListTask(this);
		mBouquetListTask.execute();

		if (mLocationsAndTagsTask != null) {
			mLocationsAndTagsTask.cancel(true);
		}
		mLocationsAndTagsTask = new GetLocationsAndTagsTask(this);
		mLocationsAndTagsTask.execute();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mMode.equals(MODE_TIMER))
			mPager.setAdapter(null);
		mNavigation.setVisibility(View.GONE);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mMode.equals(MODE_TIMER))
			mPager.setAdapter(mTimerListAdapter);
		mNavigation.setVisibility(View.VISIBLE);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public void onBouquetListReady(boolean result, GetBouquetListTask.Bouquets bouquets, String errorText) {
		mBouquets = bouquets;
		if (mMode.equals(MODE_TV))
			onTvSelected();
		else if (mMode.equals(MODE_RADIO))
			onRadioSelected();
		else if (mMode.equals(MODE_MOVIES))
			onMoviesSelected();
		else if (mMode.equals((MODE_TIMER)))
			onTimerSelected();
	}

	protected void attachTabLayoutMediator() {
		if (mTabLayout.getVisibility() != View.VISIBLE)
			mTabLayout.setVisibility(View.VISIBLE);
		detachTabLayoutMediator();
		mTabLayoutMediator = new TabLayoutMediator(mTabLayout, mPager,
				(tab, position) -> tab.setText(getTabText(position))
		);
		mTabLayoutMediator.attach();
	}

	protected void detachTabLayoutMediator() {
		if (mTabLayoutMediator != null)
			mTabLayoutMediator.detach();
		mTabLayoutMediator = null;
	}

	@Nullable
	protected String getTabText(int position) {
		if (MODE_MOVIES.equals(mMode) && mMovielistAdapter.getItemCount() > position)
			return mMovielistAdapter.get(position);
		if (MODE_TV.equals(mMode) && mTvListAdapter.getItemCount() > position)
			return mTvListAdapter.get(position).getString(Service.KEY_NAME);
		if (MODE_RADIO.equals(mMode) && mRadioListAdapter.getItemCount() > position)
			return mRadioListAdapter.get(position).getString(Service.KEY_NAME);

		return getString(R.string.not_available);
	}

	public void onItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == mSelectedItemId)
			return;
		mSelectedItemId = item.getItemId();
		switch (item.getItemId()) {
			case R.id.menu_tv:
				onTvSelected();
				break;
			case R.id.menu_radio:
				onRadioSelected();
				break;
			case R.id.menu_movie:
				onMoviesSelected();
				break;
			case R.id.menu_timer:
				onTimerSelected();
				break;
		}
	}

	public void onTvSelected() {
		mMode = MODE_TV;
		mTvListAdapter.clear();
		detachTabLayoutMediator();
		String[] servicelist = getResources().getStringArray(R.array.servicelist_dedicated);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefstv);
		int start = 0;
		if (mBouquets != null && mBouquets.tv.size() > 0) {
			start = 1;
			for (ExtendedHashMap bouquet : mBouquets.tv)
				mTvListAdapter.add(bouquet);
		}
		for (int i = start; i < servicelist.length; i++) {
			ExtendedHashMap bouquet = new ExtendedHashMap();
			bouquet.put(Event.KEY_SERVICE_NAME, servicelist[i]);
			bouquet.put(Event.KEY_SERVICE_REFERENCE, servicerefs[i]);
			mTvListAdapter.add(bouquet);
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

		attachTabLayoutMediator();
	}

	public void onRadioSelected() {
		mMode = MODE_RADIO;
		mRadioListAdapter.clear();
		detachTabLayoutMediator();
		String[] servicelist = getResources().getStringArray(R.array.servicelist_dedicated);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefsradio);
		int start = 0;
		if (mBouquets != null && mBouquets.radio.size() > 0) {
			start = 1;
			for (ExtendedHashMap bouquet : mBouquets.radio)
				mRadioListAdapter.add(bouquet);
		}
		for (int i = start; i < servicelist.length; i++) {
			ExtendedHashMap bouquet = new ExtendedHashMap();
			bouquet.put(Event.KEY_SERVICE_NAME, servicelist[i]);
			bouquet.put(Event.KEY_SERVICE_REFERENCE, servicerefs[i]);
			mRadioListAdapter.add(bouquet);
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

		attachTabLayoutMediator();
	}

	public void onMoviesSelected() {
		mMode = MODE_MOVIES;
		detachTabLayoutMediator();
		if (DreamDroid.getLocations().size() == 0) {
			showToast(getString(R.string.loading));
			attachTabLayoutMediator();
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

		attachTabLayoutMediator();
	}

	public void onTimerSelected() {
		mMode = MODE_TIMER;
		detachTabLayoutMediator();
		if (mNavigation instanceof NavigationRailView)
			mTabLayout.removeAllTabs();
		else
			mTabLayout.setVisibility(View.GONE);

		mPager.setAdapter(mTimerListAdapter);
	}
}
