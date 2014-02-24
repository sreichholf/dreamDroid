/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import net.reichholf.dreamdroid.R;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

/**
 * @author sre
 * 
 */
public class SendMessageDialog extends AbstractDialog {
	public static SendMessageDialog newInstance() {
		return new SendMessageDialog();
	}

	public interface SendMessageDialogActionListener {
		public void onSendMessage(String text, String type, String timeout);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final Dialog dialog = new Dialog(getActivity());
		dialog.setContentView(R.layout.send_message_dialog);
		dialog.setTitle(R.string.send_message);

		Button buttonCancel = (Button) dialog.findViewById(R.id.ButtonCancel);
		buttonCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		Button buttonSend = (Button) dialog.findViewById(R.id.ButtonSend);
		buttonSend.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText text = (EditText) dialog.findViewById(R.id.EditTextMessage);
				EditText timeout = (EditText) dialog.findViewById(R.id.EditTextTimeout);

				Spinner type = (Spinner) dialog.findViewById(R.id.SpinnerMessageType);
				String t = Integer.valueOf(type.getSelectedItemPosition()).toString();

				((SendMessageDialogActionListener) getActivity()).onSendMessage(text.getText().toString(), t, timeout.getText().toString());
			}
		});

		Spinner spinnerType = (Spinner) dialog.findViewById(R.id.SpinnerMessageType);
		spinnerType.setSelection(2);
		
		return dialog;
	}
}
