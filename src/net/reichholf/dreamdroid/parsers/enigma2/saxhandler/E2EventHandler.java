/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */


package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author sreichholf
 *
 */
public class E2EventHandler extends DefaultHandler {

	private boolean inEvent = false;
	private boolean inId = false;
	private boolean inStart = false;
	private boolean inDuration = false;
	private boolean inCurrentTime = false;
	private boolean inTitle = false;
	private boolean inDescription = false;
	private boolean inDescriptionEx = false;
	private boolean inServiceRef = false;
	private boolean inServiceName = false;

	private ExtendedHashMap mEvent;
	private ArrayList<ExtendedHashMap> mEventlist;

	/**
	 * @param list
	 */
	public E2EventHandler(ArrayList<ExtendedHashMap> list) {
		mEventlist = list;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startDocument()
	 */
	@Override
	public void startDocument() {
		// TODO ???
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endDocument()
	 */
	@Override
	public void endDocument() throws SAXException {
		// TODO ???
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals("e2event")) {
			inEvent = true;
			mEvent = new ExtendedHashMap();

		} else if (localName.equals("e2eventid")) {
			inId = true;
		} else if (localName.equals("e2eventstart")) {
			inStart = true;
		} else if (localName.equals("e2eventduration")) {
			inDuration = true;
		} else if (localName.equals("e2eventcurrenttime")) {
			inCurrentTime = true;
		} else if (localName.equals("e2eventtitle")) {
			inTitle = true;
		} else if (localName.equals("e2eventdescription")) {
			inDescription = true;
		} else if (localName.equals("e2eventdescriptionextended")) {
			inDescriptionEx = true;
		} else if (localName.equals("e2eventservicereference")) {
			inServiceRef = true;
		} else if (localName.equals("e2eventservicename")) {
			inServiceName = true;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if (localName.equals("e2event")) {
			inEvent = false;			
			Event.supplementReadables(mEvent);			
			mEventlist.add(mEvent);
		} else if (localName.equals("e2eventid")) {
			inId = false;
		} else if (localName.equals("e2eventstart")) {
			inStart = false;
		} else if (localName.equals("e2eventduration")) {
			inDuration = false;
		} else if (localName.equals("e2eventcurrenttime")) {
			inCurrentTime = false;
		} else if (localName.equals("e2eventtitle")) {
			inTitle = false;
		} else if (localName.equals("e2eventdescription")) {
			inDescription = false;
		} else if (localName.equals("e2eventdescriptionextended")) {
			inDescriptionEx = false;
		} else if (localName.equals("e2eventservicereference")) {
			inServiceRef = false;
		} else if (localName.equals("e2eventservicename")) {
			inServiceName = false;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inEvent) {
			if (inId) {
				mEvent.putOrConcat(Event.EVENT_ID, value.trim());
			} else if (inStart) {
				mEvent.putOrConcat(Event.EVENT_START, value.trim());
			} else if (inDuration) {
				mEvent.putOrConcat(Event.EVENT_DURATION, value.trim());
			} else if (inCurrentTime) {
				mEvent.putOrConcat(Event.CURRENT_TIME, value.trim());
			} else if (inTitle) {
				mEvent.putOrConcat(Event.EVENT_TITLE, value.trim());
			} else if (inDescription) {
				mEvent.putOrConcat(Event.EVENT_DESCRIPTION, value.trim());
			} else if (inDescriptionEx) {
				mEvent.putOrConcat(Event.EVENT_DESCRIPTION_EXTENDED, value.trim());
			} else if (inServiceRef) {
				mEvent.putOrConcat(Event.SERVICE_REFERENCE, value.trim());
			} else if (inServiceName) {
				mEvent.putOrConcat(Event.SERVICE_NAME, value.trim());
			}
		}
	}

}
