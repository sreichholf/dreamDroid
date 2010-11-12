/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler.impl;

import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;

/**
 * @author sre
 *
 */
public class TimerAddByEventIdRequestHandler extends SimpleResultRequestHandler {
	public TimerAddByEventIdRequestHandler(){
		super(URIStore.TIMER_ADD_BY_EVENT_ID);
	}
}
