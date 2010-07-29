package net.reichholf.dreamdroid.helpers;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Provides static methods for Date and Time parsing
 * 
 * @author sreichholf
 * 
 */
public class DateTime {

	/**
	 * @param eventstart
	 * @param duration
	 * @return
	 */
	public static String getDurationString(String duration, String eventstart) {
		long d = new Long(duration);
		String durationPrefix = "";

		if (eventstart != null) {
			long s = new Long(eventstart) * 1000;
			Date now = new Date();

			if (now.getTime() >= (s)) {
				d = d - ((now.getTime() - s) / 1000);
				durationPrefix = "+";
			}
		}

		d = (d / 60);
		String retVal = durationPrefix + (d);

		return retVal;
	}

	/**
	 * @param timestamp
	 * @return
	 */
	public static String getDateTimeString(String timestamp) {
		SimpleDateFormat sdfDateTime = new SimpleDateFormat("E, dd.MM. - HH:mm");

		Date date = DateTime.getDate(timestamp);
		return sdfDateTime.format(date);
	}

	/**
	 * @param timestamp
	 * @return
	 */
	public static String getYearDateTimeString(String timestamp) {
		SimpleDateFormat sdfDateTime = new SimpleDateFormat("E, dd.MM.yyyy - HH:mm");

		Date date = DateTime.getDate(timestamp);
		return sdfDateTime.format(date);
	}

	/**
	 * @param timestamp
	 * @return
	 */
	public static String getTimeString(String timestamp) {
		SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");

		Date date = DateTime.getDate(timestamp);
		return sdfTime.format(date);
	}

	/**
	 * @param timestamp
	 * @return
	 */
	public static Date getDate(String timestamp) {
		long s = new Long(timestamp);
		s = s * 1000;
		Date date = new Date(s);

		return date;
	}
}
