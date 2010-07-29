/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author sreichholf
 * 
 */
public class E2TimerHandler extends DefaultHandler {

	private boolean inTimer = false;
	private boolean inServicename = false;
	private boolean inReference = false;
	private boolean inEit = false;
	private boolean inName = false;
	private boolean inDescription = false;
	private boolean inDescriptionEx = false;
	private boolean inDisabled = false;
	private boolean inBegin = false;
	private boolean inEnd = false;
	private boolean inDuration = false;
	private boolean inStartPrepare = false;
	private boolean inJustPlay = false;
	private boolean inAfterevent = false;
	private boolean inLocation = false;
	private boolean inTags = false;
	private boolean inLogEntries = false;
	private boolean inFilename = false;
	private boolean inBackoff = false;
	private boolean inNextActivation = false;
	private boolean inFirstTryPrepare = false;
	private boolean inState = false;
	private boolean inRepeated = false;
	private boolean inDontSave = false;
	private boolean inCanceled = false;
	private boolean inToggleDisabled = false;

	private ArrayList<ExtendedHashMap> mTimerlist;
	private ExtendedHashMap mTimer;

	/**
	 * @param list
	 */
	public E2TimerHandler(ArrayList<ExtendedHashMap> list) {
		mTimerlist = list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startDocument()
	 */
	@Override
	public void startDocument() {
		// TODO ???
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
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals("e2timer")) {
			inTimer = true;
			mTimer = new ExtendedHashMap();
		} else if (localName.equals("e2servicereference")) {
			inReference = true;
		} else if (localName.equals("e2servicename")) {
			inServicename = true;
		} else if (localName.equals("e2eit")) {
			inEit = true;
		} else if (localName.equals("e2name")) {
			inName = true;
		} else if (localName.equals("e2description")) {
			inDescription = true;
		} else if (localName.equals("e2descriptionextended")) {
			inDescriptionEx = true;
		} else if (localName.equals("e2disabled")) {
			inDisabled = true;
		} else if (localName.equals("e2timebegin")) {
			inBegin = true;
		} else if (localName.equals("e2timeend")) {
			inEnd = true;
		} else if (localName.equals("e2duration")) {
			inDuration = true;
		} else if (localName.equals("e2startprepare")) {
			inStartPrepare = true;
		} else if (localName.equals("e2justplay")) {
			inJustPlay = true;
		} else if (localName.equals("e2afterevent")) {
			inAfterevent = true;
		} else if (localName.equals("e2location")) {
			inLocation = true;
		} else if (localName.equals("e2tags")) {
			inTags = true;
		} else if (localName.equals("e2logentries")) {
			inLogEntries = true;
		} else if (localName.equals("e2filename")) {
			inFilename = true;
		} else if (localName.equals("e2backoff")) {
			inBackoff = true;
		} else if (localName.equals("e2nextactivation")) {
			inNextActivation = true;
		} else if (localName.equals("e2firsttryprepare")) {
			inFirstTryPrepare = true;
		} else if (localName.equals("e2state")) {
			inState = true;
		} else if (localName.equals("e2repeated")) {
			inRepeated = true;
		} else if (localName.equals("e2dontsave")) {
			inDontSave = true;
		} else if (localName.equals("e2cancled")) {
			inCanceled = true;
		} else if (localName.equals("e2toggledisabled")) {
			inToggleDisabled = true;
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
		if (localName.equals("e2timer")) {
			inTimer = false;
			mTimerlist.add(mTimer);
		} else if (localName.equals("e2servicereference")) {
			inReference = false;
		} else if (localName.equals("e2servicename")) {
			inServicename = false;
		} else if (localName.equals("e2eit")) {
			inEit = false;
		} else if (localName.equals("e2name")) {
			inName = false;
		} else if (localName.equals("e2description")) {
			inDescription = false;
		} else if (localName.equals("e2descriptionextended")) {
			inDescriptionEx = false;
		} else if (localName.equals("e2disabled")) {
			inDisabled = false;
		} else if (localName.equals("e2timebegin")) {
			inBegin = false;
		} else if (localName.equals("e2timeend")) {
			inEnd = false;
		} else if (localName.equals("e2duration")) {
			inDuration = false;
		} else if (localName.equals("e2startprepare")) {
			inStartPrepare = false;
		} else if (localName.equals("e2justplay")) {
			inJustPlay = false;
		} else if (localName.equals("e2afterevent")) {
			inAfterevent = false;
		} else if (localName.equals("e2location")) {
			inLocation = false;
		} else if (localName.equals("e2tags")) {
			inTags = false;
		} else if (localName.equals("e2logentries")) {
			inLogEntries = false;
		} else if (localName.equals("e2filename")) {
			inFilename = false;
		} else if (localName.equals("e2backoff")) {
			inBackoff = false;
		} else if (localName.equals("e2nextactivation")) {
			inNextActivation = false;
		} else if (localName.equals("e2firsttryprepare")) {
			inFirstTryPrepare = false;
		} else if (localName.equals("e2state")) {
			inState = false;
		} else if (localName.equals("e2repeated")) {
			inRepeated = false;
		} else if (localName.equals("e2dontsave")) {
			inDontSave = false;
		} else if (localName.equals("e2cancled")) {
			inCanceled = false;
		} else if (localName.equals("e2toggledisabled")) {
			inToggleDisabled = false;
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

		if (inTimer) {
			if (inReference) {
				mTimer.putOrConcat(Timer.REFERENCE, value);
			} else if (inServicename) {
				mTimer.putOrConcat(Timer.SERVICE_NAME, value);
			} else if (inEit) {
				mTimer.put(Timer.EIT, value);
			} else if (inName) {
				mTimer.putOrConcat(Timer.NAME, value);
			} else if (inDescription) {
				mTimer.putOrConcat(Timer.DESCRIPTION, value);
			} else if (inDescriptionEx) {
				mTimer.putOrConcat(Timer.DESCRIPTION_EXTENDED, value);
			} else if (inDisabled) {
				mTimer.put(Timer.DISABLED, value);
			} else if (inBegin) {
				mTimer.put(Timer.BEGIN, value);
				mTimer.put(Timer.BEGIN_READEABLE, DateTime.getYearDateTimeString(value));
			} else if (inEnd) {
				mTimer.put(Timer.END, value);
				mTimer.put(Timer.END_READABLE, DateTime.getYearDateTimeString(value));
			} else if (inDuration) {
				mTimer.put(Timer.DURATION, value);
				mTimer.put(Timer.DURATION_READABLE, DateTime.getDurationString(value, null));
			} else if (inStartPrepare) {
				mTimer.put(Timer.START_PREPARE, value);
			} else if (inJustPlay) {
				mTimer.put(Timer.JUST_PLAY, value);
			} else if (inAfterevent) {
				mTimer.put(Timer.AFTER_EVENT, value);
			} else if (inLocation) {
				mTimer.putOrConcat(Timer.LOCATION, value);
			} else if (inTags) {
				mTimer.putOrConcat(Timer.TAGS, value);
			} else if (inLogEntries) {
				mTimer.putOrConcat(Timer.LOG_ENTRIES, value);
			} else if (inFilename) {
				mTimer.putOrConcat(Timer.FILE_NAME, value);
			} else if (inBackoff) {
				mTimer.put(Timer.BACK_OFF, value);
			} else if (inNextActivation) {
				mTimer.put(Timer.NEXT_ACTIVATION, value);
			} else if (inFirstTryPrepare) {
				mTimer.put(Timer.FIRST_TRY_PREPARE, value);
			} else if (inState) {
				mTimer.put(Timer.STATE, value);
			} else if (inRepeated) {
				mTimer.put(Timer.REPEATED, value);
			} else if (inDontSave) {
				mTimer.put(Timer.DONT_SAVE, value);
			} else if (inCanceled) {
				mTimer.put(Timer.CANCELED, value);
			} else if (inToggleDisabled) {
				mTimer.put(Timer.TOGGLE_DISABLED, value);
			}
		}
	}

}
