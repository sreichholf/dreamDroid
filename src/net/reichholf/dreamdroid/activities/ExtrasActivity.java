/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import android.os.Bundle;

/**
 * @author sre
 *
 */
public class ExtrasActivity extends MainActivity {	
	public void onCreate(Bundle savedInstanceState){
		getIntent().putExtra("extras", true);
		super.onCreate(savedInstanceState);
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	protected void onResume (){		
		super.setAvailableFeatures();
		super.onResume();
	}
}
