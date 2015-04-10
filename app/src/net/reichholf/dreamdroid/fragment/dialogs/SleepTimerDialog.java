/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import com.afollestad.materialdialogs.MaterialDialog;
import com.michaelnovakjr.numberpicker.NumberPicker;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;

import java.util.HashMap;

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
		public void onSetSleepTimer(String time, String action, boolean enabled);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		init();
		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
		builder.customView(R.layout.sleeptimer, true)
				.title(R.string.sleeptimer)
				.positiveText(R.string.save)
				.negativeText(R.string.cancel)
				.callback(new MaterialDialog.ButtonCallback() {

					@Override
					public void onNegative(MaterialDialog materialDialog) {
					}

					@Override
					public void onPositive(MaterialDialog materialDialog) {
						View view = materialDialog.getCustomView();
						NumberPicker time = (NumberPicker) view.findViewById(R.id.NumberPicker);
						CheckBox enabled = (CheckBox) view.findViewById(R.id.CheckBoxEnabled);
						RadioGroup action = (RadioGroup) view.findViewById(R.id.RadioGroupAction);

						String t = Integer.valueOf(time.getCurrent()).toString();
						int id = action.getCheckedRadioButtonId();
						String a = SleepTimer.ACTION_STANDBY;

						if (id == R.id.RadioButtonShutdown) {
							a = SleepTimer.ACTION_SHUTDOWN;
						}

						((SleepTimerDialogActionListener) getActivity()).onSetSleepTimer(t, a, enabled.isChecked());
					}
				});
		final MaterialDialog dialog = builder.build();
		View view = dialog.getCustomView();

		final NumberPicker time = (NumberPicker) view.findViewById(R.id.NumberPicker);
		final CheckBox enabled = (CheckBox) view.findViewById(R.id.CheckBoxEnabled);
		final RadioGroup action = (RadioGroup) view.findViewById(R.id.RadioGroupAction);

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

		return dialog;
	}
}
