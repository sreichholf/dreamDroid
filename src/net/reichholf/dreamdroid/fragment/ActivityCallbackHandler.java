/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.view.KeyEvent;

/**
 * @author sre
 *
 */
public interface ActivityCallbackHandler {
	public boolean onKeyDown(int keyCode, KeyEvent event);
	public boolean onKeyUp(int keyCode, KeyEvent event);
}
