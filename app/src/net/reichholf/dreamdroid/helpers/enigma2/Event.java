/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;

import java.util.ArrayList;

/**
 * @author sreichholf
 */
public class Event extends ExtendedHashMap {
    public static final String PREFIX_NOW = "now_";
    public static final String PREFIX_NEXT = "next";

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
    public static final String KEY_SERVICE_REFERENCE = Service.KEY_REFERENCE;
    public static final String KEY_SERVICE_NAME = Service.KEY_NAME;

    public static void supplementReadables(ExtendedHashMap event) {
        supplementReadables("", event);
    }

    /**
     * @param event
     */
    public static void supplementReadables(String prefix, ExtendedHashMap event) {
        String eventstart = event.getString(prefix.concat(KEY_EVENT_START));

        if (!Python.NONE.equals(eventstart) && eventstart != null) {

            String start = DateTime.getDateTimeString(eventstart);
            String starttime = DateTime.getTimeString(eventstart);
            String duration;
            try {
                duration = DateTime.getDurationString(event.getString(prefix.concat(KEY_EVENT_DURATION)), eventstart);
            } catch (NumberFormatException e) {
                // deal with WebInterface 1.5 => EVENT_DURATION is already a string
                duration = event.getString(prefix.concat(KEY_EVENT_DURATION));
            }

            event.put(prefix.concat(KEY_EVENT_START_READABLE), start);
            event.put(prefix.concat(KEY_EVENT_START_TIME_READABLE), starttime);
            event.put(prefix.concat(KEY_EVENT_DURATION_READABLE), duration);
        }

        String eventtitle = event.getString(prefix.concat(KEY_EVENT_TITLE));
        if (Python.NONE.equals(eventtitle) || eventtitle == null) {
            // deal with WebInterface 1.5 => try EVENT_NAME instead of EVENT_TITLE
            eventtitle = event.getString(prefix.concat(KEY_EVENT_NAME));
            if (eventtitle != null) {
                event.put(prefix.concat(KEY_EVENT_TITLE), eventtitle);
            } else {
                event.put(prefix.concat(KEY_EVENT_TITLE), "N/A");
            }
        }
    }

    public static ExtendedHashMap fromNext(ExtendedHashMap serviceNowNext) {
        ExtendedHashMap event = new ExtendedHashMap(serviceNowNext);
        Object[] keys = event.keySet().toArray();
        ArrayList<String> converted = new ArrayList<>();
        for (Object k : keys) {
            String key = (String) k;
            if (key.startsWith(Event.PREFIX_NEXT)) {
                String value = event.getString(key);
                event.remove(key);
                key = key.replaceFirst(Event.PREFIX_NEXT, "");
                event.put(key, value);
                converted.add(key);
            } else if (!key.equals(Event.KEY_SERVICE_NAME) && !key.equals(Event.KEY_SERVICE_REFERENCE) && !converted.contains(key)) {
                event.remove(key);
            }
        }
        return event;
    }

    public Event(ExtendedHashMap data) {
        mMap = data.getHashMap();
    }

    public String id() {
        return getString(KEY_EVENT_ID, "0");
    }

    public String name() {
        return getString(KEY_EVENT_NAME, "");
    }

    public int start() {
        return getInt(KEY_EVENT_START);
    }

    public String startReadable() {
        return getString(KEY_EVENT_START_READABLE, "");
    }

    public String startTimeReadable() {
        return getString(KEY_EVENT_START_TIME_READABLE, "");
    }

    public int duration() {
        return getInt(KEY_EVENT_DURATION);
    }

    public String durationReadable() {
        return getString(KEY_EVENT_DURATION_READABLE, "");
    }

    public int remaining() {
        return getInt(KEY_EVENT_REMAINING);
    }

    public String remainingReadable() {
        return getString(KEY_EVENT_REMAINING_READABLE, "");
    }

    public int currentTime() {
        return getInt(KEY_CURRENT_TIME);
    }

    public String title() {
        return getString(KEY_EVENT_TITLE, "");
    }

    public String description() {
        return getString(KEY_EVENT_DESCRIPTION, "");
    }

    public String descriptionExtended() {
        return getString(KEY_EVENT_DESCRIPTION_EXTENDED, "").replace("\\n", "\n");
    }

    public String reference() {
        return getString(KEY_SERVICE_REFERENCE);
    }

    public String serviceName() {
        return getString(KEY_SERVICE_NAME);
    }
}
