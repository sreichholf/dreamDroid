package net.reichholf.dreamdroid.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

/**
 * Created by Stephan on 09.11.13.
 */
public class VirtualRemotePagerFragment extends BaseHttpFragment {

	ViewPager mPager;
	RemotePagerAdapter mPagerAdapter;

	private class RemotePagerAdapter extends FragmentStatePagerAdapter {
		ExtendedHashMap mItems;

		public RemotePagerAdapter(FragmentManager fm) {
			super(fm);
			mItems = new ExtendedHashMap();

			Fragment f = new VirtualRemoteFragment();
			Bundle args = new Bundle();
			args.putBoolean(DreamDroid.PREFS_KEY_QUICKZAP, false);
			f.setArguments(args);
			mItems.put(getString(R.string.quickzap), f);

			f = new VirtualRemoteFragment();
			args = new Bundle();
			args.putBoolean(DreamDroid.PREFS_KEY_QUICKZAP, true);
			f.setArguments(args);

			mItems.put(getString(R.string.standard), f);
		}

		@Override
		public Fragment getItem(int i) {
			return (Fragment) mItems.get(mItems.keySet().toArray()[i]);
		}

		@Override
		public int getCount() {
			return mItems.size();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mShouldRetainInstance = false;
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.virtual_remote_pager, container, false);

		mPager = (ViewPager) view.findViewById(R.id.pager);
		mPagerAdapter = new RemotePagerAdapter(getChildFragmentManager());
		mPager.setAdapter(mPagerAdapter);

		ImageButton toggle = (ImageButton) view.findViewById(R.id.toggle_remote);
		toggle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mPager.getCurrentItem() == 0)
					mPager.setCurrentItem(1);
				else
					mPager.setCurrentItem(0);
			}
		});

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		if (!sp.getBoolean(DreamDroid.PREFS_KEY_SIMPLE_VRM, true))
			mPager.setCurrentItem(1);

		return view;
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
	}

}
