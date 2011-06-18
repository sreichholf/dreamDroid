/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;

/**
 * @author sre
 *
 */
public class MediaplayerNavigationActivity extends TabActivity {
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		TabHost tabHost = getTabHost();
		TabHost.TabSpec spec;
		Intent intent;
		Resources res = getResources();
		
		intent = new Intent().setClass(this, MediaplayerActivity.class);
		spec = tabHost.newTabSpec("player")
				.setIndicator("Player", res.getDrawable(android.R.drawable.ic_menu_view))
				.setContent(intent);
		tabHost.addTab(spec);
		
		intent = new Intent().setClass(this, MediaplayerPlaylistActivity.class);
		spec = tabHost.newTabSpec("playlist")
		.setIndicator("Playlist", res.getDrawable(android.R.drawable.ic_menu_view))
		.setContent(intent);
		tabHost.addTab(spec);
		
		intent = new Intent().setClass(this, MediaplayerFilebrowserActivity.class);
		spec = tabHost.newTabSpec("browser")
		.setIndicator("browser", res.getDrawable(android.R.drawable.ic_menu_view))
		.setContent(intent);
		tabHost.addTab(spec);
		
		tabHost.setCurrentTab(0);
	}
}
