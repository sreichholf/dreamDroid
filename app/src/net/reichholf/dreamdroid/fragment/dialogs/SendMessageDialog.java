/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.afollestad.materialdialogs.MaterialDialog;

import net.reichholf.dreamdroid.R;

/**
 * @author sre
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
		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
		builder.customView(R.layout.send_message_dialog)
				.title(R.string.send_message)
				.positiveText(R.string.send)
				.negativeText(R.string.cancel)
				.callback(new MaterialDialog.Callback() {
					@Override
					public void onNegative(MaterialDialog materialDialog) {
					}

					@Override
					public void onPositive(MaterialDialog materialDialog) {
						View view = materialDialog.getCustomView();
						EditText text = (EditText) view.findViewById(R.id.EditTextMessage);
						EditText timeout = (EditText) view.findViewById(R.id.EditTextTimeout);

						Spinner type = (Spinner) view.findViewById(R.id.SpinnerMessageType);
						String t = Integer.valueOf(type.getSelectedItemPosition()).toString();

						((SendMessageDialogActionListener) getActivity()).onSendMessage(text.getText().toString(), t, timeout.getText().toString());
					}
				});
		final MaterialDialog dialog = builder.build();

		Spinner spinnerType = (Spinner) dialog.getCustomView().findViewById(R.id.SpinnerMessageType);
		spinnerType.setSelection(2);

		return dialog;
	}
}
