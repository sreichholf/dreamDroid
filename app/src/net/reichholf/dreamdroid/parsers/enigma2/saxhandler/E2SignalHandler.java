/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.enigma2.Signal;

import org.xml.sax.Attributes;

public class E2SignalHandler extends E2SimpleHandler {

	protected static final String TAG_E2SNRDB = "e2snrdb";
	protected static final String TAG_E2SNR = "e2snr";
	protected static final String TAG_E2BER = "e2ber";
	protected static final String TAG_E2AGC = "e2acg";

	private boolean inSnrdb;
	private boolean inSnr;
	private boolean inBer;
	private boolean inAgc;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		switch (localName) {
			case TAG_E2SNRDB:
				inSnrdb = true;
				break;
			case TAG_E2SNR:
				inSnr = true;
				break;
			case TAG_E2BER:
				inBer = true;
				break;
			case TAG_E2AGC:
				inAgc = true;
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
			case TAG_E2SNRDB:
				inSnrdb = false;
				break;
			case TAG_E2SNR:
				inSnr = false;
				break;
			case TAG_E2BER:
				inBer = false;
				break;
			case TAG_E2AGC:
				inAgc = false;
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

		if (inSnrdb) {
			mResult.putOrConcat(Signal.KEY_SNRDB, value);
		} else if (inSnr) {
			mResult.putOrConcat(Signal.KEY_SNR, value);
		} else if (inBer) {
			mResult.putOrConcat(Signal.KEY_BER, value);
		} else if (inAgc) {
			mResult.putOrConcat(Signal.KEY_AGC, value);
		}
	}
}
