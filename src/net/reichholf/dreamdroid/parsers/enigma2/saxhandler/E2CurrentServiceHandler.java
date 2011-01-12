/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.CurrentService;
import net.reichholf.dreamdroid.helpers.enigma2.Event;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author sreichholf
 * 
 */
public class E2CurrentServiceHandler extends DefaultHandler {
	private boolean inService;
	private boolean inServiceReference;
	private boolean inServiceName;
	private boolean inProviderName;
	private boolean inVideoWidth;
	private boolean inVideoHeight;
	private boolean inVideoSize;
	private boolean inIsWideScreen;
	private boolean inApid;
	private boolean inVpid;
	private boolean inPcrPid;
	private boolean inPmtPid;
	private boolean inTxtPid;
	private boolean inTsid;
	private boolean inOnid;
	private boolean inSid;
	private boolean inEvent;
	private boolean inEventServiceReference;
	private boolean inEventServiceName;
	private boolean inEventProviderName;
	private boolean inEventId;
	private boolean inEventName;
	private boolean inEventTitle;
	private boolean inEventDescription;
	private boolean inEventStart;
	private boolean inEventDuration;
	private boolean inEventRemaining;
	private boolean inEventCurrentTime;
	private boolean inEventDescriptionExtended;

	private ExtendedHashMap mService;
	private ExtendedHashMap mEvent;
	private ArrayList<ExtendedHashMap> mEvents;
	private ExtendedHashMap mCurrent;

	/**
	 * @param list
	 */
	public E2CurrentServiceHandler(ExtendedHashMap cur) {
		mCurrent = cur;
		mEvents = new ArrayList<ExtendedHashMap>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals("e2service")) {
			inService = true;
			mService = new ExtendedHashMap();
		} else if (localName.equals("e2servicereference")) {
			inServiceReference = true;
		} else if (localName.equals("e2servicename")) {
			inServiceName = true;
		} else if (localName.equals("e2providername")) {
			inProviderName = true;
		} else if (localName.equals("e2videowidth")) {
			inVideoWidth = true;
		} else if (localName.equals("e2videoheight")) {
			inVideoHeight = true;
		} else if (localName.equals("e2servicevideosize")) {
			inVideoSize = true;
		} else if (localName.equals("e2iswidescreen")) {
			inIsWideScreen = true;
		} else if (localName.equals("e2apid")) {
			inApid = true;
		} else if (localName.equals("e2vpid")) {
			inVpid = true;
		} else if (localName.equals("e2pcrpid")) {
			inPcrPid = true;
		} else if (localName.equals("e2pmtpid")) {
			inPmtPid = true;
		} else if (localName.equals("e2txtpid")) {
			inTxtPid = true;
		} else if (localName.equals("e2tsid")) {
			inTsid = true;
		} else if (localName.equals("e2onid")) {
			inOnid = true;
		} else if (localName.equals("e2sid")) {
			inSid = true;
		} else if (localName.equals("e2event")) {
			inEvent = true;
			mEvent = new ExtendedHashMap();
		} else if (localName.equals("e2eventservicereference")) {
			inEventServiceReference = true;
		} else if (localName.equals("e2eventservicename")) {
			inEventServiceName = true;
		} else if (localName.equals("e2eventprovidername")) {
			inEventProviderName = true;
		} else if (localName.equals("e2eventid")) {
			inEventId = true;
		} else if (localName.equals("e2eventname")) {
			inEventName = true;
		} else if (localName.equals("e2eventtitle")) {
			inEventTitle = true;
		} else if (localName.equals("e2eventdescription")) {
			inEventDescription = true;
		} else if (localName.equals("e2eventstart")) {
			inEventStart = true;
		} else if (localName.equals("e2eventduration")) {
			inEventDuration = true;
		} else if (localName.equals("e2eventremaining")) {
			inEventRemaining = true;
		} else if (localName.equals("e2eventcurrenttime")) {
			inEventCurrentTime = true;
		} else if (localName.equals("e2eventdescriptionextended")) {
			inEventDescriptionExtended = true;
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
		if (localName.equals("e2service")) {
			inService = false;
			mCurrent.put(CurrentService.SERVICE, mService);
		} else if (localName.equals("e2servicereference")) {
			inServiceReference = false;
		} else if (localName.equals("e2servicename")) {
			inServiceName = false;
		} else if (localName.equals("e2providername")) {
			inProviderName = false;
		} else if (localName.equals("e2videowidth")) {
			inVideoWidth = false;
		} else if (localName.equals("e2videoheight")) {
			inVideoHeight = false;
		} else if (localName.equals("e2servicevideosize")) {
			inVideoSize = false;
		} else if (localName.equals("e2iswidescreen")) {
			inIsWideScreen = false;
		} else if (localName.equals("e2apid")) {
			inApid = false;
		} else if (localName.equals("e2vpid")) {
			inVpid = false;
		} else if (localName.equals("e2pcrpid")) {
			inPcrPid = false;
		} else if (localName.equals("e2pmtpid")) {
			inPmtPid = false;
		} else if (localName.equals("e2txtpid")) {
			inTxtPid = false;
		} else if (localName.equals("e2tsid")) {
			inTsid = false;
		} else if (localName.equals("e2onid")) {
			inOnid = false;
		} else if (localName.equals("e2sid")) {
			inSid = false;
		} else if (localName.equals("e2event")) {
			inEvent = false;			
			Event.supplementReadables(mEvent);
			mEvents.add(mEvent);			
		} else if (localName.equals("e2eventservicereference")) {
			inEventServiceReference = false;
		} else if (localName.equals("e2eventservicename")) {
			inEventServiceName = false;
		} else if (localName.equals("e2eventprovidername")) {
			inEventProviderName = false;
		} else if (localName.equals("e2eventid")) {
			inEventId = false;
		} else if (localName.equals("e2eventname")) {
			inEventName = false;
		} else if (localName.equals("e2eventtitle")) {
			inEventTitle = false;
		} else if (localName.equals("e2eventdescription")) {
			inEventDescription = false;
		} else if (localName.equals("e2eventstart")) {
			inEventStart = false;
		} else if (localName.equals("e2eventduration")) {
			inEventDuration = false;
		} else if (localName.equals("e2eventremaining")) {
			inEventRemaining = false;
		} else if (localName.equals("e2eventcurrenttime")) {
			inEventCurrentTime = false;
		} else if (localName.equals("e2eventdescriptionextended")) {
			inEventDescriptionExtended = false;
		} else if (localName.equals("e2currentserviceinformation")){
			mCurrent.put(CurrentService.EVENTS, mEvents);
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

		if (inService) {
			if (inServiceReference) {
				mService.putOrConcat(CurrentService.SERVICE_REFERENCE, value);
			} else if (inServiceName) {
				mService.putOrConcat(CurrentService.SERVICE_NAME, value);
			} else if (inProviderName) {
				mService.putOrConcat(CurrentService.SERVICE_PROVIDER, value);
			} else if (inVideoWidth) {
				mService.putOrConcat(CurrentService.SERVICE_VIDEO_WIDTH, value);
			} else if (inVideoHeight) {
				mService.putOrConcat(CurrentService.SERVICE_VIDEO_HEIGHT, value);
			} else if (inVideoSize) {
				mService.putOrConcat(CurrentService.SERVICE_VIDEO_SIZE, value);
			} else if (inIsWideScreen) {
				mService.putOrConcat(CurrentService.SERVICE_IS_WIDESCREEN, value);
			} else if (inApid) {
				mService.putOrConcat(CurrentService.SERVICE_APID, value);
			} else if (inVpid) {
				mService.putOrConcat(CurrentService.SERVICE_VPID, value);
			} else if (inPcrPid) {
				mService.putOrConcat(CurrentService.SERVICE_PCRPID, value);
			} else if (inPmtPid) {
				mService.putOrConcat(CurrentService.SERVICE_PMTPID, value);
			} else if (inTxtPid) {
				mService.putOrConcat(CurrentService.SERVICE_TXTPID, value);
			} else if (inTsid) {
				mService.putOrConcat(CurrentService.SERVICE_TSID, value);
			} else if (inOnid) {
				mService.putOrConcat(CurrentService.SERVICE_ONID, value);
			} else if (inSid) {
				mService.putOrConcat(CurrentService.SERVICE_SID, value);
			}
		} else if (inEvent) {
			if (inEventServiceReference) {
				mEvent.putOrConcat(Event.SERVICE_REFERENCE, value);
			} else if (inEventServiceName) {
				mEvent.putOrConcat(Event.SERVICE_NAME, value.replaceAll("\\p{Cntrl}", ""));
			} else if (inEventProviderName) {
				// TODO add handling if needed
			} else if (inEventId) {
				mEvent.putOrConcat(Event.EVENT_ID, value);
			} else if (inEventName) {
				mEvent.putOrConcat(Event.EVENT_NAME, value);
			} else if (inEventTitle) {
				mEvent.putOrConcat(Event.EVENT_TITLE, value);
			} else if (inEventDescription) {
				mEvent.putOrConcat(Event.EVENT_DESCRIPTION, value);
			} else if (inEventStart) {
				mEvent.putOrConcat(Event.EVENT_START, value);
			} else if (inEventDuration) {
				mEvent.putOrConcat(Event.EVENT_DURATION, value);				
			} else if (inEventRemaining) {
				mEvent.putOrConcat(Event.EVENT_REMAINING, value);
			} else if (inEventCurrentTime) {
				mEvent.putOrConcat(Event.CURRENT_TIME, value);
			} else if (inEventDescriptionExtended) {
				mEvent.putOrConcat(Event.EVENT_DESCRIPTION_EXTENDED, value);
			}
		}
	}
}
