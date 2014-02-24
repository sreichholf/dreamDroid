/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.xml.sax.helpers.DefaultHandler;

/**
 * @author sre
 *
 */
public class E2SimpleHandler extends DefaultHandler {
	protected ExtendedHashMap mResult;
	
	public E2SimpleHandler(){
		mResult = null;
	}
	
	public void setMap(ExtendedHashMap map){
		mResult = map;
	}
}
