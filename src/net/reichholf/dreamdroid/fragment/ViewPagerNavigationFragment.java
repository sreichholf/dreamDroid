/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.ViewPagerAdapter;
import net.reichholf.dreamdroid.helpers.Statics;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.viewpagerindicator.TitlePageIndicator;

/**
 * @author sre
 *
 */
public class ViewPagerNavigationFragment extends NavigationFragment{
	private Button mButtonPower;
	private Button mButtonCurrent;
	private Button mButtonConnectivity;
	private Button mButtonMovies;
	private Button mButtonServices;
	private Button mButtonTimer;
	private Button mButtonEpgSearch;
	private Button mButtonRemote;
	private Button mButtonSleepTimer;
	private Button mButtonScreenshot;
	private Button mButtonDeviceInfo;
	private Button mButtonMessage;
	private Button mButtonAbout;
	private Button mButtonProfiles;
	private ViewPager mPager;
	 
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

		View v = inflater.inflate(R.layout.indicated_viewpager, null);
		View[] pageViews = {inflater.inflate(R.layout.main, null), inflater.inflate(R.layout.extras, null)};
		ViewPagerAdapter adapter = new ViewPagerAdapter( this.getActivity(), new String[]{"Main", "Extras"}, pageViews );
	    mPager =
	        (ViewPager)v.findViewById( R.id.viewpager );
	    TitlePageIndicator indicator =
	        (TitlePageIndicator)v.findViewById( R.id.indicator );
	    mPager.setAdapter( adapter );	    
	    indicator.setViewPager( mPager );
		
	    mButtonPower = (Button) pageViews[0].findViewById(R.id.ButtonPower);
		mButtonCurrent = (Button) pageViews[0].findViewById(R.id.ButtonCurrent);
		mButtonMovies = (Button) pageViews[0].findViewById(R.id.ButtonMovies);
		mButtonServices = (Button) pageViews[0].findViewById(R.id.ButtonServices);
		mButtonTimer = (Button) pageViews[0].findViewById(R.id.ButtonTimer);
		mButtonRemote = (Button) pageViews[0].findViewById(R.id.ButtonVirtualRemote);
		mButtonEpgSearch = (Button) pageViews[0].findViewById(R.id.ButtonEpgSearch);
		mButtonProfiles = (Button) pageViews[0].findViewById(R.id.ButtonProfiles);
		mButtonSleepTimer = (Button) pageViews[1].findViewById(R.id.ButtonSleeptimer);
		mButtonSleepTimer.setEnabled(DreamDroid.featureSleepTimer());
		mButtonScreenshot = (Button) pageViews[1].findViewById(R.id.ButtonScreenshot);
		mButtonDeviceInfo = (Button) pageViews[1].findViewById(R.id.ButtonDeviceInfo);
		mButtonAbout = (Button) pageViews[1].findViewById(R.id.ButtonAbout);
		mButtonMessage = (Button) pageViews[1].findViewById(R.id.ButtonMessage);
		mButtonConnectivity = (Button) pageViews[1].findViewById(R.id.ButtonCheckConnection);
		
		registerOnClickListener(mButtonSleepTimer,  Statics.ITEM_SLEEPTIMER);
		registerOnClickListener(mButtonScreenshot,  Statics.ITEM_SCREENSHOT);
		registerOnClickListener(mButtonDeviceInfo,  Statics.ITEM_INFO);
		registerOnClickListener(mButtonAbout,  Statics.ITEM_ABOUT);
		registerOnClickListener(mButtonMessage,  Statics.ITEM_MESSAGE);
		registerOnClickListener(mButtonConnectivity,  Statics.ITEM_CHECK_CONN);
		registerOnClickListener(mButtonProfiles, Statics.ITEM_PROFILES);
		registerOnClickListener(mButtonPower, Statics.ITEM_POWERSTATE_DIALOG);
		registerOnClickListener(mButtonCurrent, Statics.ITEM_CURRENT);
		registerOnClickListener(mButtonMovies, Statics.ITEM_MOVIES);
		registerOnClickListener(mButtonServices, Statics.ITEM_SERVICES);
		registerOnClickListener(mButtonTimer, Statics.ITEM_TIMER);
		registerOnClickListener(mButtonRemote, Statics.ITEM_REMOTE);
		registerOnClickListener(mButtonEpgSearch, Statics.ITEM_EPG_SEARCH);
		return v;
	}
	
	@Override
	public void setAvailableFeatures(){
		if (DreamDroid.featureSleepTimer()) {
			mButtonSleepTimer.setEnabled(DreamDroid.featureSleepTimer());
		}
	}
	
}

