/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;


import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SleepTimerHandler;

/**
 * @author sre
 *
 */
public class SleepTimer {
	public static final String ACTION_STANDBY = "standby";
	public static final String ACTION_SHUTDOWN = "shutdown";
	public static final String CMD_GET = "get";
	public static final String CMD_SET = "set";
	
	public static final String KEY_ENABLED = "enabled";
	public static final String KEY_MINUTES = "minutes";
	public static final String KEY_ACTION = "action";
	public static final String KEY_TEXT = "text";
	
	public static ExtendedHashMap parse(String xml){
		ExtendedHashMap result = new ExtendedHashMap();

		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2SleepTimerHandler handler = new E2SleepTimerHandler(result);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return result;
		}
		
		return null;
	}
	
}
