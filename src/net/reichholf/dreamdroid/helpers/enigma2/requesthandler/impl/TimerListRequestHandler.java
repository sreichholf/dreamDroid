/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler.impl;

import java.util.ArrayList;

import org.apache.http.NameValuePair;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ListRequestHandler;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2TimerHandler;

/**
 * @author sre
 *
 */
public class TimerListRequestHandler implements ListRequestHandler {

	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public String getList(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.TIMER_LIST, params[0])) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param xml
	 * @param list
	 * @return
	 */
	public boolean parseList(String xml, ArrayList<ExtendedHashMap> list) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2TimerHandler handler = new E2TimerHandler(list);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}

}
