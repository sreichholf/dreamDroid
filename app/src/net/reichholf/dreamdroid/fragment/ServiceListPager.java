package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
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


	ViewPager2 mPager;

	ServicelistAdapter mServicelistAdapter;
	MovieListAdapter mMovielistAdapter;
	TimerListAdapter mTimerListAdapter;

	BottomNavigationView mBottomNavigation;
	TabLayout mTabLayout;
	TabLayoutMediator mTabLayoutMediator;
	GetBouquetListTask mBouquetListTask;

	int mSelectedItemId;

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

		public void addAll(ArrayList<String> folders) {
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
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.service_list_pager, container, false);
		return v;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mBottomNavigation = getAppCompatActivity().findViewById(R.id.bottom_navigation);
		mBottomNavigation.setVisibility(View.VISIBLE);
		mBottomNavigation.getMenu().clear();
		mBottomNavigation.inflateMenu(R.menu.bottom_navigation_services);
		mBottomNavigation.setOnItemSelectedListener(item -> {
			onItemSelected(item);
			return true;
		});

		mTabLayout = getView().findViewById(R.id.tab_layout);

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
		mBottomNavigation.setVisibility(View.GONE);
	}

	@Override
	public void onResume() {
		super.onResume();
		mBottomNavigation.setVisibility(View.VISIBLE);
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
		onTvSelected();
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

	protected String getTabText(int position) {
		if (mSelectedItemId == R.id.menu_movie)
			return mMovielistAdapter.get(position);
		return mServicelistAdapter.get(position).getString(Service.KEY_NAME);
	}

	public void onItemSelected(MenuItem item) {
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
		detachTabLayoutMediator();
		mTabLayout.setVisibility(View.GONE);
		mPager.setAdapter(mTimerListAdapter);
	}
}
