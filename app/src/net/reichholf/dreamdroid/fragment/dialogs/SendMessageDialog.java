/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.reichholf.dreamdroid.R;

/**
 * @author sre
 */
public class SendMessageDialog extends AbstractDialog {
    public static SendMessageDialog newInstance() {
        return new SendMessageDialog();
    }

    public interface SendMessageDialogActionListener {
        void onSendMessage(String text, String type, String timeout);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getContext()).inflate(R.layout.send_message_dialog, null);
        Spinner spinnerType = view.findViewById(R.id.SpinnerMessageType);
        spinnerType.setSelection(2);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(R.string.send_message)
                .setView(view)
                .setPositiveButton(R.string.send, (dialog, which) -> {
                    EditText text = view.findViewById(R.id.EditTextMessage);
                    EditText timeout = view.findViewById(R.id.EditTextTimeout);
                    Spinner type = view.findViewById(R.id.SpinnerMessageType);
                    String t = Integer.valueOf(type.getSelectedItemPosition()).toString();
                    ((SendMessageDialogActionListener) getActivity()).onSendMessage(text.getText().toString(), t, timeout.getText().toString());
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss());
        return builder.create();
    }
}
