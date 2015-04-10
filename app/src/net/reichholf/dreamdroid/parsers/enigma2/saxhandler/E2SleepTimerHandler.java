/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;

import org.xml.sax.Attributes;

public class E2SleepTimerHandler extends E2SimpleHandler {

	protected static final String TAG_E2SLEEPTIMER = "e2sleeptimer";
	protected static final String TAG_E2ENABLED = "e2enabled";
	protected static final String TAG_E2MINUTES = "e2minutes";
	protected static final String TAG_E2ACTION = "e2action";
	protected static final String TAG_E2TEXT = "e2text";

	private boolean inSleeptimer = false;
	private boolean inEnabled = false;
	private boolean inMinutes = false;
	private boolean inAction = false;
	private boolean inText = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals(TAG_E2SLEEPTIMER)) {
			inSleeptimer = true;
		} else if (inSleeptimer) {
			switch (localName) {
				case TAG_E2ENABLED:
					inEnabled = true;
					break;
				case TAG_E2MINUTES:
					inMinutes = true;
					break;
				case TAG_E2ACTION:
					inAction = true;
					break;
				case TAG_E2TEXT:
					inText = true;
					break;
			}
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
		if (localName.equals(TAG_E2SLEEPTIMER)) {
			inSleeptimer = false;
		} else if (inSleeptimer) {
			switch (localName) {
				case TAG_E2ENABLED:
					inEnabled = false;
					break;
				case TAG_E2MINUTES:
					inMinutes = false;
					break;
				case TAG_E2ACTION:
					inAction = false;
					break;
				case TAG_E2TEXT:
					inText = false;
					break;
			}
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

		if (inEnabled) {
			mResult.putOrConcat(SleepTimer.KEY_ENABLED, value);
		} else if (inMinutes) {
			mResult.putOrConcat(SleepTimer.KEY_MINUTES, value);
		} else if (inAction) {
			mResult.putOrConcat(SleepTimer.KEY_ACTION, value);
		} else if (inText) {
			mResult.putOrConcat(SleepTimer.KEY_TEXT, value);
		}
	}
}
