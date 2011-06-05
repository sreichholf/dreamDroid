/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.PowerState;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class E2PowerStateHandler extends DefaultHandler {

	protected static final String TAG_E2INSTANDBY = "e2instandby";

	private boolean inState;
	private ExtendedHashMap mResult;

	/**
	 * @param list
	 */
	public E2PowerStateHandler(ExtendedHashMap res) {
		mResult = res;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals(TAG_E2INSTANDBY)) {
			inState = true;
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
		if (localName.equals(TAG_E2INSTANDBY)) {
			inState = false;
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

		if (inState) {
			if ("false".equals(value.trim()))
				mResult.put(PowerState.KEY_IN_STANDBY, true);
			else if ("true".equals(value.trim())) {
				mResult.put(PowerState.KEY_IN_STANDBY, false);
			}
		}
	}
}
