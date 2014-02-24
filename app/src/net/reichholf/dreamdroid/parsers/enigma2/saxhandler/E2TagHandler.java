/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

public class E2TagHandler extends E2SimpleListHandler {
	protected static final String TAG_E2TAG = "e2tag";

	public E2TagHandler(){
		super(TAG_E2TAG);
	}
}
