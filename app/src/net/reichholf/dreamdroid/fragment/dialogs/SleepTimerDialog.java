/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import com.github.stephenvinouze.materialnumberpickercore.MaterialNumberPicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;

/**
 * @author sre
 */
public class SleepTimerDialog extends AbstractDialog {
    private static final String KEY_TIMER = "timer";
    private ExtendedHashMap mSleepTimer;

    public static SleepTimerDialog newInstance(ExtendedHashMap sleepTimer) {
        SleepTimerDialog f = new SleepTimerDialog();
        Bundle args = new Bundle();
        args.putSerializable(KEY_TIMER, sleepTimer);
        f.setArguments(args);
        return f;
    }

    @SuppressWarnings("unchecked")
    private void init() {
        mSleepTimer = ((ExtendedHashMap) getArguments().getSerializable(KEY_TIMER)).clone();
    }

    public interface SleepTimerDialogActionListener {
        void onSetSleepTimer(String time, String action, boolean enabled);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        init();
        final View view = LayoutInflater.from(getContext()).inflate(R.layout.sleeptimer, null);
        final MaterialNumberPicker time = view.findViewById(R.id.NumberPicker);
        final CheckBox enabled = view.findViewById(R.id.CheckBoxEnabled);
        final RadioGroup action = view.findViewById(R.id.RadioGroupAction);

        time.setMinValue(0);
        time.setMaxValue(999);

        int min = 90;
        try {
            min = Integer.parseInt(mSleepTimer.getString(SleepTimer.KEY_MINUTES));
        } catch (NumberFormatException nfe) {
            Log.w("bla", nfe.getLocalizedMessage());
        }

        boolean enable = Python.TRUE.equals(mSleepTimer.getString(SleepTimer.KEY_ENABLED));
        String act = mSleepTimer.getString(SleepTimer.KEY_ACTION);

        time.setValue(min);
        enabled.setChecked(enable);

        if (SleepTimer.ACTION_SHUTDOWN.equals(act))
            action.check(R.id.RadioButtonShutdown);
        else
            action.check(R.id.RadioButtonStandby);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(R.string.sleeptimer)
                .setView(view)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    MaterialNumberPicker time1 = view.findViewById(R.id.NumberPicker);
                    CheckBox enabled1 = view.findViewById(R.id.CheckBoxEnabled);
                    RadioGroup action1 = view.findViewById(R.id.RadioGroupAction);

                    String t = Integer.valueOf(time1.getValue()).toString();
                    int id = action1.getCheckedRadioButtonId();
                    String a = SleepTimer.ACTION_STANDBY;

                    if (id == R.id.RadioButtonShutdown) {
                        a = SleepTimer.ACTION_SHUTDOWN;
                    }

                    ((SleepTimerDialogActionListener) getActivity()).onSetSleepTimer(t, a, enabled1.isChecked());
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss());
        return builder.create();
    }
}
