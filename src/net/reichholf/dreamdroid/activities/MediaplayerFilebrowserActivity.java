/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import android.os.Bundle;
import net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity;

/**
 * @author sre
 *
 */
public class MediaplayerFilebrowserActivity extends AbstractHttpListActivity {
	private MediaplayerNavigationActivity mParent;
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		mParent = (MediaplayerNavigationActivity) getParent();
		super.onCreate(savedInstanceState);
	}
}
