/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2EventHandler;

import org.apache.http.NameValuePair;

/**
 * @author sreichholf
 * 
 */
public class Event {	
	public static final String EVENT_ID = "eventid";
	public static final String EVENT_NAME = "eventname";
	public static final String EVENT_START = "eventstart";
	public static final String EVENT_START_READABLE = "eventstart_readable";
	public static final String EVENT_START_TIME_READABLE = "eventstarttime_readable";
	public static final String EVENT_DURATION = "eventduration";
	public static final String EVENT_DURATION_READABLE = "eventduration_readable";
	public static final String EVENT_REMAINING = "eventremaining";
	public static final String EVENT_REMAINING_READABLE = "eventremaining_readable";
	public static final String CURRENT_TIME = "currenttime";
	public static final String EVENT_TITLE = "eventtitle";
	public static final String EVENT_DESCRIPTION = "eventdescription";
	public static final String EVENT_DESCRIPTION_EXTENDED = "eventdescriptionextended";
	public static final String SERVICE_REFERENCE = "reference";
	public static final String SERVICE_NAME = "name";
	
	
	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public static String getList(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.EPG_SERVICE, params[0])) {
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

		E2EventHandler handler = new E2EventHandler(list);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}
	
	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public static String search(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.EPG_SEARCH, params[0])){
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param event
	 */
	public static void supplementReadables(ExtendedHashMap event) {
		String eventstart = event.getString(EVENT_START);

		if (!Python.NONE.equals(eventstart) && eventstart != null) {

			String start = DateTime.getDateTimeString(eventstart);
			String starttime = DateTime.getTimeString(eventstart);
			String duration = DateTime.getDurationString(event.getString(EVENT_DURATION), eventstart);

			event.put(EVENT_START_READABLE, start);
			event.put(EVENT_START_TIME_READABLE, starttime);
			event.put(EVENT_DURATION_READABLE, duration);
		}

		String eventtitle = event.getString(EVENT_TITLE);
		if (Python.NONE.equals(eventtitle) || eventtitle == null) {
			event.put(EVENT_TITLE, "N/A");
		}
	}
}
