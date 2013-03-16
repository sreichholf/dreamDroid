/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;

import org.xml.sax.Attributes;

public class E2SimpleResultHandler extends E2SimpleHandler {

	protected static final String TAG_E2RESULTTEXT = "e2resulttext";
	protected static final String TAG_E2STATETEXT = "e2statetext";
	protected static final String TAG_E2RESULT = "e2result";
	protected static final String TAG_E2STATE = "e2state";

	private boolean inState;
	private boolean inStateText;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals(TAG_E2STATE) || localName.equals(TAG_E2RESULT)) {
			inState = true;
		} else if (localName.equals(TAG_E2STATETEXT) || localName.equals(TAG_E2RESULTTEXT)) {
			inStateText = true;
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
		if (localName.equals(TAG_E2STATE) || localName.equals(TAG_E2RESULT)) {
			inState = false;
		} else if (localName.equals(TAG_E2STATETEXT) || localName.equals(TAG_E2RESULTTEXT)) {
			inStateText = false;
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
			mResult.putOrConcat(SimpleResult.KEY_STATE, value);
		} else if (inStateText) {
			mResult.putOrConcat(SimpleResult.KEY_STATE_TEXT, value);
		}
	}
}
