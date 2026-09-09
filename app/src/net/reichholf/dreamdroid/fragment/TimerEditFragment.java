/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;

import com.evernote.android.state.State;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.SimpleToolbarFragmentActivity;
import net.reichholf.dreamdroid.enigma.LocationsAndTagsLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.fragment.dialogs.MultiChoiceDialog;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler;
import net.reichholf.dreamdroid.ui.timers.TimerEditState;
import net.reichholf.dreamdroid.ui.timers.TimerEditStateKt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Activity for Editing existing or initial timers. Compose Material 3 form;
 * save/pick still use ExtendedHashMap at the edge.
 *
 * @author sreichholf
 */
public class TimerEditFragment extends BaseHttpFragment implements MultiChoiceDialog.MultiChoiceDialogListener {

	private static final String TAG = TimerEditFragment.class.getSimpleName();

	private static final int[] sRepeatedValues = {1, 2, 4, 8, 16, 32, 64};

	@NonNull
	private boolean[] mCheckedDays = {false, false, false, false, false, false, false};

	private boolean mTagsChanged;

	@State
	public ArrayList<String> mSelectedTags;
	@State
	public ExtendedHashMap mTimer;
	@Nullable
	@State
	public ExtendedHashMap mTimerOld;

	@Nullable
	private ProgressDialog mLocationsAndTagsProgress;
	@Nullable
	private ProgressDialog mProgress;

	@Nullable
	private Job mLocationsAndTagsJob;
	private TimerEditState mEditState;

	private int mBegin;
	private int mEnd;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mHasFabMain = false;
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.timer));
		mLocationsAndTagsProgress = null;
		mEditState = new TimerEditState();
	}

	@Override
	public void onDestroy() {
		if (mLocationsAndTagsJob != null) {
			mLocationsAndTagsJob.cancel(null);
			mLocationsAndTagsJob = null;
		}
		super.onDestroy();
	}

	@SuppressWarnings("unchecked")
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (mTimer == null || mTimerOld == null) {
			ExtendedHashMap data = ((ExtendedHashMap) getArguments().get(sData)).clone();
			mTimer = ((ExtendedHashMap) data.get("timer")).clone();

			if (Intent.ACTION_EDIT.equals(data.get("action"))) {
				mTimerOld = mTimer.clone();
			} else {
				mTimerOld = null;
			}

			mSelectedTags = new ArrayList<>();

			if (DreamDroid.getLocations().size() == 0 || DreamDroid.getTags().size() == 0) {
				mLocationsAndTagsJob = LocationsAndTagsLoadKt.launchLocationsAndTagsLoad(
						this,
						(title, progress) -> {
							onGetLocationsAndTagsProgress(title, progress);
							return Unit.INSTANCE;
						},
						() -> {
							mLocationsAndTagsJob = null;
							onLocationsAndTagsReady();
							return Unit.INSTANCE;
						});
			} else {
				reload();
			}
		} else {
			reload();
		}

		ComposeView composeView = new ComposeView(requireContext());
		composeView.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
		));
		TimerEditStateKt.bindTimerEditScreen(
				composeView,
				mEditState,
				getString(R.string.save),
				() -> {
					onItemSelected(Statics.ITEM_SAVE);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_PICK_BEGIN_DATE);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_PICK_BEGIN_TIME);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_PICK_END_DATE);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_PICK_END_TIME);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_PICK_REPEATED);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_PICK_SERVICE);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_PICK_TAGS);
					return kotlin.Unit.INSTANCE;
				}
		);
		return composeView;
	}

	public void createOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.save, menu);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
		if (requestCode == Statics.REQUEST_PICK_SERVICE) {
			if (resultCode == Activity.RESULT_OK) {
				ExtendedHashMap map = (ExtendedHashMap) data.getSerializableExtra(sData);

				mTimer.put(Timer.KEY_SERVICE_NAME, map.getString(Service.KEY_NAME));
				mTimer.put(Timer.KEY_REFERENCE, map.getString(Service.KEY_REFERENCE));
				mEditState.setServiceName(mTimer.getString(Timer.KEY_SERVICE_NAME));
			}
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
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
			selectedTags[tc] = mSelectedTags.contains(tag);
			tc++;
		}

		mTagsChanged = false;

		MultiChoiceDialog f = MultiChoiceDialog.newInstance(R.string.choose_tags, tags, selectedTags, R.string.ok,
				R.string.cancel);

		getMultiPaneHandler().showDialogFragment(f, "dialog_select_tags");
	}

	protected boolean onItemSelected(int id) {
		boolean consumed = true;
		int timeFormat = DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H;
		switch (id) {
			case Statics.ITEM_SAVE:
				saveTimer();
				break;

			case Statics.ITEM_CANCEL:
				finish(Activity.RESULT_CANCELED);
				break;

			case Statics.ITEM_PICK_SERVICE:
				pickService();
				break;

			case Statics.ITEM_PICK_BEGIN_DATE:
				MaterialDatePicker datePickerDialogBegin = MaterialDatePicker.Builder.datePicker()
						.setSelection(getCalendar(mBegin).getTimeInMillis())
						.build();
				datePickerDialogBegin.addOnPositiveButtonClickListener(v -> {
					onDateSet(true, (Long) datePickerDialogBegin.getSelection());
				});
				getMultiPaneHandler().showDialogFragment(datePickerDialogBegin, "dialog_pick_begin_date");
				break;

			case Statics.ITEM_PICK_BEGIN_TIME:
				MaterialTimePicker timePickerDialogBegin = new MaterialTimePicker.Builder()
						.setHour(getCalendar(mBegin).get(Calendar.HOUR_OF_DAY))
						.setMinute(getCalendar(mBegin).get(Calendar.MINUTE))
						.setTimeFormat(timeFormat)
						.build();
				timePickerDialogBegin.addOnPositiveButtonClickListener(v -> {
					onTimeSet(true, timePickerDialogBegin.getHour(), timePickerDialogBegin.getMinute());
				});
				getMultiPaneHandler().showDialogFragment(timePickerDialogBegin, "dialog_pick_begin_time");
				break;

			case Statics.ITEM_PICK_END_DATE:
				MaterialDatePicker datePickerDialogEnd = MaterialDatePicker.Builder.datePicker()
						.setSelection(getCalendar(mEnd).getTimeInMillis())
						.build();
				datePickerDialogEnd.addOnPositiveButtonClickListener(v -> {
					onDateSet(false, (Long) datePickerDialogEnd.getSelection());
				});
				getMultiPaneHandler().showDialogFragment(datePickerDialogEnd, "dialog_pick_end_date");
				break;

			case Statics.ITEM_PICK_END_TIME:
				MaterialTimePicker timePickerDialogEnd = new MaterialTimePicker.Builder()
						.setHour(getCalendar(mEnd).get(Calendar.HOUR_OF_DAY))
						.setMinute(getCalendar(mEnd).get(Calendar.MINUTE))
						.setTimeFormat(timeFormat)
						.build();
				timePickerDialogEnd.addOnPositiveButtonClickListener(v -> {
					onTimeSet(false, timePickerDialogEnd.getHour(), timePickerDialogEnd.getMinute());
				});
				getMultiPaneHandler().showDialogFragment(timePickerDialogEnd, "dialog_pick_end_time");
				break;

			case Statics.ITEM_PICK_REPEATED:
				pickRepeatings();
				break;

			case Statics.ITEM_PICK_TAGS:
				pickTags();
				break;

			default:
				consumed = super.onItemSelected(id);
				break;
		}
		return consumed;
	}

	private void pickService() {
		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Service.KEY_REFERENCE, "default");

		Intent intent = new Intent(getContext(), SimpleToolbarFragmentActivity.class);
		intent.putExtra("fragmentClass", TimerServicePickFragment.class);
		intent.putExtra("titleResource", R.string.service);
		intent.putExtra("action", Intent.ACTION_PICK);
		intent.putExtra("serializableData", data);
		getActivity().startActivityForResult(intent, Statics.REQUEST_PICK_SERVICE);
	}

	/**
	 * Sync Compose state from <code>mTimer</code>
	 */
	protected void reload() {
		mBegin = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_BEGIN));
		mEnd = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_END));

		int repeatedValue = 0;
		try {
			repeatedValue = DateTime.parseTimestamp(mTimer.getString(Timer.KEY_REPEATED));
		} catch (NumberFormatException ex) {
			ex.printStackTrace();
		}
		String repeatedText = getRepeated(repeatedValue);

		String text = mTimer.getString(Timer.KEY_TAGS);
		if (text == null) {
			text = "";
		}
		mSelectedTags.clear();
		if (!text.isEmpty()) {
			String[] tags = text.split(" ");
			Collections.addAll(mSelectedTags, tags);
		}

		List<String> afterEvents = new ArrayList<>();
		for (CharSequence cs : getResources().getTextArray(R.array.afterevents)) {
			afterEvents.add(cs.toString());
		}
		mEditState.loadFrom(mTimer, afterEvents, DreamDroid.getLocations(), repeatedText);
	}

	@NonNull
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

	@NonNull
	private String setRepeated(@NonNull boolean[] checkedDays, @NonNull ExtendedHashMap timer) {
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

	private void applyViewValues() {
		mEditState.applyTo(mTimer);
	}

	private void saveTimer() {
		Log.i(TAG, "saveTimer()");
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		Activity activity = getAppCompatActivity();
		mProgress = ProgressDialog.show(activity, "", getText(R.string.saving), true);

		applyViewValues();
		ArrayList<NameValuePair> params = Timer.getSaveParams(mTimer, mTimerOld);
		execSimpleResultTask(new TimerChangeRequestHandler(), params);
	}

	@Override
	public void onSimpleResult(boolean success, @NonNull ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}

		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
			finish(Activity.RESULT_OK);
		}
	}

	private void updateBegin(@NonNull Calendar cal) {
		mBegin = (int) (cal.getTimeInMillis() / 1000);
		String timestamp = Long.valueOf(mBegin).toString();
		mTimer.put(Timer.KEY_BEGIN, timestamp);
		mTimer.put(Timer.KEY_BEGIN_READEABLE, DateTime.getYearDateTimeString(timestamp));
		mEditState.setBeginEndLabels(mBegin, mEnd);
	}

	private void updateEnd(@NonNull Calendar cal) {
		mEnd = (int) (cal.getTimeInMillis() / 1000);
		String timestamp = Long.valueOf(mEnd).toString();
		mTimer.put(Timer.KEY_END, timestamp);
		mTimer.put(Timer.KEY_END_READABLE, DateTime.getYearDateTimeString(timestamp));
		mEditState.setBeginEndLabels(mBegin, mEnd);
	}

	@Override
	public void onMultiChoiceDialogSelection(String dialogTag, DialogInterface dialog, @NonNull Integer[] selected) {
		if ("dialog_select_tags".equals(dialogTag)) {
			ArrayList<String> tags = DreamDroid.getTags();
			ArrayList<String> selectedTags = new ArrayList<>();
			for (Integer which : selected) {
				selectedTags.add(tags.get(which));
			}
			mTagsChanged = !selectedTags.equals(mSelectedTags);
			mSelectedTags = selectedTags;
		} else if ("dialog_select_repeatings".equals(dialogTag)) {
			Arrays.fill(mCheckedDays, false);
			for (Integer which : selected) {
				mCheckedDays[which] = true;
			}
			String text = setRepeated(mCheckedDays, mTimer);
			mEditState.setRepeatedLabel(text);
		}
	}

	@Override
	public void onMultiChoiceDialogFinish(String dialogTag, int result) {
		if ("dialog_select_tags".equals(dialogTag) && mTagsChanged) {
			String tags = Tag.implodeTags(mSelectedTags);
			mTimer.put(Timer.KEY_TAGS, tags);
			mEditState.setTagsLabel(tags);
		}
	}

	@NonNull
	private Calendar getCalendar(int time) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis((long) time * 1000);
		return cal;
	}

	public void onDateSet(boolean isBegin, Long millis) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(millis);
		onDateSet(isBegin, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
	}

	public void onDateSet(boolean isBegin, int year, int month, int day) {
		int time = isBegin ? mBegin : mEnd;

		Calendar cal = getCalendar(time);

		if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && cal.get(Calendar.DATE) == day)
			return;
		cal.set(year, month, day);

		onTimeChanged(isBegin, cal);
	}

	public void onTimeSet(boolean isBegin, int hourOfDay, int minute) {
		int time = isBegin ? mBegin : mEnd;
		Calendar cal = getCalendar(time);
		if (cal.get(Calendar.HOUR_OF_DAY) == hourOfDay && cal.get(Calendar.MINUTE) == minute)
			return;
		cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
		cal.set(Calendar.MINUTE, minute);

		onTimeChanged(isBegin, cal);
	}

	private void onTimeChanged(boolean isBegin, @NonNull Calendar cal) {
		if (isBegin)
			updateBegin(cal);
		else
			updateEnd(cal);
	}

	private void onGetLocationsAndTagsProgress(String title, String progress) {

		if (mLocationsAndTagsProgress != null) {
			if (!mLocationsAndTagsProgress.isShowing()) {
				mLocationsAndTagsProgress = ProgressDialog.show(getAppCompatActivity(), title, progress);
			} else {
				mLocationsAndTagsProgress.setMessage(progress);
			}
		} else {
			mLocationsAndTagsProgress = ProgressDialog.show(getAppCompatActivity(), title, progress);
		}

	}

	private void onLocationsAndTagsReady() {
		if (mLocationsAndTagsProgress != null) {
			mLocationsAndTagsProgress.dismiss();
			mLocationsAndTagsProgress = null;
		}
		reload();
	}
}
