/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import net.reichholf.dreamdroid.DreamDroid;

public class TabbedNavigationActivity extends Activity {
	/* (non-Javadoc)
	 * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent;
		if (DreamDroid.isTV(this)) {
			intent = new Intent(this, net.reichholf.dreamdroid.tv.activities.MainActivity.class);
		} else {
			intent = new Intent(this, MainActivity.class);
		}
		startActivity(intent);
		finish();
	}
}
