/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;

import android.os.Bundle;
import android.view.WindowManager;

/**
 * @author sre
 * 
 */
public class SimpleNoTitleFragmentActivity extends SimpleFragmentActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setTheme(R.style.Theme_DreamDroid_NoTitle);
		mThemeSet = true;
		super.onCreate(savedInstanceState);
	}
}
