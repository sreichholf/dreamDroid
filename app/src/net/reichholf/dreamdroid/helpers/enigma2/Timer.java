/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.Fragment;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.SimpleToolbarFragmentActivity;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.TimerEditFragment;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Statics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * @author sreichholf
 */
public class Timer {
	public static final String DATA = "data";

	public static final String KEY_REFERENCE = "reference";
	public static final String KEY_SERVICE_NAME = "servicename";
	public static final String KEY_EIT = "eit";
	public static final String KEY_NAME = "name";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_DESCRIPTION_EXTENDED = "descriptionex";
	public static final String KEY_DISABLED = "disabled";
	public static final String KEY_BEGIN = "begin";
	public static final String KEY_BEGIN_READEABLE = "begin_readable";
	public static final String KEY_END = "end";
	public static final String KEY_END_READABLE = "end_readable";
	public static final String KEY_DURATION = "duration";
	public static final String KEY_DURATION_READABLE = "duration_readable";
	public static final String KEY_START_PREPARE = "startprepare";
	public static final String KEY_JUST_PLAY = "justplay";
	public static final String KEY_AFTER_EVENT = "afterevent";
	public static final String KEY_LOCATION = "location";
	public static final String KEY_TAGS = "tags";
	public static final String KEY_LOG_ENTRIES = "logentries";
	public static final String KEY_FILE_NAME = "filename";
	public static final String KEY_BACK_OFF = "backoff";
	public static final String KEY_NEXT_ACTIVATION = "nextactivation";
	public static final String KEY_FIRST_TRY_PREPARE = "firsttryprepare";
	public static final String KEY_STATE = "state";
	public static final String KEY_REPEATED = "repeated";
	public static final String KEY_DONT_SAVE = "dontsave";
	public static final String KEY_CANCELED = "canceled";
	public static final String KEY_TOGGLE_DISABLED = "toggledisabled";

	public enum TimerStates {
		WAITING(0),
		PREPARED(1),
		RUNNING(2),
		ENDED(3);

		private int value;

		TimerStates(int val) {
			value = val;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}

		public int intValue() {
			return value;
		}

		public String getText(Activity ac) {
			return (String) ac.getResources().getTextArray(R.array.afterevents)[value];
		}
	}

	public enum Afterevents {
		NOTHING(0),
		STANDBY(1),
		DEEP_STANDBY(2),
		AUTO(3);

		private int value;

		Afterevents(int val) {
			value = val;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}

		public int intValue() {
			return value;
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
		timer.put(KEY_DESCRIPTION, "");
		timer.put(KEY_LOCATION, "/hdd/movie/");
		timer.put(KEY_DISABLED, "0"); // enabled
		timer.put(KEY_JUST_PLAY, "0"); // record
		timer.put(KEY_AFTER_EVENT, Afterevents.AUTO.toString()); // auto
		timer.put(KEY_REPEATED, "0"); // One-Time-Event

		// Calculate values for begin and end
		Calendar cal = GregorianCalendar.getInstance();
		cal.setTime(new Date());

		long s = (cal.getTimeInMillis() / 1000);
		long e = s + 3600;

		String begin = Long.toString(s);
		String end = Long.toString(e);

		timer.put(KEY_BEGIN, begin);
		timer.put(KEY_END, end);

		return timer;
	}

	/**
	 * @param event
	 * @return
	 */
	public static ExtendedHashMap createByEvent(ExtendedHashMap event) {
		ExtendedHashMap timer = getInitialTimer();

		String start = event.getString(Event.KEY_EVENT_START);
		int duration = DateTime.parseTimestamp(event.getString(Event.KEY_EVENT_DURATION));
		int end = duration + DateTime.parseTimestamp(start);

		timer.put(Timer.KEY_BEGIN, start);
		timer.put(Timer.KEY_END, String.valueOf(end));
		timer.put(Timer.KEY_NAME, event.getString(Event.KEY_EVENT_TITLE));
		timer.put(Timer.KEY_DESCRIPTION, event.getString(Event.KEY_EVENT_DESCRIPTION));
		timer.put(Timer.KEY_DESCRIPTION_EXTENDED, event.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED));
		timer.put(Timer.KEY_SERVICE_NAME, event.getString(Event.KEY_SERVICE_NAME));
		timer.put(Timer.KEY_REFERENCE, event.getString(Event.KEY_SERVICE_REFERENCE));

		return timer;
	}

	public static ArrayList<NameValuePair> getSaveParams(ExtendedHashMap timer, ExtendedHashMap timerOld) {
		/*
		 * URL to create /web/timerchange? sRef= &begin= &end= &name=
		 * &description= &dirname= &tags= &eit= &disabled= &justplay=
		 * &afterevent= &repeated= &channelOld= &beginOld= &endOld=
		 * &deleteOldOnSave=
		 */
		// Build Parameters using timer HashMap
		ArrayList<NameValuePair> params = new ArrayList<>();

		params.add(new NameValuePair("sRef", timer.getString(KEY_REFERENCE)));
		params.add(new NameValuePair("begin", timer.getString(KEY_BEGIN)));
		params.add(new NameValuePair("end", timer.getString(KEY_END)));
		params.add(new NameValuePair("name", timer.getString(KEY_NAME)));
		params.add(new NameValuePair("description", timer.getString(KEY_DESCRIPTION)));
		params.add(new NameValuePair("dirname", timer.getString(KEY_LOCATION)));
		params.add(new NameValuePair("tags", timer.getString(KEY_TAGS)));
		params.add(new NameValuePair("eit", timer.getString(KEY_EIT)));
		params.add(new NameValuePair("disabled", timer.getString(KEY_DISABLED)));
		params.add(new NameValuePair("justplay", timer.getString(KEY_JUST_PLAY)));
		params.add(new NameValuePair("afterevent", timer.getString(KEY_AFTER_EVENT)));
		params.add(new NameValuePair("repeated", timer.getString(KEY_REPEATED)));

		if (timerOld != null) {
			params.add(new NameValuePair("channelOld", timerOld.getString(KEY_REFERENCE)));
			params.add(new NameValuePair("beginOld", timerOld.getString(KEY_BEGIN)));
			params.add(new NameValuePair("endOld", timerOld.getString(KEY_END)));
			params.add(new NameValuePair("deleteOldOnSave", "1"));
		} else {
			params.add(new NameValuePair("deleteOldOnSave", "0"));
		}

		return params;
	}

	public static ArrayList<NameValuePair> getEventIdParams(ExtendedHashMap event) {
		ArrayList<NameValuePair> params = new ArrayList<>();

		params.add(new NameValuePair("sRef", event.getString(Event.KEY_SERVICE_REFERENCE)));
		params.add(new NameValuePair("eventid", event.getString(Event.KEY_EVENT_ID)));

		return params;
	}

	public static ArrayList<NameValuePair> getDeleteParams(ExtendedHashMap timer) {
		// URL - web/timerdelete?sRef=&begin=&end=
		ArrayList<NameValuePair> params = new ArrayList<>();

		params.add(new NameValuePair("sRef", timer.getString(KEY_REFERENCE)));
		params.add(new NameValuePair("begin", timer.getString(KEY_BEGIN)));
		params.add(new NameValuePair("end", timer.getString(KEY_END)));

		return params;
	}


	/**
	 * @param mph    - A MultiPaneHandler instance
	 * @param event  - A timer (ExtendedHashMap)
	 * @param target - The target fragment (after saving/cancellation)
	 */
	public static void editUsingEvent(MultiPaneHandler mph, ExtendedHashMap event, Fragment target) {
		Timer.edit(mph, Timer.createByEvent(event), target, true);
	}

	/**
	 * @param mph    - A MultiPaneHandler instance
	 * @param timer  - A timer (ExtendedHashMap)
	 * @param target - The target fragment (after saving/cancellation)
	 * @param create - set to true if a new timer should be created instead of editing an existing one
	 */
	public static void edit(MultiPaneHandler mph, ExtendedHashMap timer, Fragment target, boolean create) {
		ExtendedHashMap data = new ExtendedHashMap();
		data.put("timer", timer);
		data.put("action", create ? DreamDroid.ACTION_CREATE : Intent.ACTION_EDIT);

		Intent intent = new Intent(target.getContext(), SimpleToolbarFragmentActivity.class);
		intent.putExtra("fragmentClass", TimerEditFragment.class);
		intent.putExtra("titleResource", create ? R.string.new_timer : R.string.edit_timer);
		//intent.putExtra("menuResource", R.menu.save);
		intent.putExtra("serializableData", (Serializable) data);
		target.getActivity().startActivityForResult(intent, Statics.REQUEST_EDIT_TIMER);
	}
}
