/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */


package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Service;

/**
 * @author sreichholf
 *
 */
public class E2ServiceListHandler extends DefaultHandler {

	private boolean inService;
	private boolean inReference;
	private boolean inName;

	private ExtendedHashMap mService;
	private ArrayList<ExtendedHashMap> mServicelist;

	/**
	 * @param list
	 */
	public E2ServiceListHandler(ArrayList<ExtendedHashMap> list) {
		mServicelist = list;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals("e2service")) {
			inService = true;
			mService = new ExtendedHashMap();
		} else if (localName.equals("e2servicereference")) {
			inReference = true;
		} else if (localName.equals("e2servicename")) {
			inName = true;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if (localName.equals("e2service")) {
			inService = false;
			mServicelist.add(mService);
		} else if (localName.equals("e2servicereference")) {
			inReference = false;
		} else if (localName.equals("e2servicename")) {
			inName = false;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inService) {
			if (inReference) {
				mService.putOrConcat(Service.REFERENCE, value);
			} else if (inName) {
				mService.putOrConcat(Service.NAME, value.replaceAll("\\p{Cntrl}", ""));
			}
		}
	}
}
