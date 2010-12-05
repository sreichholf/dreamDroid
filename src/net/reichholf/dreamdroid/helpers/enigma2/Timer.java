/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;

/**
 * @author sreichholf
 * 
 */
public class Timer {
	public static final String REFERENCE = "reference";
	public static final String SERVICE_NAME = "servicename";
	public static final String EIT = "eit";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String DESCRIPTION_EXTENDED = "descriptionex";
	public static final String DISABLED = "disabled";
	public static final String BEGIN = "begin";
	public static final String BEGIN_READEABLE = "begin_readable";
	public static final String END = "end";
	public static final String END_READABLE = "end_readable";
	public static final String DURATION = "duration";
	public static final String DURATION_READABLE = "duration_readable";
	public static final String START_PREPARE = "startprepare";
	public static final String JUST_PLAY = "justplay";
	public static final String AFTER_EVENT = "afterevent";
	public static final String LOCATION = "location";
	public static final String TAGS = "tags";
	public static final String LOG_ENTRIES = "logentries";
	public static final String FILE_NAME = "filename";
	public static final String BACK_OFF = "backoff";
	public static final String NEXT_ACTIVATION = "nextactivation";
	public static final String FIRST_TRY_PREPARE = "firsttryprepare";
	public static final String STATE = "state";
	public static final String REPEATED = "repeated";
	public static final String DONT_SAVE = "dontsave";
	public static final String CANCELED = "canceled";
	public static final String TOGGLE_DISABLED = "toggledisabled";
	
	public static final int STATE_WAITING = 0;
	public static final int STATE_PREPARED = 1;
	public static final int STATE_RUNNING = 2;
	public static final int STATE_ENDED = 3;	
	
	public static enum Afterevents {
		NOTHING(0), STANDBY(1), DEEP_STANDBY(2), AUTO(3);

		private int value;

		Afterevents(int val) {
			value = val;
		}

		@Override
		public String toString() {
			return new Integer(value).toString();
		}

		public int intValue() {
			return new Integer(value).intValue();
		}

		public String getText(Activity ac) {
			return (String) ac.getResources().getTextArray(R.array.afterevents)[value];
		}
	}

	/**
	 * @return
	 */
	public static ExtendedHashMap getInitialTimer() {
		ExtendedHashMap timer = new ExtendedHashMap();
		timer.put(DESCRIPTION, "");
		timer.put(LOCATION, "/hdd/movie/");
		timer.put(DISABLED, "0"); // enabled
		timer.put(JUST_PLAY, "0"); // record
		timer.put(AFTER_EVENT, Afterevents.AUTO.toString()); // auto
		timer.put(REPEATED, "0"); // One-Time-Event

		// Calculate values for begin and end
		Calendar cal = GregorianCalendar.getInstance();
		cal.setTime(new Date());

		long s = (cal.getTimeInMillis() / 1000);
		long e = s + 3600;

		String begin = Long.toString(s);
		String end = Long.toString(e);

		timer.put(BEGIN, begin);
		timer.put(END, end);

		return timer;
	}

	/**
	 * @param event
	 * @return
	 */
	public static ExtendedHashMap createByEvent(ExtendedHashMap event) {
		ExtendedHashMap timer = getInitialTimer();

		String start = event.getString(Event.EVENT_START);
		int duration = new Integer(event.getString(Event.EVENT_DURATION));
		int end = duration + new Integer(start);

		timer.put(Timer.BEGIN, start);
		timer.put(Timer.END, String.valueOf(end));
		timer.put(Timer.NAME, event.getString(Event.EVENT_TITLE));
		timer.put(Timer.DESCRIPTION, event.getString(Event.EVENT_DESCRIPTION));
		timer.put(Timer.DESCRIPTION_EXTENDED, event.getString(Event.EVENT_DESCRIPTION_EXTENDED));
		timer.put(Timer.SERVICE_NAME, event.getString(Event.SERVICE_NAME));
		timer.put(Timer.REFERENCE, event.getString(Event.SERVICE_REFERENCE));

		return timer;
	}
	
	public static ArrayList<NameValuePair> getSaveParams(ExtendedHashMap timer, ExtendedHashMap timerOld){
		/*
		 * URL to create /web/timerchange? sRef= &begin= &end= &name=
		 * &description= &dirname= &tags= &eit= &disabled= &justplay=
		 * &afterevent= &repeated= &channelOld= &beginOld= &endOld=
		 * &deleteOldOnSave=
		 */
		// Build Parameters using timer HashMap
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("sRef", timer.getString(REFERENCE)));
		params.add(new BasicNameValuePair("begin", timer.getString(BEGIN)));
		params.add(new BasicNameValuePair("end", timer.getString(END)));
		params.add(new BasicNameValuePair("name", timer.getString(NAME)));
		params.add(new BasicNameValuePair("description", timer.getString(DESCRIPTION)));
		params.add(new BasicNameValuePair("dirname", timer.getString(LOCATION)));
		params.add(new BasicNameValuePair("tags", timer.getString(TAGS)));
		params.add(new BasicNameValuePair("eit", timer.getString(EIT)));
		params.add(new BasicNameValuePair("disabled", timer.getString(DISABLED)));
		params.add(new BasicNameValuePair("justplay", timer.getString(JUST_PLAY)));
		params.add(new BasicNameValuePair("afterevent", timer.getString(AFTER_EVENT)));
		params.add(new BasicNameValuePair("repeated", timer.getString(REPEATED)));

		if (timerOld != null) {
			params.add(new BasicNameValuePair("channelOld", timerOld.getString(REFERENCE)));
			params.add(new BasicNameValuePair("beginOld", timerOld.getString(BEGIN)));
			params.add(new BasicNameValuePair("endOld", timerOld.getString(END)));
			params.add(new BasicNameValuePair("deleteOldOnSave", "1"));
		} else {
			params.add(new BasicNameValuePair("deleteOldOnSave", "0"));
		}
		
		return params;
	}
	
	public static ArrayList<NameValuePair> getEventIdParams(ExtendedHashMap event){
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("sRef", event.getString(Event.SERVICE_REFERENCE)));
		params.add(new BasicNameValuePair("eventid", event.getString(Event.EVENT_ID)));
		
		return params;
	}
	
	public static ArrayList<NameValuePair> getDeleteParams(ExtendedHashMap timer){
		// URL - web/timerdelete?sRef=&begin=&end=
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("sRef", timer.getString(REFERENCE)));
		params.add(new BasicNameValuePair("begin", timer.getString(BEGIN)));
		params.add(new BasicNameValuePair("end", timer.getString(END)));
		
		return params;
	}

}
