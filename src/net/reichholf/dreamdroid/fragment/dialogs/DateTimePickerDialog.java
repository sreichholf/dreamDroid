/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import net.reichholf.dreamdroid.R;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TimePicker;

/**
 * @author sre
 * 
 */
public class DateTimePickerDialog extends ActionDialog {
	public static String ARG_REQUEST_CODE = "requestCode";
	public static String ARG_TIMESTAMP = "timestamp";
	public static String ARG_TITLE = "title";

	private int mAction;
	private String mTimestamp;
	private String mTitle;

	public DateTimePickerDialog() {
	}

	public static DateTimePickerDialog newInstance() {
		return new DateTimePickerDialog();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		mAction = getArguments().getInt(ARG_REQUEST_CODE);
		mTimestamp = getArguments().getString(ARG_TIMESTAMP);
		mTitle = getArguments().getString(ARG_TITLE);
		if(mTitle == null)
			getString(R.string.set_time_begin);
		
		
		final Dialog dialog;
		int currentapiVersion = android.os.Build.VERSION.SDK_INT;
		Calendar cal = getCalendarFromTimestamp(mTimestamp);

		dialog = new Dialog(getActivity());
		dialog.setContentView(R.layout.date_time_picker);
		dialog.setTitle(mTitle);

		if (currentapiVersion >= 11) {
			DatePicker dp = (DatePicker) dialog.findViewById(R.id.DatePicker);
			try {
				Method m = dp.getClass().getMethod("setCalendarViewShown", boolean.class);
				m.invoke(dp, false);
			} catch (Exception e) {
			} // eat exception in our case
		}

		setDateAndTimePicker(dialog, cal);

		Button buttonCancel = (Button) dialog.findViewById(R.id.ButtonCancel);
		buttonCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		Button buttonApply = (Button) dialog.findViewById(R.id.ButtonApply);
		buttonApply.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finishDialog(mAction, getCalendarFromPicker(dialog));
			}
		});

		return dialog;
	}

	/**
	 * Set the the values of the date and the time picker of the DateTimePicker
	 * dialog
	 * 
	 * @param dialog
	 *            The Dialog containing the date and the time picker
	 * @param cal
	 *            The calendar-object to set date and time from
	 */
	private void setDateAndTimePicker(final Dialog dialog, Calendar cal) {
		DatePicker dp = (DatePicker) dialog.findViewById(R.id.DatePicker);
		TimePicker tp = (TimePicker) dialog.findViewById(R.id.TimePicker);

		dp.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
		tp.setCurrentHour(cal.get(Calendar.HOUR_OF_DAY));
		tp.setCurrentMinute(cal.get(Calendar.MINUTE));
	}

	/**
	 * @param dialog
	 *            The dialog containing the date and the time picker
	 * @return <code>Calendar</code> container set to the date and time of the
	 *         Date- and TimePicker
	 */
	private Calendar getCalendarFromPicker(final Dialog dialog) {
		Calendar cal = GregorianCalendar.getInstance();
		DatePicker dp = (DatePicker) dialog.findViewById(R.id.DatePicker);
		TimePicker tp = (TimePicker) dialog.findViewById(R.id.TimePicker);

		cal.set(Calendar.YEAR, dp.getYear());
		cal.set(Calendar.MONTH, dp.getMonth());
		cal.set(Calendar.DAY_OF_MONTH, dp.getDayOfMonth());
		cal.set(Calendar.HOUR_OF_DAY, tp.getCurrentHour());
		cal.set(Calendar.MINUTE, tp.getCurrentMinute());
		cal.set(Calendar.SECOND, 0);

		return cal;
	}

	/**
	 * Convert a unix timestamp to a java Calendar instance
	 * 
	 * @param timestamp
	 *            A unix timestamp
	 * @return
	 */
	private Calendar getCalendarFromTimestamp(String timestamp) {
		long ts = (Long.valueOf(timestamp)) * 1000;
		Date date = new Date(ts);

		Calendar cal = GregorianCalendar.getInstance();
		cal.setTime(date);

		return cal;
	}
}
