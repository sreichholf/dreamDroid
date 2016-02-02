/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.interfaces;

import android.view.Menu;
import android.view.MenuInflater;

import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;

/**
 * @author sre
 */
public interface IMutliPaneContent {
	MultiPaneHandler getMultiPaneHandler();

	void createOptionsMenu(Menu menu, MenuInflater inflater);
}
