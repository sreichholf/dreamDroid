/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.enigma2.Volume;

import org.xml.sax.Attributes;

public class E2VolumeHandler extends E2SimpleHandler {

	protected static final String TAG_E2ISMUTED = "e2ismuted";
	protected static final String TAG_E2CURRENT = "e2current";
	protected static final String TAG_E2RESULT = "e2result";

	private boolean inResult;
	private boolean inCurrent;
	private boolean inMuted;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		switch (localName) {
			case TAG_E2RESULT:
				inResult = true;
				break;
			case TAG_E2CURRENT:
				inCurrent = true;
				break;
			case TAG_E2ISMUTED:
				inMuted = true;
				break;
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
		switch (localName) {
			case TAG_E2RESULT:
				inResult = false;
				break;
			case TAG_E2CURRENT:
				inCurrent = false;
				break;
			case TAG_E2ISMUTED:
				inMuted = false;
				break;
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

		if (inResult) {
			mResult.putOrConcat(Volume.KEY_RESULT, value);
		} else if (inCurrent) {
			mResult.putOrConcat(Volume.KEY_CURRENT, value);
		} else if (inMuted) {
			mResult.putOrConcat(Volume.KEY_MUTED, value);
		}
	}
}
