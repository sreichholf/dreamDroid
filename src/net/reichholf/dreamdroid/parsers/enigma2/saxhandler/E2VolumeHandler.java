/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Volume;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class E2VolumeHandler extends DefaultHandler{
	private boolean inResult;
	private boolean inCurrent;
	private boolean inMuted;

	private ExtendedHashMap mResult;

	/**
	 * @param list
	 */
	public E2VolumeHandler(ExtendedHashMap res) {
		mResult = res;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals("e2result")) {
			inResult = true;
		} else if (localName.equals("e2current")) {
			inCurrent = true;
		} else if (localName.equals("e2ismuted")) {
			inMuted = true;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if (localName.equals("e2result")) {
			inResult = false;
		} else if (localName.equals("e2current")) {
			inCurrent = false;
		} else if (localName.equals("e2ismuted")) {
			inMuted = false;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inResult) {
			mResult.putOrConcat(Volume.RESULT, value);
		} else if(inCurrent){
			mResult.putOrConcat(Volume.CURRENT, value);
		} else if(inMuted){
			mResult.putOrConcat(Volume.MUTED, value);
		}
	}
}
