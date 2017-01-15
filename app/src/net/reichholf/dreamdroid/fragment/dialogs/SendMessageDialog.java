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
import android.widget.EditText;
import android.widget.Spinner;

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

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final View view = LayoutInflater.from(getContext()).inflate(R.layout.send_message_dialog, null);
		Spinner spinnerType = (Spinner) view.findViewById(R.id.SpinnerMessageType);
		spinnerType.setSelection(2);

		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.send_message)
				.setView(view)
				.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						EditText text = (EditText) view.findViewById(R.id.EditTextMessage);
						EditText timeout = (EditText) view.findViewById(R.id.EditTextTimeout);
						Spinner type = (Spinner) view.findViewById(R.id.SpinnerMessageType);
						String t = Integer.valueOf(type.getSelectedItemPosition()).toString();
						((SendMessageDialogActionListener) getActivity()).onSendMessage(text.getText().toString(), t, timeout.getText().toString());
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
