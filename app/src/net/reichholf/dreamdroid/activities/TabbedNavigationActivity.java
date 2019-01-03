/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import net.reichholf.dreamdroid.DreamDroid;

public class TabbedNavigationActivity extends AppCompatActivity {
	/* (non-Javadoc)
	 * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
	 */
	public void onCreate(Bundle savedInstanceState) {
		DreamDroid.setTheme(this);
		super.onCreate(savedInstanceState);


		Intent intent;
		if(DreamDroid.isTV(this)) {
			intent = new Intent(this, net.reichholf.dreamdroid.tv.activities.MainActivity.class);
		} else {
			intent = new Intent(this, MainActivity.class);
		}
		startActivity(intent);
		finish();
	}
}
