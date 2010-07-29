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
import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2TimerHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;

/**
 * @author sreichholf
 * 
 */
public class Timer extends AbstractRequestHandler {
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
	public static ExtendedHashMap getNewTimer() {
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
	 * @param shc
	 * @param params
	 * @return
	 */
	public static String getList(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.TIMER_LIST, params[0])) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param xml
	 * @param list
	 * @return
	 */
	public static boolean parseList(String xml, ArrayList<ExtendedHashMap> list) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2TimerHandler handler = new E2TimerHandler(list);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}

	/**
	 * 
	 * @param shc
	 * @param timer
	 * @return
	 */
	public static String save(SimpleHttpClient shc, ExtendedHashMap timer, ExtendedHashMap timerOld) {
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

		if (shc.fetchPageContent(URIStore.TIMER_CHANGE, params)) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param shc
	 * @param event
	 * @return
	 */
	public static String addByEventId(SimpleHttpClient shc, ExtendedHashMap event) {
		// URL - /web/timeraddbyeventid?sRef=&eventid=&justplay=&dirname=&tags=
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("sRef", event.getString(Event.SERVICE_REFERENCE)));
		params.add(new BasicNameValuePair("eventid", event.getString(Event.EVENT_ID)));

		if (shc.fetchPageContent(URIStore.TIMER_ADD_BY_EVENT_ID, params)) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param shc
	 * @param timer
	 * @return
	 */
	public static String delete(SimpleHttpClient shc, ExtendedHashMap timer) {
		// URL - web/timerdelete?sRef=&begin=&end=
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("sRef", timer.getString(REFERENCE)));
		params.add(new BasicNameValuePair("begin", timer.getString(BEGIN)));
		params.add(new BasicNameValuePair("end", timer.getString(END)));

		if (shc.fetchPageContent(URIStore.TIMER_DELETE, params)) {
			return shc.getPageContentString();
		}

		return null;
	}

}
