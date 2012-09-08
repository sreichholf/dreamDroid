/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.reichholf.dreamdroid.DreamDroid;
import android.util.Log;

/**
 * Provides static methods for Date and Time parsing
 * 
 * @author sreichholf
 * 
 */
public class DateTime {

	/**
	 * @param duration
	 * @param eventstart
	 * @return
	 */
	public static int getRemaining(String duration, String eventstart) {
		long d = Double.valueOf(duration).longValue();
		
		if (eventstart != null) {
			try {
				long s = Double.valueOf(eventstart).longValue() * 1000;
				Date now = new Date();

				if (now.getTime() >= s) {
					d = d - ((now.getTime() - s) / 1000);
					if (d <= 60) {
						d = 60;
					}
				}
			} catch (NumberFormatException nfe) {
				Log.e(DreamDroid.LOG_TAG, nfe.getMessage());
				return 0;
			}
		}

		d = (d / 60);
		return (int) d;
	}

	
	/**
	 * @param eventstart
	 * @param duration
	 * @return
	 */
	public static String getDurationString(String duration, String eventstart) {
		long d = Double.valueOf(duration).longValue();
		String durationPrefix = "";

		if (eventstart != null) {
			try {
				long s = Double.valueOf(eventstart).longValue() * 1000;
				Date now = new Date();

				if (now.getTime() >= s) {
					d = d - ((now.getTime() - s) / 1000);
					if (d <= 60) {
						d = 60;
					}
					durationPrefix = "+";
				}
			} catch (NumberFormatException nfe) {
				Log.e(DreamDroid.LOG_TAG, nfe.getMessage());
				return duration;
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
		SimpleDateFormat sdfDateTime;

		// Some devices are missing some Translations, wee need to work around
		// that!
		if (DreamDroid.DATE_LOCALE_WO) {
			sdfDateTime = new SimpleDateFormat("E, dd.MM. - HH:mm", Locale.US);
		} else {
			sdfDateTime = new SimpleDateFormat("E, dd.MM. - HH:mm");
		}

		return DateTime.getFormattedDateString(sdfDateTime, timestamp);
	}

	/**
	 * @param timestamp
	 * @return
	 */
	public static String getYearDateTimeString(String timestamp) {
		SimpleDateFormat sdfDateTime;

		// Some devices are missing some Translations, wee need to work around
		// that!
		if (DreamDroid.DATE_LOCALE_WO) {
			sdfDateTime = new SimpleDateFormat("E, dd.MM.yyyy - HH:mm", Locale.US);
		} else {
			sdfDateTime = new SimpleDateFormat("E, dd.MM.yyyy - HH:mm");
		}

		return DateTime.getFormattedDateString(sdfDateTime, timestamp);
	}

	/**
	 * @param timestamp
	 * @return
	 */
	public static String getTimeString(String timestamp) {
		SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");
		return DateTime.getFormattedDateString(sdfTime, timestamp);
	}

	/**
	 * @param timestamp
	 * @return
	 */
	public static Date getDate(String timestamp) {
		try {
			long s = Double.valueOf(timestamp).longValue();
			s = s * 1000;
			Date date = new Date( (long) s);

			return date;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * @param sdf
	 * @param timestamp
	 * @return
	 */
	public static String getFormattedDateString(SimpleDateFormat sdf, String timestamp) {
		Date date = DateTime.getDate(timestamp);

		if (date != null) {
			return sdf.format(date);
		}

		return "-";
	}
}
