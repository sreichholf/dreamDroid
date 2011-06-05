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
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author sreichholf
 * 
 */
public class E2TimerHandler extends DefaultHandler {

	protected static final String TAG_E2TIMER = "e2timer";
	protected static final String TAG_E2SERVICEREFERENCE = "e2servicereference";
	protected static final String TAG_E2SERVICENAME = "e2servicename";
	protected static final String TAG_E2EIT = "e2eit";
	protected static final String TAG_E2NAME = "e2name";
	protected static final String TAG_E2DESCRIPTION = "e2description";
	protected static final String TAG_E2DESCRIPTIONEXTENDED = "e2descriptionextended";
	protected static final String TAG_E2DISABLED = "e2disabled";
	protected static final String TAG_E2TIMEBEGIN = "e2timebegin";
	protected static final String TAG_E2TIMEEND = "e2timeend";
	protected static final String TAG_E2DURATION = "e2duration";
	protected static final String TAG_E2STARTPREPARE = "e2startprepare";
	protected static final String TAG_E2JUSTPLAY = "e2justplay";
	protected static final String TAG_E2AFTEREVENT = "e2afterevent";
	protected static final String TAG_E2LOCATION = "e2location";
	protected static final String TAG_E2TAGS = "e2tags";
	protected static final String TAG_E2LOGENTRIES = "e2logentries";
	protected static final String TAG_E2FILENAME = "e2filename";
	protected static final String TAG_E2BACKOFF = "e2backoff";
	protected static final String TAG_E2NEXTACTIVATION = "e2nextactivation";
	protected static final String TAG_E2FIRSTTRYPREPARE = "e2firsttryprepare";
	protected static final String TAG_E2STATE = "e2state";
	protected static final String TAG_E2REPEATED = "e2repeated";
	protected static final String TAG_E2DONTSAVE = "e2dontsave";
	protected static final String TAG_E2CANCLED = "e2cancled";
	protected static final String TAG_E2TOGGLEDISABLED = "e2toggledisabled";

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
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals(TAG_E2TIMER)) {
			inTimer = true;
			mTimer = new ExtendedHashMap();
		} else if (localName.equals(TAG_E2SERVICEREFERENCE)) {
			inReference = true;
		} else if (localName.equals(TAG_E2SERVICENAME)) {
			inServicename = true;
		} else if (localName.equals(TAG_E2EIT)) {
			inEit = true;
		} else if (localName.equals(TAG_E2NAME)) {
			inName = true;
		} else if (localName.equals(TAG_E2DESCRIPTION)) {
			inDescription = true;
		} else if (localName.equals(TAG_E2DESCRIPTIONEXTENDED)) {
			inDescriptionEx = true;
		} else if (localName.equals(TAG_E2DISABLED)) {
			inDisabled = true;
		} else if (localName.equals(TAG_E2TIMEBEGIN)) {
			inBegin = true;
		} else if (localName.equals(TAG_E2TIMEEND)) {
			inEnd = true;
		} else if (localName.equals(TAG_E2DURATION)) {
			inDuration = true;
		} else if (localName.equals(TAG_E2STARTPREPARE)) {
			inStartPrepare = true;
		} else if (localName.equals(TAG_E2JUSTPLAY)) {
			inJustPlay = true;
		} else if (localName.equals(TAG_E2AFTEREVENT)) {
			inAfterevent = true;
		} else if (localName.equals(TAG_E2LOCATION)) {
			inLocation = true;
		} else if (localName.equals(TAG_E2TAGS)) {
			inTags = true;
		} else if (localName.equals(TAG_E2LOGENTRIES)) {
			inLogEntries = true;
		} else if (localName.equals(TAG_E2FILENAME)) {
			inFilename = true;
		} else if (localName.equals(TAG_E2BACKOFF)) {
			inBackoff = true;
		} else if (localName.equals(TAG_E2NEXTACTIVATION)) {
			inNextActivation = true;
		} else if (localName.equals(TAG_E2FIRSTTRYPREPARE)) {
			inFirstTryPrepare = true;
		} else if (localName.equals(TAG_E2STATE)) {
			inState = true;
		} else if (localName.equals(TAG_E2REPEATED)) {
			inRepeated = true;
		} else if (localName.equals(TAG_E2DONTSAVE)) {
			inDontSave = true;
		} else if (localName.equals(TAG_E2CANCLED)) {
			inCanceled = true;
		} else if (localName.equals(TAG_E2TOGGLEDISABLED)) {
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
		if (localName.equals(TAG_E2TIMER)) {
			inTimer = false;
			mTimerlist.add(mTimer);
		} else if (localName.equals(TAG_E2SERVICEREFERENCE)) {
			inReference = false;
		} else if (localName.equals(TAG_E2SERVICENAME)) {
			inServicename = false;
		} else if (localName.equals(TAG_E2EIT)) {
			inEit = false;
		} else if (localName.equals(TAG_E2NAME)) {
			inName = false;
		} else if (localName.equals(TAG_E2DESCRIPTION)) {
			inDescription = false;
		} else if (localName.equals(TAG_E2DESCRIPTIONEXTENDED)) {
			inDescriptionEx = false;
		} else if (localName.equals(TAG_E2DISABLED)) {
			inDisabled = false;
		} else if (localName.equals(TAG_E2TIMEBEGIN)) {
			inBegin = false;
		} else if (localName.equals(TAG_E2TIMEEND)) {
			inEnd = false;
		} else if (localName.equals(TAG_E2DURATION)) {
			inDuration = false;
		} else if (localName.equals(TAG_E2STARTPREPARE)) {
			inStartPrepare = false;
		} else if (localName.equals(TAG_E2JUSTPLAY)) {
			inJustPlay = false;
		} else if (localName.equals(TAG_E2AFTEREVENT)) {
			inAfterevent = false;
		} else if (localName.equals(TAG_E2LOCATION)) {
			inLocation = false;
		} else if (localName.equals(TAG_E2TAGS)) {
			inTags = false;
		} else if (localName.equals(TAG_E2LOGENTRIES)) {
			inLogEntries = false;
		} else if (localName.equals(TAG_E2FILENAME)) {
			inFilename = false;
		} else if (localName.equals(TAG_E2BACKOFF)) {
			inBackoff = false;
		} else if (localName.equals(TAG_E2NEXTACTIVATION)) {
			inNextActivation = false;
		} else if (localName.equals(TAG_E2FIRSTTRYPREPARE)) {
			inFirstTryPrepare = false;
		} else if (localName.equals(TAG_E2STATE)) {
			inState = false;
		} else if (localName.equals(TAG_E2REPEATED)) {
			inRepeated = false;
		} else if (localName.equals(TAG_E2DONTSAVE)) {
			inDontSave = false;
		} else if (localName.equals(TAG_E2CANCLED)) {
			inCanceled = false;
		} else if (localName.equals(TAG_E2TOGGLEDISABLED)) {
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
				mTimer.putOrConcat(Timer.KEY_REFERENCE, value);
			} else if (inServicename) {
				mTimer.putOrConcat(Timer.KEY_SERVICE_NAME, value.replaceAll("\\p{Cntrl}", ""));
			} else if (inEit) {
				mTimer.put(Timer.KEY_EIT, value);
			} else if (inName) {
				mTimer.putOrConcat(Timer.KEY_NAME, value);
			} else if (inDescription) {
				mTimer.putOrConcat(Timer.KEY_DESCRIPTION, value);
			} else if (inDescriptionEx) {
				mTimer.putOrConcat(Timer.KEY_DESCRIPTION_EXTENDED, value);
			} else if (inDisabled) {
				mTimer.put(Timer.KEY_DISABLED, value);
			} else if (inBegin) {
				mTimer.put(Timer.KEY_BEGIN, value);
				mTimer.put(Timer.KEY_BEGIN_READEABLE, DateTime.getYearDateTimeString(value));
			} else if (inEnd) {
				mTimer.put(Timer.KEY_END, value);
				mTimer.put(Timer.KEY_END_READABLE, DateTime.getYearDateTimeString(value));
			} else if (inDuration) {
				mTimer.put(Timer.KEY_DURATION, value);
				mTimer.put(Timer.KEY_DURATION_READABLE, DateTime.getDurationString(value, null));
			} else if (inStartPrepare) {
				mTimer.put(Timer.KEY_START_PREPARE, value);
			} else if (inJustPlay) {
				mTimer.put(Timer.KEY_JUST_PLAY, value);
			} else if (inAfterevent) {
				mTimer.put(Timer.KEY_AFTER_EVENT, value);
			} else if (inLocation) {
				mTimer.putOrConcat(Timer.KEY_LOCATION, value);
			} else if (inTags) {
				mTimer.putOrConcat(Timer.KEY_TAGS, value);
			} else if (inLogEntries) {
				mTimer.putOrConcat(Timer.KEY_LOG_ENTRIES, value);
			} else if (inFilename) {
				mTimer.putOrConcat(Timer.KEY_FILE_NAME, value);
			} else if (inBackoff) {
				mTimer.put(Timer.KEY_BACK_OFF, value);
			} else if (inNextActivation) {
				mTimer.put(Timer.KEY_NEXT_ACTIVATION, value);
			} else if (inFirstTryPrepare) {
				mTimer.put(Timer.KEY_FIRST_TRY_PREPARE, value);
			} else if (inState) {
				mTimer.put(Timer.KEY_STATE, value);
			} else if (inRepeated) {
				mTimer.put(Timer.KEY_REPEATED, value);
			} else if (inDontSave) {
				mTimer.put(Timer.KEY_DONT_SAVE, value);
			} else if (inCanceled) {
				mTimer.put(Timer.KEY_CANCELED, value);
			} else if (inToggleDisabled) {
				mTimer.put(Timer.KEY_TOGGLE_DISABLED, value);
			}
		}
	}

}
