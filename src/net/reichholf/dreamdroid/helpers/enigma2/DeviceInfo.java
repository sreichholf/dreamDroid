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
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2DeviceInfoHandler;

import org.apache.http.NameValuePair;

public class DeviceInfo {
	public static final String KEY_GUI_VERSION = "guiversion";
	public static final String KEY_IMAGE_VERSION = "imageversion";
	public static final String KEY_INTERFACE_VERSION = "interfaceversion";
	public static final String KEY_FRONT_PROCESSOR_VERSION = "fpversion";
	public static final String KEY_DEVICE_NAME = "devicename";
	public static final String KEY_FRONTENDS = "frontends";
	public static final String KEY_FRONTEND_NAME = "name";
	public static final String KEY_FRONTEND_MODEL = "model";
	public static final String KEY_NICS = "nics";
	public static final String KEY_NIC_NAME = "name";
	public static final String KEY_NIC_MAC = "mac";
	public static final String KEY_NIC_DHCP = "dhcp";
	public static final String KEY_NIC_IP = "ip";
	public static final String KEY_NIC_GATEWAY = "gateway";
	public static final String KEY_NIC_NETMASK = "netmask";
	public static final String KEY_HDDS = "hdds";
	public static final String KEY_HDD_MODEL = "model";
	public static final String KEY_HDD_CAPACITY = "capacity";
	public static final String KEY_HDD_FREE_SPACE = "free";

	/**
	 * @param shc
	 * @return
	 */
	public static String get(SimpleHttpClient shc) {
		if (shc.fetchPageContent(URIStore.DEVICE_INFO, new ArrayList<NameValuePair>())) {
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

		E2DeviceInfoHandler handler = new E2DeviceInfoHandler(map);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}

}
