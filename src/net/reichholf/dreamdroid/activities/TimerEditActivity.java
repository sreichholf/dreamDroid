/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;

import org.apache.http.NameValuePair;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.impl.TimerChangeRequestHandler;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

//TODO Add Tag Support
/**
 * Activity for Editing existing or initial timers
 * 
 * @author sreichholf
 * 
 */
public class TimerEditActivity extends AbstractHttpActivity {
	public static final int ITEM_SAVE = 0;
	public static final int ITEM_CANCEL = 1;
	public static final int ITEM_PICK_SERVICE = 2;
	public static final int ITEM_PICK_START = 3;
	public static final int ITEM_PICK_END = 4;
	public static final int ITEM_PICK_REPEATED = 5;
	public static final int ITEM_PICK_TAGS = 6;

	public static final int PICK_SERVICE_REQUEST = 0;

	public static final int DIALOG_PICK_BEGIN_ID = 1;
	public static final int DIALOG_PICK_END_ID = 2;
	public static final int DIALOG_PICK_REPEATED_ID = 3;
	public static final int DIALOG_PICK_TAGS_ID = 4;
	public static final int DIALOG_LOADING_ID = 5;

	private static final int[] sRepeatedValues = { 1, 2, 4, 8, 16, 32, 64 };

	private boolean[] mCheckedDays = { false, false, false, false, false, false, false };

	private boolean mTagsChanged;
	private ArrayList<String> mSelectedTags;
	private ArrayList<String> mOldTags;

	private ExtendedHashMap mTimer;
	private ExtendedHashMap mTimerOld;

	private EditText mName;
	private EditText mDescription;
	private CheckBox mEnabled;
	private Spinner mAfterevent;
	private Spinner mLocation;
	private TextView mStart;
	private TextView mEnd;
	private TextView mService;
	private TextView mRepeatings;
	private TextView mTags;
	private Button mSave;
	private Button mCancel;
	private ProgressDialog mLoadProgress;
	private ProgressDialog mProgress;

	private GetLocationsAndTagsTask mGetLocationsAndTagsTask;

	private class GetLocationsAndTagsTask extends AsyncTask<Void, String, Boolean> {
		private TimerEditActivity mTea;

		public GetLocationsAndTagsTask(TimerEditActivity tea) {
			mTea = tea;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(Void... params) {
			if (DreamDroid.LOCATIONS.size() == 0) {
				publishProgress(getText(R.string.locations) + " - " + getText(R.string.fetching_data));

				DreamDroid.loadLocations(mShc);
			}

			if (DreamDroid.TAGS.size() == 0) {
				publishProgress(getText(R.string.tags) + " - " + getText(R.string.fetching_data));

				DreamDroid.loadTags(mShc);
			}

			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			if (mLoadProgress != null) {
				if (!mLoadProgress.isShowing()) {
					mLoadProgress = ProgressDialog.show(mTea, getText(R.string.loading).toString(), progress[0]);
				} else {
					mLoadProgress.setMessage(progress[0]);
				}
			} else {
				mLoadProgress = ProgressDialog.show(mTea, getText(R.string.loading).toString(), progress[0]);
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Boolean result) {
			if (mLoadProgress.isShowing()) {
				mLoadProgress.dismiss();
			}
			reload();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.timer_edit);

		mName = (EditText) findViewById(R.id.EditTextTitle);
		mDescription = (EditText) findViewById(R.id.EditTextDescription);
		mEnabled = (CheckBox) findViewById(R.id.CheckBoxEnabled);
		mAfterevent = (Spinner) findViewById(R.id.SpinnerAfterEvent);
		mLocation = (Spinner) findViewById(R.id.SpinnerLocation);
		mStart = (TextView) findViewById(R.id.TextViewBegin);
		mEnd = (TextView) findViewById(R.id.TextViewEnd);
		mRepeatings = (TextView) findViewById(R.id.TextViewRepeated);
		mService = (TextView) findViewById(R.id.TextViewService);
		mTags = (TextView) findViewById(R.id.TextViewTags);

		mSave = (Button) findViewById(R.id.ButtonSave);
		mCancel = (Button) findViewById(R.id.ButtonCancel);

		// onClickListeners
		registerOnClickListener(mSave, ITEM_SAVE);
		registerOnClickListener(mCancel, ITEM_CANCEL);
		registerOnClickListener(mService, ITEM_PICK_SERVICE);
		registerOnClickListener(mStart, ITEM_PICK_START);
		registerOnClickListener(mEnd, ITEM_PICK_END);
		registerOnClickListener(mRepeatings, ITEM_PICK_REPEATED);
		registerOnClickListener(mTags, ITEM_PICK_TAGS);

		mAfterevent.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
				mTimer.put(Timer.KEY_AFTER_EVENT, new Integer(position).toString());
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// Auto is the default
				mAfterevent.setSelection(Timer.Afterevents.AUTO.intValue());
			}
		});

		mLocation.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
				mTimer.put(Timer.KEY_LOCATION, DreamDroid.LOCATIONS.get(position));
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// TODO implement some nothing-selected-handler for locations
			}
		});

		// Default result of this Activity is RESULT_CANCELED
		setResult(RESULT_CANCELED);

		// Initialize if savedInstanceState won't
		if (savedInstanceState == null) {
			HashMap<String, Object> map = (HashMap<String, Object>) getIntent().getExtras().get(sData);
			ExtendedHashMap data = new ExtendedHashMap();
			data.putAll(map);

			mTimer = new ExtendedHashMap();
			mTimer.putAll((HashMap<String, Object>) data.get("timer"));

			if (getIntent().getAction().equals(Intent.ACTION_EDIT)) {
				mTimerOld = mTimer.clone();
			} else {
				mTimerOld = null;
			}

			mSelectedTags = new ArrayList<String>();

			if (DreamDroid.LOCATIONS.size() == 0 || DreamDroid.TAGS.size() == 0) {
				mGetLocationsAndTagsTask = new GetLocationsAndTagsTask(this);
				mGetLocationsAndTagsTask.execute();
			} else {
				reload();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_SERVICE_REQUEST) {
			if (resultCode == RESULT_OK) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.putAll((HashMap<String, Object>) data.getSerializableExtra(sData));

				mTimer.put(Timer.KEY_SERVICE_NAME, map.getString(Service.KEY_NAME));
				mTimer.put(Timer.KEY_REFERENCE, map.getString(Service.KEY_REFERENCE));
				mService.setText(mTimer.getString(Timer.KEY_SERVICE_NAME));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seenet.reichholf.dreamdroid.activities.AbstractHttpListActivity#
	 * onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		final ExtendedHashMap timer = mTimer;
		final ExtendedHashMap timerOld = mTimerOld;

		outState.putSerializable("timer", timer);
		outState.putSerializable("timerOld", timerOld);

		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		super.onSaveInstanceState(outState);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seenet.reichholf.dreamdroid.activities.AbstractHttpListActivity#
	 * onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		mTimer = (ExtendedHashMap) savedInstanceState.getSerializable("timer");
		mTimerOld = (ExtendedHashMap) savedInstanceState.getSerializable("timerOld");

		if (mTimer != null) {
			reload();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		final Dialog dialog;

		Calendar cal;
		Button buttonApply;

		applyViewValues();

		switch (id) {

		case (DIALOG_PICK_BEGIN_ID):
			cal = getCalendarFromTimestamp(mTimer.getString("begin"));

			dialog = new Dialog(this);
			dialog.setContentView(R.layout.date_time_picker);
			dialog.setTitle(R.string.set_time_begin);

			setDateAndTimePicker(dialog, cal);
			dialogRegisterCancel(dialog);

			buttonApply = (Button) dialog.findViewById(R.id.ButtonApply);
			buttonApply.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onTimerBeginSet(getCalendarFromPicker(dialog));
				}
			});

			dialog.show();
			break;

		case (DIALOG_PICK_END_ID):
			cal = getCalendarFromTimestamp(mTimer.getString("end"));

			dialog = new Dialog(this);
			dialog.setContentView(R.layout.date_time_picker);
			dialog.setTitle(R.string.set_time_end);

			setDateAndTimePicker(dialog, cal);
			dialogRegisterCancel(dialog);

			buttonApply = (Button) dialog.findViewById(R.id.ButtonApply);
			buttonApply.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onTimerEndSet(getCalendarFromPicker(dialog));
				}
			});

			dialog.show();
			break;

		case (DIALOG_PICK_REPEATED_ID):
			CharSequence[] days = getResources().getTextArray(R.array.weekdays);
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getText(R.string.choose_days));
			builder.setMultiChoiceItems(days, mCheckedDays, new OnMultiChoiceClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					mCheckedDays[which] = isChecked;

					String text = setRepeated(mCheckedDays, mTimer);
					mRepeatings.setText(text);

				}

			});
			dialog = builder.create();

			break;

		case (DIALOG_PICK_TAGS_ID):
			CharSequence[] tags = new CharSequence[DreamDroid.TAGS.size()];
			boolean[] selectedTags = new boolean[DreamDroid.TAGS.size()];

			int tc = 0;
			for (String tag : DreamDroid.TAGS) {
				tags[tc] = tag;

				if (mSelectedTags.contains(tag)) {
					selectedTags[tc] = true;
				} else {
					selectedTags[tc] = false;
				}

				tc++;
			}

			mTagsChanged = false;
			mOldTags = new ArrayList<String>();
			mOldTags.addAll(mSelectedTags);

			builder = new AlertDialog.Builder(this);
			builder.setTitle(getText(R.string.choose_tags));

			builder.setMultiChoiceItems(tags, selectedTags, new OnMultiChoiceClickListener() {
				/*
				 * (non-Javadoc)
				 * 
				 * @see android.content.DialogInterface.
				 * OnMultiChoiceClickListener
				 * #onClick(android.content.DialogInterface, int, boolean)
				 */
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					String tag = DreamDroid.TAGS.get(which);
					mTagsChanged = true;
					if (isChecked) {
						if (!mSelectedTags.contains(tag)) {
							mSelectedTags.add(tag);
						}
					} else {
						int idx = mSelectedTags.indexOf(tag);
						if (idx >= 0) {
							mSelectedTags.remove(idx);
						}
					}
				}

			});

			builder.setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (mTagsChanged) {
						// TODO Update current Tags
						String tags = Tag.implodeTags(mSelectedTags);
						mTimer.put(Timer.KEY_TAGS, tags);
						mTags.setText(tags);
					}
					dialog.dismiss();
					removeDialog(DIALOG_PICK_TAGS_ID);
				}

			});

			builder.setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mSelectedTags.clear();
					mSelectedTags.addAll(mOldTags);
					dialog.dismiss();
					removeDialog(DIALOG_PICK_TAGS_ID);
				}

			});

			dialog = builder.create();
			break;
		default:
			dialog = null;
		}

		return dialog;
	}

	/**
	 * @param b
	 * @param id
	 */
	protected void registerOnClickListener(View v, final int id) {
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onItemClicked(id);
			}
		});
	}

	/**
	 * @param id
	 */
	protected boolean onItemClicked(int id) {
		switch (id) {
		case ITEM_SAVE:
			saveTimer();
			return true;

		case ITEM_CANCEL:
			cancel();
			return true;

		case ITEM_PICK_SERVICE:
			pickService();
			return true;

		case ITEM_PICK_START:
			showDialog(DIALOG_PICK_BEGIN_ID);
			return true;

		case ITEM_PICK_END:
			showDialog(DIALOG_PICK_END_ID);
			return true;

		case ITEM_PICK_REPEATED:
			showDialog(DIALOG_PICK_REPEATED_ID);
			return true;

		case ITEM_PICK_TAGS:
			showDialog(DIALOG_PICK_TAGS_ID);
		default:
			return super.onItemClicked(id);
		}

	}

	/**
	 * 
	 */
	private void pickService() {
		Intent intent = new Intent(this, ServiceListActivity.class);

		ExtendedHashMap map = new ExtendedHashMap();
		map.put(Service.KEY_REFERENCE, "default");

		intent.putExtra(sData, map);
		intent.setAction(Intent.ACTION_PICK);

		startActivityForResult(intent, PICK_SERVICE_REQUEST);
	}

	/**
	 * Set the GUI-Content from <code>mTimer</code>
	 */
	private void reload() {
		// Name
		mName.setText(mTimer.getString(Timer.KEY_NAME));
		mName.setHint(R.string.title);

		// Description
		mDescription.setText(mTimer.getString(Timer.KEY_DESCRIPTION));
		mDescription.setHint(R.string.description);

		// Enabled
		int disabled = new Integer(mTimer.getString(Timer.KEY_DISABLED));
		if (disabled == 0) {
			mEnabled.setChecked(true);
		} else {
			mEnabled.setChecked(false);
		}

		mService.setText(mTimer.getString(Timer.KEY_SERVICE_NAME));

		// Afterevents
		ArrayAdapter<CharSequence> aaAfterevent = ArrayAdapter.createFromResource(this, R.array.afterevents,
				android.R.layout.simple_spinner_item);
		aaAfterevent.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mAfterevent.setAdapter(aaAfterevent);

		int aeValue = new Integer(mTimer.getString(Timer.KEY_AFTER_EVENT)).intValue();
		mAfterevent.setSelection(aeValue);

		// Locations
		ArrayAdapter<String> aaLocations = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
				DreamDroid.LOCATIONS);
		aaLocations.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mLocation.setAdapter(aaLocations);

		String timerLoc = mTimer.getString(Timer.KEY_LOCATION);
		for (int i = 0; i < DreamDroid.LOCATIONS.size(); i++) {
			String loc = DreamDroid.LOCATIONS.get(i);

			if (timerLoc != null) {
				if (timerLoc.equals(loc)) {
					mLocation.setSelection(i);
				}
			}
		}

		// Start and Endtime
		int begin = new Integer(mTimer.getString(Timer.KEY_BEGIN));
		int end = new Integer(mTimer.getString(Timer.KEY_END));
		long b = ((long) begin) * 1000;
		long e = ((long) end) * 1000;
		Date dateBegin = new Date(b);
		Date dateEnd = new Date(e);

		mStart.setText(dateBegin.toLocaleString());
		mEnd.setText(dateEnd.toLocaleString());

		// Repeatings
		int repeatedValue = new Integer(mTimer.getString(Timer.KEY_REPEATED));
		String repeatedText = getRepeated(repeatedValue);
		mRepeatings.setText(repeatedText);

		String text = mTimer.getString(Timer.KEY_TAGS);
		if (text == null) {
			text = "";
		}
		mTags.setText(text);
		String[] tags = text.split(" ");
		for (String tag : tags) {
			mSelectedTags.add(tag);
		}
	}

	/**
	 * Interpret the repeated int-value by bit-shifting it
	 * 
	 * @param value
	 *            The int-value for to-repeat-days
	 * @return All days selected for repeatings in "Mo, Tu, Fr"-style
	 */
	private String getRepeated(int value) {
		String text = "";
		CharSequence[] daysShort = getResources().getTextArray(R.array.weekdays_short);

		for (int i = 0; i < sRepeatedValues.length; i++) {
			boolean checked = false;

			if ((value & 1) == 1) {
				checked = true;
				if (!text.equals("")) {
					text = text.concat(", ");
				}
				text = text.concat((String) daysShort[i]);
			}
			mCheckedDays[i] = checked;

			value = (value >> 1);
		}

		if (text.equals("")) {
			text = (String) getText(R.string.none);
		}
		return text;
	}

	/**
	 * Applies repeated settings to a timer
	 * 
	 * @param checkedDays
	 *            <code>boolean[]> of checked days for timer-repeatings
	 * @param timer
	 *            The acutal timer
	 * @return The string to set for the GUI-Label
	 */
	private String setRepeated(boolean[] checkedDays, ExtendedHashMap timer) {
		String text = "";
		int value = 0;
		CharSequence[] daysShort = getResources().getTextArray(R.array.weekdays_short);

		for (int i = 0; i < checkedDays.length; i++) {
			if (checkedDays[i]) {
				if (!text.equals("")) {
					text = text.concat(", ");
				}

				text = text.concat((String) daysShort[i]);
				value += sRepeatedValues[i];
			}
		}

		String repeated = new Integer(value).toString();
		timer.put(Timer.KEY_REPEATED, repeated);

		if (value == 31) {
			text = (String) getText(R.string.mo_to_fr);
		} else if (value == 127) {
			text = (String) getText(R.string.daily);
		}

		if (text.equals("")) {
			text = (String) getText(R.string.none);
		}

		return text;
	}

	/**
	 * Apply GUI-values to the timer. Applies Name, Description, Enabled and
	 * Afterevent from the GUI-Elements to <code>mTimer</code>
	 */
	private void applyViewValues() {
		mTimer.put(Timer.KEY_NAME, mName.getText().toString());
		mTimer.put(Timer.KEY_DESCRIPTION, mDescription.getText().toString());

		if (mEnabled.isChecked()) {
			mTimer.put(Timer.KEY_DISABLED, "0");
		} else {
			mTimer.put(Timer.KEY_DISABLED, "1");
		}

		String ae = new Integer(mAfterevent.getSelectedItemPosition()).toString();
		mTimer.put(Timer.KEY_AFTER_EVENT, ae);
	}

	/**
	 * Save the current timer on the target device
	 */
	private void saveTimer() {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		
		mProgress = ProgressDialog.show(this, "", getText(R.string.saving), true);
		
		applyViewValues();
		ArrayList<NameValuePair> params = Timer.getSaveParams(mTimer, mTimerOld);
		
		execSimpleResultTask(new TimerChangeRequestHandler(), params);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onSimpleResult
	 * (boolean, net.reichholf.dreamdroid.helpers.ExtendedHashMap)
	 */
	@Override
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
		if(mProgress != null){
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
			setResult(RESULT_OK);
			finish();
		}
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
		long ts = (new Long(timestamp)) * 1000;
		Date date = new Date(ts);

		Calendar cal = GregorianCalendar.getInstance();
		cal.setTime(date);

		return cal;
	}

	/**
	 * Registers an OnClickListener for a dialogs cancel-button with id
	 * <code>R.id.ButtonCancel</code>
	 * 
	 * @param dialog
	 *            The Dialog containing a <code>R.id.ButtonCancel</code>
	 */
	private void dialogRegisterCancel(final Dialog dialog) {
		Button buttonCancel = (Button) dialog.findViewById(R.id.ButtonCancel);

		buttonCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
	}

	/**
	 * Apply the values of the TimePicker for the Timer-Begin to
	 * <code>mTimer</code>
	 * 
	 * @param cal
	 *            Calndear Object
	 */
	private void onTimerBeginSet(Calendar cal) {
		String seconds = new Long((cal.getTimeInMillis() / 1000)).toString();
		mTimer.put(Timer.KEY_BEGIN, seconds);
		removeDialog(DIALOG_PICK_BEGIN_ID);
		reload();
	}

	/**
	 * Apply the values of the TimePicker for the Timer-End to
	 * <code>mTimer</code>
	 * 
	 * @param cal
	 */
	private void onTimerEndSet(Calendar cal) {
		String seconds = new Long((cal.getTimeInMillis() / 1000)).toString();
		mTimer.put(Timer.KEY_END, seconds);
		removeDialog(DIALOG_PICK_END_ID);
		reload();
	}

	/**
	 * Cancels the Activity
	 */
	private void cancel() {
		finish();
	}
}
