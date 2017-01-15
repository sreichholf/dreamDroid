/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;

import java.util.HashMap;

import biz.kasual.materialnumberpicker.MaterialNumberPicker;

/**
 * @author sre
 */
public class SleepTimerDialog extends AbstractDialog {
	private static final String KEY_TIMER = "timer";
	private ExtendedHashMap mSleepTimer;

	public static SleepTimerDialog newInstance(ExtendedHashMap sleepTimer) {
		SleepTimerDialog f = new SleepTimerDialog();
		Bundle args = new Bundle();
		args.putParcelable(KEY_TIMER, sleepTimer);
		f.setArguments(args);
		return f;
	}

	public SleepTimerDialog() {
	}

	@SuppressWarnings("unchecked")
	private void init() {
		mSleepTimer = new ExtendedHashMap((HashMap<String, Object>) getArguments().getSerializable(KEY_TIMER));
	}

	public interface SleepTimerDialogActionListener {
		void onSetSleepTimer(String time, String action, boolean enabled);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		init();
		final View view = LayoutInflater.from(getContext()).inflate(R.layout.sleeptimer, null);
		final MaterialNumberPicker time = (MaterialNumberPicker) view.findViewById(R.id.NumberPicker);
		final CheckBox enabled = (CheckBox) view.findViewById(R.id.CheckBoxEnabled);
		final RadioGroup action = (RadioGroup) view.findViewById(R.id.RadioGroupAction);

		time.setMinValue(0);
		time.setMaxValue(999);

		int min = 90;
		try {
			min = Integer.parseInt(mSleepTimer.getString(SleepTimer.KEY_MINUTES));
		} catch (NumberFormatException nfe) {
		}

		boolean enable = Python.TRUE.equals(mSleepTimer.getString(SleepTimer.KEY_ENABLED));
		String act = mSleepTimer.getString(SleepTimer.KEY_ACTION);

		time.setValue(min);
		enabled.setChecked(enable);

		if (SleepTimer.ACTION_SHUTDOWN.equals(act))
			action.check(R.id.RadioButtonShutdown);
		else
			action.check(R.id.RadioButtonStandby);

		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.sleeptimer)
				.setView(view)
				.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						MaterialNumberPicker time = (MaterialNumberPicker) view.findViewById(R.id.NumberPicker);
						CheckBox enabled = (CheckBox) view.findViewById(R.id.CheckBoxEnabled);
						RadioGroup action = (RadioGroup) view.findViewById(R.id.RadioGroupAction);

						String t = Integer.valueOf(time.getValue()).toString();
						int id = action.getCheckedRadioButtonId();
						String a = SleepTimer.ACTION_STANDBY;

						if (id == R.id.RadioButtonShutdown) {
							a = SleepTimer.ACTION_SHUTDOWN;
						}

						((SleepTimerDialogActionListener) getActivity()).onSetSleepTimer(t, a, enabled.isChecked());
					}
				})
				.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dismiss();
					}
				});
		return builder.create();
	}
}
