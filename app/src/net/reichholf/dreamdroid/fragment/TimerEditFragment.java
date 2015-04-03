/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.MultiChoiceDialog;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler;

import org.apache.http.NameValuePair;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.fourmob.datetimepicker.date.DatePickerDialog;
import com.sleepbot.datetimepicker.time.RadialPickerLayout;
import com.sleepbot.datetimepicker.time.TimePickerDialog;

//TODO Add Tag Support

/**
 * Activity for Editing existing or initial timers
 *
 * @author sreichholf
 */
public class TimerEditFragment extends AbstractHttpFragment implements MultiChoiceDialog.MultiChoiceDialogListener {

	private static final String TAG = TimerEditFragment.class.getSimpleName();

	private static final int[] sRepeatedValues = {1, 2, 4, 8, 16, 32, 64};

	private boolean[] mCheckedDays = {false, false, false, false, false, false, false};

	private boolean mTagsChanged;
	private ArrayList<String> mSelectedTags;

	private ExtendedHashMap mTimer;
	private ExtendedHashMap mTimerOld;

	private EditText mName;
	private EditText mDescription;
	private CheckBox mEnabled;
	private CheckBox mZap;
	private Spinner mAfterevent;
	private Spinner mLocation;
	private TextView mStartDate;
	private TextView mStartTime;
	private TextView mEndDate;
	private TextView mEndTime;
	private TextView mService;
	private TextView mRepeatings;
	private TextView mTags;
	private ProgressDialog mLoadProgress;
	private ProgressDialog mProgress;

	private GetLocationsAndTagsTask mGetLocationsAndTagsTask;

	private int mBegin;
	private int mEnd;

	private class GetLocationsAndTagsTask extends AsyncTask<Void, String, Boolean> {

		@Override
		protected Boolean doInBackground(Void... params) {
			if (DreamDroid.getLocations().size() == 0) {
				if (isCancelled())
					return false;
				publishProgress(getText(R.string.locations) + " - " + getText(R.string.fetching_data));
				DreamDroid.loadLocations(getHttpClient());
			}

			if (DreamDroid.getTags().size() == 0) {
				if (isCancelled())
					return false;
				publishProgress(getText(R.string.tags) + " - " + getText(R.string.fetching_data));
				DreamDroid.loadTags(getHttpClient());
			}

			return true;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			if (isCancelled())
				return;
			if (mLoadProgress != null) {
				if (!mLoadProgress.isShowing()) {
					mLoadProgress = ProgressDialog.show(getActionBarActivity(), getText(R.string.loading).toString(),
							progress[0]);
				} else {
					mLoadProgress.setMessage(progress[0]);
				}
			} else {
				mLoadProgress = ProgressDialog.show(getActionBarActivity(), getText(R.string.loading).toString(),
						progress[0]);
			}
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (mLoadProgress.isShowing()) {
				mLoadProgress.dismiss();
			}
			reload();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.timer));
	}

	@Override
	public void onDestroy() {
		if (mGetLocationsAndTagsTask != null)
			mGetLocationsAndTagsTask.cancel(true);
		super.onDestroy();
	}

	@SuppressWarnings("unchecked")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.timer_edit, container, false);

		mName = (EditText) view.findViewById(R.id.EditTextTitle);
		mDescription = (EditText) view.findViewById(R.id.EditTextDescription);
		mEnabled = (CheckBox) view.findViewById(R.id.CheckBoxEnabled);
		mZap = (CheckBox) view.findViewById(R.id.CheckBoxZap);
		mAfterevent = (Spinner) view.findViewById(R.id.SpinnerAfterEvent);
		mLocation = (Spinner) view.findViewById(R.id.SpinnerLocation);
		mStartDate = (TextView) view.findViewById(R.id.TextViewBeginDate);
		mStartTime = (TextView) view.findViewById(R.id.TextViewBeginTime);
		mEndDate = (TextView) view.findViewById(R.id.TextViewEndDate);
		mEndTime = (TextView) view.findViewById(R.id.TextViewEndTime);
		mRepeatings = (TextView) view.findViewById(R.id.TextViewRepeated);
		mService = (TextView) view.findViewById(R.id.TextViewService);
		mTags = (TextView) view.findViewById(R.id.TextViewTags);

		// onClickListeners
		registerOnClickListener(mService, Statics.ITEM_PICK_SERVICE);
		registerOnClickListener(mStartDate, Statics.ITEM_PICK_BEGIN_DATE);
		registerOnClickListener(mStartTime, Statics.ITEM_PICK_BEGIN_TIME);
		registerOnClickListener(mEndDate, Statics.ITEM_PICK_END_DATE);
		registerOnClickListener(mEndTime, Statics.ITEM_PICK_END_TIME);
		registerOnClickListener(mRepeatings, Statics.ITEM_PICK_REPEATED);
		registerOnClickListener(mTags, Statics.ITEM_PICK_TAGS);

		mAfterevent.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
				mTimer.put(Timer.KEY_AFTER_EVENT, Integer.valueOf(position).toString());
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
				mTimer.put(Timer.KEY_LOCATION, DreamDroid.getLocations().get(position));
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// TODO implement some nothing-selected-handler for locations
			}
		});

		// Initialize if savedInstanceState won't and instance was not retained
		if (savedInstanceState == null && mTimer == null && mTimerOld == null) {
			HashMap<String, Object> map = (HashMap<String, Object>) getArguments().get(sData);
			ExtendedHashMap data = new ExtendedHashMap();
			data.putAll(map);

			mTimer = new ExtendedHashMap();
			mTimer.putAll((HashMap<String, Object>) data.get("timer"));

			if (Intent.ACTION_EDIT.equals(getArguments().get("action"))) {
				mTimerOld = mTimer.clone();
			} else {
				mTimerOld = null;
			}

			mSelectedTags = new ArrayList<String>();

			if (DreamDroid.getLocations().size() == 0 || DreamDroid.getTags().size() == 0) {
				mGetLocationsAndTagsTask = new GetLocationsAndTagsTask();
				mGetLocationsAndTagsTask.execute();
			} else {
				reload();
			}
		} else if (savedInstanceState != null) {
			mTimer = (ExtendedHashMap) savedInstanceState.getParcelable("timer");
			mTimerOld = (ExtendedHashMap) savedInstanceState.getParcelable("timerOld");
			mSelectedTags = new ArrayList<String>(Arrays.asList(savedInstanceState.getStringArray("selectedTags")));
			if (mTimer != null) {
				reload();
			}
		} else {
			reload();
		}

		registerFab(R.id.fab_save, view, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onItemSelected(Statics.ITEM_SAVE);
			}
		});
		return view;
	}

	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.cancel, menu);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_PICK_SERVICE) {
			if (resultCode == Activity.RESULT_OK) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.putAll((HashMap<String, Object>) data.getSerializableExtra(sData));

				mTimer.put(Timer.KEY_SERVICE_NAME, map.getString(Service.KEY_NAME));
				mTimer.put(Timer.KEY_REFERENCE, map.getString(Service.KEY_REFERENCE));
				mService.setText(mTimer.getString(Timer.KEY_SERVICE_NAME));
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("timer", mTimer);
		outState.putParcelable("timerOld", mTimerOld);

		String[] selectedTags;
		if (mSelectedTags != null) {
			selectedTags = new String[mSelectedTags.size()];
			mSelectedTags.toArray(selectedTags);
		} else {
			selectedTags = new String[0];
		}
		outState.putStringArray("selectedTags", selectedTags);

		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		super.onSaveInstanceState(outState);
	}

	protected void pickRepeatings() {
		CharSequence[] days = getResources().getTextArray(R.array.weekdays);
		MultiChoiceDialog f = MultiChoiceDialog.newInstance(R.string.choose_days, days, mCheckedDays);
		getMultiPaneHandler().showDialogFragment(f, "dialog_select_repeatings");
	}

	protected void pickTags() {
		CharSequence[] tags = new CharSequence[DreamDroid.getTags().size()];
		boolean[] selectedTags = new boolean[DreamDroid.getTags().size()];

		int tc = 0;
		for (String tag : DreamDroid.getTags()) {
			tags[tc] = tag;
			if (mSelectedTags.contains(tag)) {
				selectedTags[tc] = true;
			} else {
				selectedTags[tc] = false;
			}
			tc++;
		}

		mTagsChanged = false;

		MultiChoiceDialog f = MultiChoiceDialog.newInstance(R.string.choose_tags, tags, selectedTags, R.string.ok,
				R.string.cancel);

		getMultiPaneHandler().showDialogFragment(f, "dialog_select_tags");
	}

	/**
	 * @param v
	 * @param id
	 */
	protected void registerOnClickListener(View v, final int id) {
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onItemSelected(id);
			}
		});
	}

	/**
	 * @param id
	 */
	protected boolean onItemSelected(int id) {
		Bundle args;
		boolean consumed = false;
		Calendar calendar = null;
		switch (id) {
			case Statics.ITEM_SAVE:
				saveTimer();
				consumed = true;
				break;

			case Statics.ITEM_CANCEL:
				finish(Activity.RESULT_CANCELED);
				consumed = true;
				break;

			case Statics.ITEM_PICK_SERVICE:
				pickService();
				consumed = true;
				break;

			case Statics.ITEM_PICK_BEGIN_DATE:
				calendar = getCalendar(mBegin);
				DatePickerDialog datePickerDialogBegin = DatePickerDialog.newInstance(new DatePickerDialog.OnDateSetListener() {
					@Override
					public void onDateSet(DatePickerDialog datePickerDialog, int year, int month, int day) {
						TimerEditFragment.this.onDateSet(true, year, month, day);
					}
				}, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), true);
				getMultiPaneHandler().showDialogFragment(datePickerDialogBegin, "dialog_pick_begin_date");
				consumed = true;
				break;

			case Statics.ITEM_PICK_BEGIN_TIME:
				calendar = getCalendar(mBegin);
				TimePickerDialog timePickerDialogBegin = TimePickerDialog.newInstance(new TimePickerDialog.OnTimeSetListener() {
					@Override
					public void onTimeSet(RadialPickerLayout radialPickerLayout, int hour, int minute) {
						TimerEditFragment.this.onTimeSet(true, hour, minute);
					}
				}, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true, true);
				getMultiPaneHandler().showDialogFragment(timePickerDialogBegin, "dialog_pick_begin_time");
				consumed = true;
				break;

			case Statics.ITEM_PICK_END_DATE:
				calendar = getCalendar(mEnd);
				DatePickerDialog datePickerDialogEnd = DatePickerDialog.newInstance(new DatePickerDialog.OnDateSetListener() {
					@Override
					public void onDateSet(DatePickerDialog datePickerDialog, int year, int month, int day) {
						TimerEditFragment.this.onDateSet(false, year, month, day);
					}
				}, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), true);
				getMultiPaneHandler().showDialogFragment(datePickerDialogEnd, "dialog_pick_end_date");
				consumed = true;
				break;

			case Statics.ITEM_PICK_END_TIME:
				calendar = getCalendar(mEnd);
				TimePickerDialog timePickerDialogEnd = TimePickerDialog.newInstance(new TimePickerDialog.OnTimeSetListener() {
					@Override
					public void onTimeSet(RadialPickerLayout radialPickerLayout, int hour, int minute) {
						TimerEditFragment.this.onTimeSet(false, hour, minute);
					}
				}, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true, true);
				getMultiPaneHandler().showDialogFragment(timePickerDialogEnd, "dialog_pick_end_time");
				consumed = true;
				break;

			case Statics.ITEM_PICK_REPEATED:
				pickRepeatings();
				consumed = true;
				break;

			case Statics.ITEM_PICK_TAGS:
				pickTags();
				consumed = true;
				break;

			default:
				consumed = super.onItemSelected(id);
				break;
		}

		return consumed;
	}

	/**
	 *
	 */
	private void pickService() {
		ServiceListFragment f = new ServiceListFragment();
		Bundle args = new Bundle();

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Service.KEY_REFERENCE, "default");

		args.putSerializable(sData, data);
		args.putString("action", Intent.ACTION_PICK);

		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_PICK_SERVICE);
		((MultiPaneHandler) getActionBarActivity()).showDetails(f, true);
	}

	/**
	 * Set the GUI-Content from <code>mTimer</code>
	 */
	protected void reload() {
		// Name
		mName.setText(mTimer.getString(Timer.KEY_NAME));
		mName.setHint(R.string.title);

		// Description
		mDescription.setText(mTimer.getString(Timer.KEY_DESCRIPTION));
		mDescription.setHint(R.string.description);

		// Enabled
		int disabled = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_DISABLED));
		if (disabled == 0) {
			mEnabled.setChecked(true);
		} else {
			mEnabled.setChecked(false);
		}

		int zap = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_JUST_PLAY));
		if (zap == 1) {
			mZap.setChecked(true);
		} else {
			mZap.setChecked(false);
		}

		mService.setText(mTimer.getString(Timer.KEY_SERVICE_NAME));

		// Afterevents
		ArrayAdapter<CharSequence> aaAfterevent = ArrayAdapter.createFromResource(getActionBarActivity(),
				R.array.afterevents, android.R.layout.simple_spinner_item);
		aaAfterevent.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mAfterevent.setAdapter(aaAfterevent);


		int aeValue = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_AFTER_EVENT));
		mAfterevent.setSelection(aeValue);

		// Locations
		ArrayAdapter<String> aaLocations = new ArrayAdapter<String>(getActionBarActivity(),
				android.R.layout.simple_spinner_item, DreamDroid.getLocations());
		aaLocations.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mLocation.setAdapter(aaLocations);

		String timerLoc = mTimer.getString(Timer.KEY_LOCATION);
		for (int i = 0; i < DreamDroid.getLocations().size(); i++) {
			String loc = DreamDroid.getLocations().get(i);

			if (timerLoc != null) {
				if (timerLoc.equals(loc)) {
					mLocation.setSelection(i);
				}
			}
		}

		// Start and Endtime
		mBegin = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_BEGIN));
		mEnd = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_END));
		Date dateBegin = new Date(((long) mBegin) * 1000);
		Date dateEnd = new Date(((long) mEnd) * 1000);

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

		mStartDate.setText(dateFormat.format(dateBegin));
		mStartTime.setText(timeFormat.format(dateBegin));
		mEndDate.setText(dateFormat.format(dateEnd));
		mEndTime.setText(timeFormat.format(dateEnd));

		// Repeatings
		int repeatedValue = 0;
		try {
			repeatedValue = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_REPEATED));
		} catch (NumberFormatException ex) {
		}

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
	 * @param value The int-value for to-repeat-days
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
	 * @param checkedDays <code>boolean[]> of checked days for timer-repeatings
	 * @param timer       The acutal timer
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

		String repeated = Integer.valueOf(value).toString();
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

		if (mZap.isChecked()) {
			mTimer.put(Timer.KEY_JUST_PLAY, "1");
		} else {
			mTimer.put(Timer.KEY_JUST_PLAY, "0");
		}

		String ae = Integer.valueOf(mAfterevent.getSelectedItemPosition()).toString();
		mTimer.put(Timer.KEY_AFTER_EVENT, ae);
	}

	/**
	 * Save the current timer on the target device
	 */
	private void saveTimer() {
		Log.i(TAG, "saveTimer()");
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		Activity activtiy = getActionBarActivity();
		mProgress = ProgressDialog.show(activtiy, "", getText(R.string.saving), true);

		applyViewValues();
		ArrayList<NameValuePair> params = Timer.getSaveParams(mTimer, mTimerOld);
		execSimpleResultTask(new TimerChangeRequestHandler(), params);
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
			finish(Activity.RESULT_OK);
		}
	}

	/**
	 * Apply the values of the TimePicker for the Timer-Begin to
	 * <code>mTimer</code>
	 */
	private void updateBegin(Calendar cal) {
		mBegin = (int) (cal.getTimeInMillis() / 1000);
		String timestamp = Long.valueOf(mBegin).toString();
		mTimer.put(Timer.KEY_BEGIN, timestamp);
		mTimer.put(Timer.KEY_BEGIN_READEABLE, DateTime.getYearDateTimeString(timestamp));
	}

	/**
	 * Apply the values of the TimePicker for the Timer-End to
	 * <code>mTimer</code>
	 */
	private void updateEnd(Calendar cal) {
		mEnd = (int) (cal.getTimeInMillis() / 1000);
		String timestamp = Long.valueOf(mEnd).toString();
		mTimer.put(Timer.KEY_END, timestamp);
		mTimer.put(Timer.KEY_END_READABLE, DateTime.getYearDateTimeString(timestamp));
	}

	@Override
	public void onMultiChoiceDialogSelection(String dialogTag, DialogInterface dialog, Integer[] selected) {
		if ("dialog_select_tags".equals(dialogTag)) {
			ArrayList<String> tags = DreamDroid.getTags();
			ArrayList<String> selectedTags = new ArrayList<>();
			for (Integer which : selected) {
				selectedTags.add(tags.get(which));
			}
			mTagsChanged = !selectedTags.equals(mSelectedTags);
			mSelectedTags = selectedTags;
		} else if ("dialog_select_repeatings".equals(dialogTag)) {
			for (int i = 0; i < mCheckedDays.length; ++i) {
				mCheckedDays[i] = false;
			}
			for (Integer which : selected) {
				mCheckedDays[which] = true;
			}
			String text = setRepeated(mCheckedDays, mTimer);
			mRepeatings.setText(text);
		}
	}

	@Override
	public void onMultiChoiceDialogFinish(String dialogTag, int result) {
		if ("dialog_select_tags".equals(dialogTag) && mTagsChanged) {
			String tags = Tag.implodeTags(mSelectedTags);
			mTimer.put(Timer.KEY_TAGS, tags);
			mTags.setText(tags);
		}
	}

	private Calendar getCalendar(int time) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis((long) time * 1000);
		return cal;
	}

	public void onDateSet(boolean isBegin, int year, int month, int day) {
		int time = isBegin ? mBegin : mEnd;

		Calendar cal = getCalendar(time);

		if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && cal.get(Calendar.DATE) == day)
			return;
		cal.set(year, month, day);

		TextView dateView = isBegin ? mStartDate : mEndDate;
		SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
		dateView.setText(dayFormat.format(cal.getTime()));

		onTimeChanged(isBegin, cal);
	}

	public void onTimeSet(boolean isBegin, int hourOfDay, int minute) {
		int time = isBegin ? mBegin : mEnd;
		Calendar cal = getCalendar(time);
		if (cal.get(Calendar.HOUR_OF_DAY) == hourOfDay && cal.get(Calendar.MINUTE) == minute)
			return;
		cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
		cal.set(Calendar.MINUTE, minute);

		TextView timeView = isBegin ? mStartTime : mEndTime;
		SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
		timeView.setText(timeFormat.format(cal.getTime()));

		onTimeChanged(isBegin, cal);
	}

	private void onTimeChanged(boolean isBegin, Calendar cal) {
		if (isBegin)
			updateBegin(cal);
		else
			updateEnd(cal);
	}
}
