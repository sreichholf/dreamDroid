/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceInfo;

import org.xml.sax.Attributes;

/**
 * @author sreichholf
 * 
 */
public class E2DeviceInfoHandler extends E2SimpleHandler {

	protected static final String TAG_E2ENIGMAVERSION = "e2enigmaversion";
	protected static final String TAG_E2IMAGEVERSION = "e2imageversion";
	protected static final String TAG_E2WEBIFVERSION = "e2webifversion";
	protected static final String TAG_E2FPVERSION = "e2fpversion";
	protected static final String TAG_E2DEVICENAME = "e2devicename";
	protected static final String TAG_E2FRONTEND = "e2frontend";
	protected static final String TAG_E2NAME = "e2name";
	protected static final String TAG_E2MODEL = "e2model";
	protected static final String TAG_E2INTERFACE = "e2interface";
	protected static final String TAG_E2MAC = "e2mac";
	protected static final String TAG_E2DHCP = "e2dhcp";
	protected static final String TAG_E2IP = "e2ip";
	protected static final String TAG_E2GATEWAY = "e2gateway";
	protected static final String TAG_E2NETMASK = "e2netmask";
	protected static final String TAG_E2HDD = "e2hdd";
	protected static final String TAG_E2CAPACITY = "e2capacity";
	protected static final String TAG_E2FREE = "e2free";

	private boolean inGuiV = false;
	private boolean inImageV = false;
	private boolean inInterfaceV = false;
	private boolean inFpV = false;
	private boolean inDeviceName = false;
	private boolean inFrontend = false;
	private boolean inName = false;
	private boolean inModel = false;
	private boolean inInterface = false;
	private boolean inMac = false;
	private boolean inDhcp = false;
	private boolean inIp = false;
	private boolean inGateway = false;
	private boolean inNetmask = false;
	private boolean inHdd = false;
	private boolean inCapacity = false;
	private boolean inFree = false;

	private ExtendedHashMap mFrontendData;
	private ExtendedHashMap mHddData;
	private ExtendedHashMap mNicData;

	private ArrayList<ExtendedHashMap> mFrontends;
	private ArrayList<ExtendedHashMap> mHdds;
	private ArrayList<ExtendedHashMap> mNics;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startDocument()
	 */
	@Override
	public void startDocument() {
		mFrontends = new ArrayList<ExtendedHashMap>();
		mHdds = new ArrayList<ExtendedHashMap>();
		mNics = new ArrayList<ExtendedHashMap>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals(TAG_E2ENIGMAVERSION)) {
			inGuiV = true;
		} else if (localName.equals(TAG_E2IMAGEVERSION)) {
			inImageV = true;
		} else if (localName.equals(TAG_E2WEBIFVERSION)) {
			inInterfaceV = true;
		} else if (localName.equals(TAG_E2FPVERSION)) {
			inFpV = true;
		} else if (localName.equals(TAG_E2DEVICENAME)) {
			inDeviceName = true;
		} else if (localName.equals(TAG_E2FRONTEND)) {
			inFrontend = true;
			mFrontendData = new ExtendedHashMap();
		} else if (localName.equals(TAG_E2NAME)) {
			inName = true;
		} else if (localName.equals(TAG_E2MODEL)) {
			inModel = true;
		} else if (localName.equals(TAG_E2INTERFACE)) {
			inInterface = true;
			mNicData = new ExtendedHashMap();
		} else if (localName.equals(TAG_E2MAC)) {
			inMac = true;
		} else if (localName.equals(TAG_E2DHCP)) {
			inDhcp = true;
		} else if (localName.equals(TAG_E2IP)) {
			inIp = true;
		} else if (localName.equals(TAG_E2GATEWAY)) {
			inGateway = true;
		} else if (localName.equals(TAG_E2NETMASK)) {
			inNetmask = true;
		} else if (localName.equals(TAG_E2HDD)) {
			inHdd = true;
			mHddData = new ExtendedHashMap();
		} else if (localName.equals(TAG_E2CAPACITY)) {
			inCapacity = true;
		} else if (localName.equals(TAG_E2FREE)) {
			inFree = true;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		try {
			if (localName.equals(TAG_E2ENIGMAVERSION)) {
				inGuiV = false;

			} else if (localName.equals(TAG_E2IMAGEVERSION)) {
				inImageV = false;

			} else if (localName.equals(TAG_E2WEBIFVERSION)) {
				inInterfaceV = false;

			} else if (localName.equals(TAG_E2FPVERSION)) {
				inFpV = false;

			} else if (localName.equals(TAG_E2DEVICENAME)) {
				inDeviceName = false;

			} else if (localName.equals(TAG_E2FRONTEND)) {
				inFrontend = false;
				mFrontends.add(mFrontendData);

			} else if (localName.equals("e2frontends")) {
				mResult.putOrConcat(DeviceInfo.KEY_FRONTENDS, mFrontends);

			} else if (localName.equals(TAG_E2NAME)) {
				inName = false;

			} else if (localName.equals(TAG_E2MODEL)) {
				inModel = false;

			} else if (localName.equals(TAG_E2INTERFACE)) {
				inInterface = false;
				mNics.add(mNicData);

			} else if (localName.equals("e2network")) {
				mResult.putOrConcat(DeviceInfo.KEY_NICS, mNics);

			} else if (localName.equals(TAG_E2MAC)) {
				inMac = false;

			} else if (localName.equals(TAG_E2DHCP)) {
				inDhcp = false;

			} else if (localName.equals(TAG_E2IP)) {
				inIp = false;

			} else if (localName.equals(TAG_E2GATEWAY)) {
				inGateway = false;

			} else if (localName.equals(TAG_E2NETMASK)) {
				inNetmask = false;

			} else if (localName.equals(TAG_E2HDD)) {
				inHdd = false;
				mHdds.add(mHddData);

			} else if (localName.equals("e2hdds")) {
				mResult.putOrConcat(DeviceInfo.KEY_HDDS, mHdds);

			} else if (localName.equals(TAG_E2CAPACITY)) {
				inCapacity = false;

			} else if (localName.equals(TAG_E2FREE)) {
				inFree = false;

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inGuiV) {
			mResult.putOrConcat(DeviceInfo.KEY_GUI_VERSION, value.trim());
		} else if (inImageV) {
			mResult.putOrConcat(DeviceInfo.KEY_IMAGE_VERSION, value.trim());
		} else if (inInterfaceV) {
			mResult.putOrConcat(DeviceInfo.KEY_INTERFACE_VERSION, value.trim());
		} else if (inFpV) {
			mResult.putOrConcat(DeviceInfo.KEY_FRONT_PROCESSOR_VERSION, value.trim());
		} else if (inDeviceName) {
			mResult.putOrConcat(DeviceInfo.KEY_DEVICE_NAME, value.trim());
		} else if (inFrontend) {
			if (inName) {
				mFrontendData.putOrConcat(DeviceInfo.KEY_FRONTEND_NAME, value.trim());
			} else if (inModel) {
				mFrontendData.putOrConcat(DeviceInfo.KEY_FRONTEND_MODEL, value.trim());
			}

		} else if (inInterface) {
			if (inName) {
				mNicData.putOrConcat(DeviceInfo.KEY_NIC_NAME, value.trim());
			} else if (inMac) {
				mNicData.putOrConcat(DeviceInfo.KEY_NIC_MAC, value.trim());
			} else if (inDhcp) {
				mNicData.putOrConcat(DeviceInfo.KEY_NIC_DHCP, value.trim());
			} else if (inIp) {
				mNicData.putOrConcat(DeviceInfo.KEY_NIC_IP, value.trim());
			} else if (inGateway) {
				mNicData.putOrConcat(DeviceInfo.KEY_NIC_GATEWAY, value.trim());
			} else if (inNetmask) {
				mNicData.putOrConcat(DeviceInfo.KEY_NIC_NETMASK, value.trim());
			}

		} else if (inHdd) {
			if (inModel) {
				mHddData.putOrConcat(DeviceInfo.KEY_HDD_MODEL, value.trim());
			} else if (inCapacity) {
				mHddData.putOrConcat(DeviceInfo.KEY_HDD_CAPACITY, value.trim());
			} else if (inFree) {
				mHddData.putOrConcat(DeviceInfo.KEY_HDD_FREE_SPACE, value.trim());
			}
		}
	}
}
