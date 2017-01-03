/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2ListHandler;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SimpleHandler;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SimpleListHandler;

import java.util.ArrayList;

/**
 * @author sre
 * 
 */
public class Request {
    public static String get(SimpleHttpClient shc, String uri){
        return get(shc, uri, new ArrayList<NameValuePair>());
    }

	public static String get(SimpleHttpClient shc, String uri, ArrayList<NameValuePair> params) {
		if (shc.fetchPageContent(uri, params)) {
			return shc.getPageContentString();
		}

		return null;
	}

	public static byte[] getBytes(SimpleHttpClient shc, String uri, ArrayList<NameValuePair> params) {
		if (shc.fetchPageContent(uri, params)) {
			return shc.getBytes();
		}
		return new byte[0];
	}
	
	public static byte[] getBytes(SimpleHttpClient shc, String uri) {
		if (shc.fetchPageContent(uri)) {
			return shc.getBytes();
		}

		return new byte[0];
	}

	/**
	 * @param xml
	 * @param result
	 * @param handler
	 * @return
	 */
	public static boolean parse(String xml, ExtendedHashMap result, E2SimpleHandler handler) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());
		handler.setMap(result);
		sdp.getParser().setHandler(handler);
		return sdp.parse(xml);
	}

	/**
	 * @param xml
	 * @param list
	 * @param handler
	 * @return
	 */
	public static boolean parseList(String xml, ArrayList<ExtendedHashMap> list, E2ListHandler handler) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());
		handler.setList(list);
		sdp.setHandler(handler);

		return sdp.parse(xml);
	}

	/**
	 * @param xml
	 * @param list
	 * @param handler
	 * @return
	 */
	public static boolean parseList(String xml, ArrayList<String> list, E2SimpleListHandler handler) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());
		handler.setList(list);
		sdp.setHandler(handler);

		return sdp.parse(xml);
	}
}
