/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceInfo;

/**
 * @author sreichholf
 * 
 */
public class E2DeviceInfoHandler extends DefaultHandler {

	public E2DeviceInfoHandler(ExtendedHashMap map) {
		mDeviceInfo = map;
	}

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

	private ExtendedHashMap mDeviceInfo;
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
	 * @see org.xml.sax.helpers.DefaultHandler#endDocument()
	 */
	@Override
	public void endDocument() throws SAXException {
		// TODO ???
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName,
			String qName, Attributes attrs) {
		if (localName.equals("e2enigmaversion")) {
			inGuiV = true;
		} else if (localName.equals("e2imageversion")) {
			inImageV = true;
		} else if (localName.equals("e2webifversion")) {
			inInterfaceV = true;
		} else if (localName.equals("e2fpversion")) {
			inFpV = true;
		} else if (localName.equals("e2devicename")) {
			inDeviceName = true;
		} else if (localName.equals("e2frontend")) {
			inFrontend = true;
			mFrontendData = new ExtendedHashMap();
		} else if (localName.equals("e2name")) {
			inName = true;
		} else if (localName.equals("e2model")) {
			inModel = true;
		} else if (localName.equals("e2interface")) {
			inInterface = true;
			mNicData = new ExtendedHashMap();
		} else if (localName.equals("e2mac")) {
			inMac = true;
		} else if (localName.equals("e2dhcp")) {
			inDhcp = true;
		} else if (localName.equals("e2ip")) {
			inIp = true;
		} else if (localName.equals("e2gateway")) {
			inGateway = true;
		} else if (localName.equals("e2netmask")) {
			inNetmask = true;
		} else if (localName.equals("e2hdd")) {
			inHdd = true;
			mHddData = new ExtendedHashMap();
		} else if (localName.equals("e2capacity")) {
			inCapacity = true;
		} else if (localName.equals("e2free")) {
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
			if (localName.equals("e2enigmaversion")) {
				inGuiV = false;

			} else if (localName.equals("e2imageversion")) {
				inImageV = false;

			} else if (localName.equals("e2webifversion")) {
				inInterfaceV = false;

			} else if (localName.equals("e2fpversion")) {
				inFpV = false;

			} else if (localName.equals("e2devicename")) {
				inDeviceName = false;

			} else if (localName.equals("e2frontend")) {
				inFrontend = false;
				mFrontends.add(mFrontendData);

			} else if (localName.equals("e2frontends")) {
				mDeviceInfo.putOrConcat(DeviceInfo.FRONTENDS, mFrontends);

			} else if (localName.equals("e2name")) {
				inName = false;

			} else if (localName.equals("e2model")) {
				inModel = false;

			} else if (localName.equals("e2interface")) {
				inInterface = false;
				mNics.add(mNicData);

			} else if (localName.equals("e2network")) {
				mDeviceInfo.putOrConcat(DeviceInfo.NICS, mNics);

			} else if (localName.equals("e2mac")) {
				inMac = false;

			} else if (localName.equals("e2dhcp")) {
				inDhcp = false;

			} else if (localName.equals("e2ip")) {
				inIp = false;

			} else if (localName.equals("e2gateway")) {
				inGateway = false;

			} else if (localName.equals("e2netmask")) {
				inNetmask = false;

			} else if (localName.equals("e2hdd")) {
				inHdd = false;
				mHdds.add(mHddData);

			} else if (localName.equals("e2hdds")) {
				mDeviceInfo.putOrConcat(DeviceInfo.HDDS, mHdds);

			} else if (localName.equals("e2capacity")) {
				inCapacity = false;

			} else if (localName.equals("e2free")) {
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
			mDeviceInfo.putOrConcat(DeviceInfo.GUI_VERSION, value.trim());
		} else if (inImageV) {
			mDeviceInfo.putOrConcat(DeviceInfo.IMAGE_VERSION, value.trim());
		} else if (inInterfaceV) {
			mDeviceInfo.putOrConcat(DeviceInfo.INTERFACE_VERSION, value.trim());
		} else if (inFpV) {
			mDeviceInfo.putOrConcat(DeviceInfo.FRONT_PROCESSOR_VERSION, value
					.trim());
		} else if (inDeviceName) {
			mDeviceInfo.putOrConcat(DeviceInfo.DEVICE_NAME, value.trim());
		} else if (inFrontend) {
			if (inName) {
				mFrontendData
						.putOrConcat(DeviceInfo.FRONTEND_NAME, value.trim());
			} else if (inModel) {
				mFrontendData.putOrConcat(DeviceInfo.FRONTEND_MODEL, value
						.trim());
			}

		} else if (inInterface) {
			if (inName) {
				mNicData.putOrConcat(DeviceInfo.NIC_NAME, value.trim());
			} else if (inMac) {
				mNicData.putOrConcat(DeviceInfo.NIC_MAC, value.trim());
			} else if (inDhcp) {
				mNicData.putOrConcat(DeviceInfo.NIC_DHCP, value.trim());
			} else if (inIp) {
				mNicData.putOrConcat(DeviceInfo.NIC_IP, value.trim());
			} else if (inGateway) {
				mNicData.putOrConcat(DeviceInfo.NIC_GATEWAY, value.trim());
			} else if (inNetmask) {
				mNicData.putOrConcat(DeviceInfo.NIC_NETMASK, value.trim());
			}

		} else if (inHdd) {
			if (inModel) {
				mHddData.putOrConcat(DeviceInfo.HDD_MODEL, value.trim());
			} else if (inCapacity) {
				mHddData.putOrConcat(DeviceInfo.HDD_CAPACITY, value.trim());
			} else if (inFree) {
				mHddData.putOrConcat(DeviceInfo.HDD_FREE_SPACE, value.trim());
			}
		}
	}
}
