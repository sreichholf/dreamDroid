/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class E2SleepTimerHandler extends DefaultHandler{
	private boolean inSleeptimer = false;
	private boolean inEnabled = false;
	private boolean inMinutes = false;
	private boolean inAction = false;
	private boolean inText = false;

	private ExtendedHashMap mResult;

	/**
	 * @param list
	 */
	public E2SleepTimerHandler(ExtendedHashMap res) {
		mResult = res;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if(localName.equals("e2sleeptimer")){
			inSleeptimer = true;
		} else if (inSleeptimer){
			if(localName.equals("e2enabled")){
				inEnabled = true;
			} else if(localName.equals("e2minutes")){
				inMinutes = true;
			} else if(localName.equals("e2action")){
				inAction = true;
			} else if(localName.equals("e2text")){
				inText = true;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if(localName.equals("e2sleeptimer")){
			inSleeptimer = false;
		} else if (inSleeptimer){
			if(localName.equals("e2enabled")){
				inEnabled = false;
			} else if(localName.equals("e2minutes")){
				inMinutes = false;
			} else if(localName.equals("e2action")){
				inAction = false;
			} else if(localName.equals("e2text")){
				inText = false;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inEnabled) {
			mResult.putOrConcat(SleepTimer.ENABLED, value);
		} else if(inMinutes){
			mResult.putOrConcat(SleepTimer.MINUTES, value);
		} else if(inAction){
			mResult.putOrConcat(SleepTimer.ACTION, value);
		} else if(inText){
			mResult.putOrConcat(SleepTimer.TEXT, value);
		}
	}
}
