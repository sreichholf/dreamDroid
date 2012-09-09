/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.abstivities;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

/**
 * @author sre
 *
 */
public interface MultiPaneHandler {
	public void showDetails(Fragment fragment);
	public void showDetails(Fragment fragment, boolean addToBackStack);
	public void showDetails(Fragment fragment, Class<? extends MultiPaneHandler> handlerClass);
	public void showDetails(Fragment fragment, Class<? extends MultiPaneHandler> handlerClass, boolean addToBackStack);
	public void showDetails(Class<? extends Fragment> fragmentClass);
	public void showDetails(Class<? extends Fragment> fragmentClass, Class<? extends MultiPaneHandler> handlerClass);
	public void onDetailFragmentResume(Fragment fragment);
	public void onDetailFragmentPause(Fragment fragment);
	public void showDialog(Class<? extends DialogFragment> fragmentClass, Bundle args, String tag);
	public void showDialog(DialogFragment fragment, String tag);
	public boolean isMultiPane();
	public void finish();
}
