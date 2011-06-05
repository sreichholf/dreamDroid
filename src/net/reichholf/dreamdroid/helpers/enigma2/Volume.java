/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2VolumeHandler;

/**
 * @author sre
 *
 */
public class Volume {
	public static final String KEY_RESULT = "result";
	public static final String KEY_CURRENT = "current";
	public static final String KEY_MUTED = "muted";
	
	public static final String CMD_UP = "up";
	public static final String CMD_DOWN = "down";
	public static final String CMD_MUTE = "mute";
	
	public static ExtendedHashMap parse(String xml){
		ExtendedHashMap volume = new ExtendedHashMap();
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2VolumeHandler handler = new E2VolumeHandler(volume);
		sdp.getParser().setHandler(handler);
		
		if (sdp.parse(xml)) {
			return volume;
		} else {			
			return null;
		}
	}
}
