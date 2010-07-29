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
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2CurrentServiceHandler;

import org.apache.http.NameValuePair;

/**
 * @author sreichholf
 * 
 */
public class CurrentService {
	// Service
	public static final String SERVICE = "service";
	public static final String SERVICE_REFERENCE = "reference";
	public static final String SERVICE_NAME = "name";
	public static final String SERVICE_PROVIDER = "provider";
	public static final String SERVICE_VIDEO_WIDTH = "videowidth";
	public static final String SERVICE_VIDEO_HEIGHT = "videoheight";
	public static final String SERVICE_VIDEO_SIZE = "videosize";
	public static final String SERVICE_IS_WIDESCREEN = "widescreen";
	public static final String SERVICE_APID = "apid";
	public static final String SERVICE_VPID = "vpid";
	public static final String SERVICE_PCRPID = "pcrpid";
	public static final String SERVICE_PMTPID = "pmtpid";
	public static final String SERVICE_TXTPID = "txtpid";
	public static final String SERVICE_TSID = "tsid";
	public static final String SERVICE_ONID = "onid";
	public static final String SERVICE_SID = "sid";
	public static final String EVENTS = "event";

	/**
	 * @param shc
	 * @return
	 */
	public static String get(SimpleHttpClient shc) {
		if (shc.fetchPageContent(URIStore.CURRENT, new ArrayList<NameValuePair>())) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param xml
	 * @param map
	 * @return
	 */
	public static boolean parse(String xml, ExtendedHashMap map) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2CurrentServiceHandler handler = new E2CurrentServiceHandler(map);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}
}
