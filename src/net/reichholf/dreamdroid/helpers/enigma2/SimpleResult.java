package net.reichholf.dreamdroid.helpers.enigma2;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SimpleResultHandler;

public class SimpleResult {
	public static final String STATE = "state";
	public static final String STATE_TEXT = "statetext";
	
	public static ExtendedHashMap parseSimpleResult(String xml) {
		ExtendedHashMap result = new ExtendedHashMap();

		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2SimpleResultHandler handler = new E2SimpleResultHandler(result);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return result;
		} else {
			result.put(STATE, Python.FALSE);
			result.put(STATE_TEXT, null);
			return result;
		}
	}
}
