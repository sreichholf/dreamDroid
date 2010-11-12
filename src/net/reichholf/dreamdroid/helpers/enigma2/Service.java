/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2EventHandler;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2ServiceListHandler;

import org.apache.http.NameValuePair;

/**
 * @author sreichholf
 * 
 */
public class Service extends SimpleResult {
	public static final String NAME = "name";
	public static final String REFERENCE = "reference";

	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public static String getList(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.SERVICES, params[0])) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param xml
	 * @param list
	 * @return
	 */
	public static boolean parseList(String xml, ArrayList<ExtendedHashMap> list) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2ServiceListHandler handler = new E2ServiceListHandler(list);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}

	public static String getEpgBouquetList(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.EPG_NOW, params[0])) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param xml
	 * @param list
	 * @return
	 */
	public static boolean parseEpgBouquetList(String xml, ArrayList<ExtendedHashMap> list) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2EventHandler handler = new E2EventHandler(list);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}
}
