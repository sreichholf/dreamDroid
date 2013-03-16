/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import com.michaelnovakjr.numberpicker.NumberPicker;

/**
 * @author sre
 * 
 */
public class SleepTimerDialog extends AbstractDialog {
	private ExtendedHashMap mSleepTimer;

	public static SleepTimerDialog newInstance(ExtendedHashMap sleepTimer) {
		return new SleepTimerDialog(sleepTimer);
	}

	public SleepTimerDialog(ExtendedHashMap sleepTimer) {
		mSleepTimer = sleepTimer;
	}

	public interface SleepTimerDialogActionListener {
		public void onSetSleepTimer(String time, String action, boolean enabled);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final Dialog dialog = new Dialog(getActivity());
		dialog.setContentView(R.layout.sleeptimer);
		dialog.setTitle(R.string.sleeptimer);
		final NumberPicker time = (NumberPicker) dialog.findViewById(R.id.NumberPicker);
		final CheckBox enabled = (CheckBox) dialog.findViewById(R.id.CheckBoxEnabled);
		final RadioGroup action = (RadioGroup) dialog.findViewById(R.id.RadioGroupAction);

		time.setRange(0, 999);

		int min = 90;
		try {
			min = Integer.parseInt(mSleepTimer.getString(SleepTimer.KEY_MINUTES));
		} catch (NumberFormatException nfe) {
		}

		boolean enable = Python.TRUE.equals(mSleepTimer.getString(SleepTimer.KEY_ENABLED));
		String act = mSleepTimer.getString(SleepTimer.KEY_ACTION);

		time.setCurrent(min);
		enabled.setChecked(enable);

		if (SleepTimer.ACTION_SHUTDOWN.equals(act)) {
			action.check(R.id.RadioButtonShutdown);
		} else {
			action.check(R.id.RadioButtonStandby);
		}

		Button buttonCloseSleepTimer = (Button) dialog.findViewById(R.id.ButtonClose);
		buttonCloseSleepTimer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}

		});

		Button buttonSaveSleepTimer = (Button) dialog.findViewById(R.id.ButtonSave);
		buttonSaveSleepTimer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String t = Integer.valueOf(time.getCurrent()).toString();
				int id = action.getCheckedRadioButtonId();
				String a = SleepTimer.ACTION_STANDBY;

				if (id == R.id.RadioButtonShutdown) {
					a = SleepTimer.ACTION_SHUTDOWN;
				}

				((SleepTimerDialogActionListener) getActivity()).onSetSleepTimer(t, a, enabled.isChecked());
				dialog.dismiss();
			}
		});

		return dialog;
	}
}
