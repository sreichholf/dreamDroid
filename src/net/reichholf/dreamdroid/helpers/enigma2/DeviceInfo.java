package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2DeviceInfoHandler;

import org.apache.http.NameValuePair;

public class DeviceInfo {
	public static final String GUI_VERSION = "guiversion";
	public static final String IMAGE_VERSION = "imageversion";
	public static final String INTERFACE_VERSION = "interfaceversion";
	public static final String FRONT_PROCESSOR_VERSION = "fpversion";
	public static final String DEVICE_NAME = "devicename";
	public static final String FRONTENDS = "frontends";
	public static final String FRONTEND_NAME = "name";
	public static final String FRONTEND_MODEL = "model";
	public static final String NICS = "nics";
	public static final String NIC_NAME = "name";
	public static final String NIC_MAC = "mac";
	public static final String NIC_DHCP = "dhcp";
	public static final String NIC_IP = "ip";
	public static final String NIC_GATEWAY = "gateway";
	public static final String NIC_NETMASK = "netmask";
	public static final String HDDS = "hdds";
	public static final String HDD_MODEL = "model";
	public static final String HDD_CAPACITY = "capacity";
	public static final String HDD_FREE_SPACE = "free";

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
