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

/**
 * @author sreichholf
 * 
 */
public class E2CurrentServiceHandler extends E2SimpleHandler {

	protected static final String TAG_E2CURRENTSERVICEINFORMATION = "e2currentserviceinformation";
	protected static final String TAG_E2EVENTDESCRIPTIONEXTENDED = "e2eventdescriptionextended";
	protected static final String TAG_E2EVENTCURRENTTIME = "e2eventcurrenttime";
	protected static final String TAG_E2EVENTREMAINING = "e2eventremaining";
	protected static final String TAG_E2EVENTDURATION = "e2eventduration";
	protected static final String TAG_E2EVENTSTART = "e2eventstart";
	protected static final String TAG_E2EVENTDESCRIPTION = "e2eventdescription";
	protected static final String TAG_E2EVENTTITLE = "e2eventtitle";
	protected static final String TAG_E2EVENTNAME = "e2eventname";
	protected static final String TAG_E2EVENTID = "e2eventid";
	protected static final String TAG_E2EVENTPROVIDERNAME = "e2eventprovidername";
	protected static final String TAG_E2EVENTSERVICENAME = "e2eventservicename";
	protected static final String TAG_E2EVENTSERVICEREFERENCE = "e2eventservicereference";
	protected static final String TAG_E2EVENT = "e2event";
	protected static final String TAG_E2SID = "e2sid";
	protected static final String TAG_E2ONID = "e2onid";
	protected static final String TAG_E2TSID = "e2tsid";
	protected static final String TAG_E2TXTPID = "e2txtpid";
	protected static final String TAG_E2PMTPID = "e2pmtpid";
	protected static final String TAG_E2PCRPID = "e2pcrpid";
	protected static final String TAG_E2VPID = "e2vpid";
	protected static final String TAG_E2APID = "e2apid";
	protected static final String TAG_E2ISWIDESCREEN = "e2iswidescreen";
	protected static final String TAG_E2SERVICEVIDEOSIZE = "e2servicevideosize";
	protected static final String TAG_E2VIDEOHEIGHT = "e2videoheight";
	protected static final String TAG_E2VIDEOWIDTH = "e2videowidth";
	protected static final String TAG_E2PROVIDERNAME = "e2providername";
	protected static final String TAG_E2SERVICENAME = "e2servicename";
	protected static final String TAG_E2SERVICEREFERENCE = "e2servicereference";

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

	/**
	 * @param list
	 */
	public E2CurrentServiceHandler() {
		super();
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
		} else if (localName.equals(TAG_E2SERVICEREFERENCE)) {
			inServiceReference = true;
		} else if (localName.equals(TAG_E2SERVICENAME)) {
			inServiceName = true;
		} else if (localName.equals(TAG_E2PROVIDERNAME)) {
			inProviderName = true;
		} else if (localName.equals(TAG_E2VIDEOWIDTH)) {
			inVideoWidth = true;
		} else if (localName.equals(TAG_E2VIDEOHEIGHT)) {
			inVideoHeight = true;
		} else if (localName.equals(TAG_E2SERVICEVIDEOSIZE)) {
			inVideoSize = true;
		} else if (localName.equals(TAG_E2ISWIDESCREEN)) {
			inIsWideScreen = true;
		} else if (localName.equals(TAG_E2APID)) {
			inApid = true;
		} else if (localName.equals(TAG_E2VPID)) {
			inVpid = true;
		} else if (localName.equals(TAG_E2PCRPID)) {
			inPcrPid = true;
		} else if (localName.equals(TAG_E2PMTPID)) {
			inPmtPid = true;
		} else if (localName.equals(TAG_E2TXTPID)) {
			inTxtPid = true;
		} else if (localName.equals(TAG_E2TSID)) {
			inTsid = true;
		} else if (localName.equals(TAG_E2ONID)) {
			inOnid = true;
		} else if (localName.equals(TAG_E2SID)) {
			inSid = true;
		} else if (localName.equals(TAG_E2EVENT)) {
			inEvent = true;
			mEvent = new ExtendedHashMap();
		} else if (localName.equals(TAG_E2EVENTSERVICEREFERENCE)) {
			inEventServiceReference = true;
		} else if (localName.equals(TAG_E2EVENTSERVICENAME)) {
			inEventServiceName = true;
		} else if (localName.equals(TAG_E2EVENTPROVIDERNAME)) {
			inEventProviderName = true;
		} else if (localName.equals(TAG_E2EVENTID)) {
			inEventId = true;
		} else if (localName.equals(TAG_E2EVENTNAME)) {
			inEventName = true;
		} else if (localName.equals(TAG_E2EVENTTITLE)) {
			inEventTitle = true;
		} else if (localName.equals(TAG_E2EVENTDESCRIPTION)) {
			inEventDescription = true;
		} else if (localName.equals(TAG_E2EVENTSTART)) {
			inEventStart = true;
		} else if (localName.equals(TAG_E2EVENTDURATION)) {
			inEventDuration = true;
		} else if (localName.equals(TAG_E2EVENTREMAINING)) {
			inEventRemaining = true;
		} else if (localName.equals(TAG_E2EVENTCURRENTTIME)) {
			inEventCurrentTime = true;
		} else if (localName.equals(TAG_E2EVENTDESCRIPTIONEXTENDED)) {
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
			mResult.put(CurrentService.KEY_SERVICE, mService);
		} else if (localName.equals(TAG_E2SERVICEREFERENCE)) {
			inServiceReference = false;
		} else if (localName.equals(TAG_E2SERVICENAME)) {
			inServiceName = false;
		} else if (localName.equals(TAG_E2PROVIDERNAME)) {
			inProviderName = false;
		} else if (localName.equals(TAG_E2VIDEOWIDTH)) {
			inVideoWidth = false;
		} else if (localName.equals(TAG_E2VIDEOHEIGHT)) {
			inVideoHeight = false;
		} else if (localName.equals(TAG_E2SERVICEVIDEOSIZE)) {
			inVideoSize = false;
		} else if (localName.equals(TAG_E2ISWIDESCREEN)) {
			inIsWideScreen = false;
		} else if (localName.equals(TAG_E2APID)) {
			inApid = false;
		} else if (localName.equals(TAG_E2VPID)) {
			inVpid = false;
		} else if (localName.equals(TAG_E2PCRPID)) {
			inPcrPid = false;
		} else if (localName.equals(TAG_E2PMTPID)) {
			inPmtPid = false;
		} else if (localName.equals(TAG_E2TXTPID)) {
			inTxtPid = false;
		} else if (localName.equals(TAG_E2TSID)) {
			inTsid = false;
		} else if (localName.equals(TAG_E2ONID)) {
			inOnid = false;
		} else if (localName.equals(TAG_E2SID)) {
			inSid = false;
		} else if (localName.equals(TAG_E2EVENT)) {
			inEvent = false;
			Event.supplementReadables(mEvent);
			mEvents.add(mEvent);
		} else if (localName.equals(TAG_E2EVENTSERVICEREFERENCE)) {
			inEventServiceReference = false;
		} else if (localName.equals(TAG_E2EVENTSERVICENAME)) {
			inEventServiceName = false;
		} else if (localName.equals(TAG_E2EVENTPROVIDERNAME)) {
			inEventProviderName = false;
		} else if (localName.equals(TAG_E2EVENTID)) {
			inEventId = false;
		} else if (localName.equals(TAG_E2EVENTNAME)) {
			inEventName = false;
		} else if (localName.equals(TAG_E2EVENTTITLE)) {
			inEventTitle = false;
		} else if (localName.equals(TAG_E2EVENTDESCRIPTION)) {
			inEventDescription = false;
		} else if (localName.equals(TAG_E2EVENTSTART)) {
			inEventStart = false;
		} else if (localName.equals(TAG_E2EVENTDURATION)) {
			inEventDuration = false;
		} else if (localName.equals(TAG_E2EVENTREMAINING)) {
			inEventRemaining = false;
		} else if (localName.equals(TAG_E2EVENTCURRENTTIME)) {
			inEventCurrentTime = false;
		} else if (localName.equals(TAG_E2EVENTDESCRIPTIONEXTENDED)) {
			inEventDescriptionExtended = false;
		} else if (localName.equals(TAG_E2CURRENTSERVICEINFORMATION)) {
			mResult.put(CurrentService.KEY_EVENTS, mEvents);
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
				mService.putOrConcat(CurrentService.KEY_SERVICE_REFERENCE, value);
			} else if (inServiceName) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_NAME, value);
			} else if (inProviderName) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_PROVIDER, value);
			} else if (inVideoWidth) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_VIDEO_WIDTH, value);
			} else if (inVideoHeight) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_VIDEO_HEIGHT, value);
			} else if (inVideoSize) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_VIDEO_SIZE, value);
			} else if (inIsWideScreen) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_IS_WIDESCREEN, value);
			} else if (inApid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_APID, value);
			} else if (inVpid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_VPID, value);
			} else if (inPcrPid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_PCRPID, value);
			} else if (inPmtPid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_PMTPID, value);
			} else if (inTxtPid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_TXTPID, value);
			} else if (inTsid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_TSID, value);
			} else if (inOnid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_ONID, value);
			} else if (inSid) {
				mService.putOrConcat(CurrentService.KEY_SERVICE_SID, value);
			}
		} else if (inEvent) {
			if (inEventServiceReference) {
				mEvent.putOrConcat(Event.KEY_SERVICE_REFERENCE, value);
			} else if (inEventServiceName) {
				mEvent.putOrConcat(Event.KEY_SERVICE_NAME, value.replaceAll("\\p{Cntrl}", ""));
			} else if (inEventProviderName) {
				// TODO add handling if needed
			} else if (inEventId) {
				mEvent.putOrConcat(Event.KEY_EVENT_ID, value);
			} else if (inEventName) {
				mEvent.putOrConcat(Event.KEY_EVENT_NAME, value);
			} else if (inEventTitle) {
				mEvent.putOrConcat(Event.KEY_EVENT_TITLE, value);
			} else if (inEventDescription) {
				mEvent.putOrConcat(Event.KEY_EVENT_DESCRIPTION, value);
			} else if (inEventStart) {
				mEvent.putOrConcat(Event.KEY_EVENT_START, value);
			} else if (inEventDuration) {
				mEvent.putOrConcat(Event.KEY_EVENT_DURATION, value);
			} else if (inEventRemaining) {
				mEvent.putOrConcat(Event.KEY_EVENT_REMAINING, value);
			} else if (inEventCurrentTime) {
				mEvent.putOrConcat(Event.KEY_CURRENT_TIME, value);
			} else if (inEventDescriptionExtended) {
				mEvent.putOrConcat(Event.KEY_EVENT_DESCRIPTION_EXTENDED, value);
			}
		}
	}
}
