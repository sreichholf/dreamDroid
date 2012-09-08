/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import android.app.Activity;

import com.actionbarsherlock.app.SherlockFragment;

/**
 * @author sre
 * 
 */
public abstract class DreamDroidFragment extends SherlockFragment implements ActivityCallbackHandler {
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		((MultiPaneHandler) activity).onDetailFragmentAttached(this);
	}

	@Override
	public void onPause() {
		MultiPaneHandler mph = (MultiPaneHandler) getSherlockActivity();
		if(mph != null)
			mph.onDetailFragmentPause(this);
		super.onPause();
	}
}
