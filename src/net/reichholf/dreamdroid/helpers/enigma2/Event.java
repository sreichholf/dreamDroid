/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;

/**
 * @author sreichholf
 * 
 */
public class Event{	
	public static final String KEY_EVENT_ID = "eventid";
	public static final String KEY_EVENT_NAME = "eventname";
	public static final String KEY_EVENT_START = "eventstart";
	public static final String KEY_EVENT_START_READABLE = "eventstart_readable";
	public static final String KEY_EVENT_START_TIME_READABLE = "eventstarttime_readable";
	public static final String KEY_EVENT_DURATION = "eventduration";
	public static final String KEY_EVENT_DURATION_READABLE = "eventduration_readable";
	public static final String KEY_EVENT_REMAINING = "eventremaining";
	public static final String KEY_EVENT_REMAINING_READABLE = "eventremaining_readable";
	public static final String KEY_CURRENT_TIME = "currenttime";
	public static final String KEY_EVENT_TITLE = "eventtitle";
	public static final String KEY_EVENT_DESCRIPTION = "eventdescription";
	public static final String KEY_EVENT_DESCRIPTION_EXTENDED = "eventdescriptionextended";
	public static final String KEY_SERVICE_REFERENCE = "reference";
	public static final String KEY_SERVICE_NAME = "name";
	
	/**
	 * @param event
	 */
	public static void supplementReadables(ExtendedHashMap event) {
		String eventstart = event.getString(KEY_EVENT_START);

		if (!Python.NONE.equals(eventstart) && eventstart != null) {

			String start = DateTime.getDateTimeString(eventstart);
			String starttime = DateTime.getTimeString(eventstart);
			String duration;
			try {
				duration = DateTime.getDurationString(event.getString(KEY_EVENT_DURATION), eventstart);
			} catch (NumberFormatException e) {
				// deal with WebInterface 1.5 => EVENT_DURATION is already a string
				duration = event.getString(KEY_EVENT_DURATION);
			}

			event.put(KEY_EVENT_START_READABLE, start);
			event.put(KEY_EVENT_START_TIME_READABLE, starttime);
			event.put(KEY_EVENT_DURATION_READABLE, duration);
		}

		String eventtitle = event.getString(KEY_EVENT_TITLE);
		if (Python.NONE.equals(eventtitle) || eventtitle == null) {
			// deal with WebInterface 1.5 => try EVENT_NAME instead of EVENT_TITLE
			eventtitle = event.getString(KEY_EVENT_NAME);
			if (eventtitle != null) {
				event.put(KEY_EVENT_TITLE, eventtitle);
			} else {
				event.put(KEY_EVENT_TITLE, "N/A");
			}
		}
	}
}
