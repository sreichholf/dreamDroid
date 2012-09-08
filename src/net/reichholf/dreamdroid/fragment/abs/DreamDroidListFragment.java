/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import android.app.Activity;

import com.actionbarsherlock.app.SherlockListFragment;

/**
 * @author sre
 * 
 */
public abstract class DreamDroidListFragment extends SherlockListFragment implements ActivityCallbackHandler {
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		((MultiPaneHandler) activity).onDetailFragmentAttached(this);
	}

	@Override
	public void onPause() {
		((MultiPaneHandler) getSherlockActivity()).onDetailFragmentPause(this);
		super.onPause();
	}
}
