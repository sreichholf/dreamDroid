/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SimpleResultHandler;

public class SimpleResultRequestHandler extends AbstractSimpleRequestHandler {
	public SimpleResultRequestHandler(String uri) {
		super(uri, new E2SimpleResultHandler());
	}

	public ExtendedHashMap parseSimpleResult(String xml) {
		ExtendedHashMap result = new ExtendedHashMap();
		parse(xml, result);
		return result;
	}
	
	public ExtendedHashMap getDefault(){
		ExtendedHashMap result = new ExtendedHashMap();
		result.put(SimpleResult.KEY_STATE, Python.FALSE);
		result.put(SimpleResult.KEY_STATE_TEXT, null);
		
		return result;
	}
}
