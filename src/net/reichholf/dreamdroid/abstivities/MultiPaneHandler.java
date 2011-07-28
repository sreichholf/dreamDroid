/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.abstivities;

import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import android.support.v4.app.Fragment;

/**
 * @author sre
 *
 */
public interface MultiPaneHandler {
	public void showDetails(Fragment fragment);
	public void showDetails(Fragment fragment, boolean addToBackStack);
	public void showDetails(Class<?> fragmentClass);
	public void setActivityCallbackHandler(ActivityCallbackHandler handler);
	public void finish();
	public void finish(boolean Fragment);
	public void onProfileChanged();
}
