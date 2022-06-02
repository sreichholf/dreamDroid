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
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

import java.util.ArrayList;

public class ServiceListPager extends BaseHttpFragment implements GetBouquetListTask.GetBoquetListTaskHandler {
	private static final String MODE_TV = "TV";
	private static final String MODE_RADIO = "Radio";
	private static final String MODE_MOVIES = "Movies";
	private static final String MODE_TIMER = "Timer";


	@State
	public String mMode;

	ViewPager2 mPager;

	ServicelistAdapter mServicelistAdapter;
	MovieListAdapter mMovielistAdapter;
	TimerListAdapter mTimerListAdapter;

	NavigationBarView mNavigation;
	TabLayout mTabLayout;
	@Nullable
	TabLayoutMediator mTabLayoutMediator;
	GetBouquetListTask mBouquetListTask;

	int mSelectedItemId;

	@Nullable
	private GetBouquetListTask.Bouquets mBouquets;

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
			return f;
		}

		@Override
		public int getItemCount() {
			if (mFolders != null)
				return mFolders.size();
			return 0;
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
					ServiceListPageFragment f = (ServiceListPageFragment) getChildFragmentManager().findFragmentByTag("f" +mPager.getCurrentItem());
					f.upOrReload();
				}
			}
		});

		mPager = getView().findViewById(R.id.viewPager);

		mServicelistAdapter = new ServicelistAdapter(this);
		mMovielistAdapter = new MovieListAdapter(this);
		mTimerListAdapter = new TimerListAdapter(this);

		mPager.setAdapter(mServicelistAdapter);

		attachTabLayoutMediator();
		if (mBouquetListTask != null) {
			mBouquetListTask.cancel(true);
		}
		mBouquetListTask = new GetBouquetListTask(this);
		mBouquetListTask.execute();
	}

	@Override
	public void onPause() {
		super.onPause();
		mNavigation.setVisibility(View.GONE);
	}

	@Override
	public void onResume() {
		super.onResume();
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
		else if (mMode.equals((MODE_MOVIES)))
			onMoviesSelected();
		else
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
		if (mSelectedItemId == R.id.menu_movie)
			return mMovielistAdapter.get(position);
		return mServicelistAdapter.get(position).getString(Service.KEY_NAME);
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
		mServicelistAdapter.clear();
		detachTabLayoutMediator();
		String[] servicelist = getResources().getStringArray(R.array.servicelist_dedicated);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefstv);
		int start = 0;
		if (mBouquets != null && mBouquets.tv.size() > 0)
			start = 1;
		for (ExtendedHashMap bouquet : mBouquets.tv)
			mServicelistAdapter.add(bouquet);
		for (int i = start; i < servicelist.length; i++) {
			ExtendedHashMap bouquet = new ExtendedHashMap();
			bouquet.put(Event.KEY_SERVICE_NAME, servicelist[i]);
			bouquet.put(Event.KEY_SERVICE_REFERENCE, servicerefs[i]);
			mServicelistAdapter.add(bouquet);
		}

		mServicelistAdapter.notifyDataSetChanged();
		mPager.setAdapter(mServicelistAdapter);
		attachTabLayoutMediator();
	}

	public void onRadioSelected() {
		mMode = MODE_RADIO;
		mServicelistAdapter.clear();
		detachTabLayoutMediator();
		String[] servicelist = getResources().getStringArray(R.array.servicelist_dedicated);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefsradio);
		int start = 0;
		if (mBouquets != null && mBouquets.radio.size() > 0)
			start = 1;
		for (ExtendedHashMap bouquet : mBouquets.radio)
			mServicelistAdapter.add(bouquet);
		for (int i = start; i < servicelist.length; i++) {
			ExtendedHashMap bouquet = new ExtendedHashMap();
			bouquet.put(Event.KEY_SERVICE_NAME, servicelist[i]);
			bouquet.put(Event.KEY_SERVICE_REFERENCE, servicerefs[i]);
			mServicelistAdapter.add(bouquet);
		}

		mServicelistAdapter.notifyDataSetChanged();
		//if (!mPager.getAdapter().equals(mServicelistAdapter))
		mPager.setAdapter(mServicelistAdapter);
		attachTabLayoutMediator();
	}

	public void onMoviesSelected() {
		mMode = MODE_MOVIES;
		detachTabLayoutMediator();
		mMovielistAdapter.clear();
		for (String location : DreamDroid.getLocations())
			mMovielistAdapter.add(location);
		mMovielistAdapter.notifyDataSetChanged();
		if (!mPager.getAdapter().equals(mMovielistAdapter))
			mPager.setAdapter(mMovielistAdapter);
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
