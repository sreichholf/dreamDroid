/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile;
import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TabHost;
import android.widget.TextView;

public class TabbedNavigationActivity extends TabActivity {
	private CheckProfileTask mCheckProfileTask;
	private TextView mActiveProfile;
	private TextView mConnectionState;
	
	private class CheckProfileTask extends AsyncTask<Void, String, ExtendedHashMap> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected ExtendedHashMap doInBackground(Void... params) {
			publishProgress(getText(R.string.checking).toString());
			return CheckProfile.checkProfile(DreamDroid.PROFILE);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			setConnectionState(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(ExtendedHashMap result) {
			Log.i(DreamDroid.LOG_TAG, result.toString());
			if ((Boolean) result.get(CheckProfile.KEY_HAS_ERROR)) {
				String error = getText((Integer) result.get(CheckProfile.KEY_ERROR_TEXT)).toString();
				setConnectionState(error);
			} else {
				setConnectionState(getText(R.string.ok).toString());
			}
		}
	}	
	
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabhost);

		Resources res = getResources(); // Resource object to get Drawables
		TabHost tabHost = getTabHost(); // The activity TabHost
		TabHost.TabSpec spec; // Resusable TabSpec for each tab
		Intent intent; // Reusable Intent for each tab

		// Create an Intent to launch an Activity for the tab (to be reused)
		intent = new Intent().setClass(this, MainActivity.class);
		
		// Initialize a TabSpec for each tab and add it to the TabHost
		spec = tabHost.newTabSpec("menu").setIndicator("Menu", res.getDrawable(android.R.drawable.ic_menu_view))
				.setContent(intent);
		tabHost.addTab(spec);

		// Do the same for the other tabs
		intent = new Intent().setClass(this, ExtrasActivity.class);
		spec = tabHost.newTabSpec("extras").setIndicator("Extras", res.getDrawable(android.R.drawable.ic_menu_zoom))
				.setContent(intent);
		tabHost.addTab(spec);

		intent = new Intent().setClass(this, ProfileListActivity.class);
		spec = tabHost.newTabSpec("profiles").setIndicator("Profiles", res.getDrawable(R.drawable.ic_tab_link))
				.setContent(intent);
		tabHost.addTab(spec);

		tabHost.setCurrentTab(0);
		
		mActiveProfile = (TextView) findViewById(R.id.TextViewProfile);
		mConnectionState = (TextView) findViewById(R.id.TextViewConnectionState);
		
		setProfileName();
		checkActiveProfile();
	}
	
	/**
	 * 
	 */
	public void setProfileName() {
		mActiveProfile.setText(DreamDroid.PROFILE.getProfile());
	}

	/**
	 * @param state
	 */
	private void setConnectionState(String state) {		
		mConnectionState.setText(state);
		setAvailableFeatures();
	}

	/**
	 * 
	 */
	public void checkActiveProfile() {
		if (mCheckProfileTask != null) {
			mCheckProfileTask.cancel(true);
		}

		mCheckProfileTask = new CheckProfileTask();
		mCheckProfileTask.execute();
	}
	
	/**
	 * 
	 */
	private void setAvailableFeatures(){
		Activity currentActivity = getCurrentActivity();
		if(currentActivity.getClass().equals(ExtrasActivity.class)){
			( (ExtrasActivity) currentActivity ).setAvailableFeatures();
		}
	}
	

}
