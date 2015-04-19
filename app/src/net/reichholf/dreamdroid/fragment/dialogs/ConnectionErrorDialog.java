package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import net.reichholf.dreamdroid.R;

/**
 * Created by Reichi on 19.04.2015.
 */
public class ConnectionErrorDialog extends ActionDialog {
	private static String KEY_TITLE = "title";
	private static String KEY_TEXT = "text";

	public static int ACTION_POSITIVE = 0x00;
	public static int ACTION_EDIT_PROFILE = 0x01;

	public static ConnectionErrorDialog newInstance(String title, String text){
		Bundle args = new Bundle();
		args.putString(KEY_TITLE, title);
		args.putString(KEY_TEXT, text);

		ConnectionErrorDialog d = new ConnectionErrorDialog();
		d.setArguments(args);
		return d;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();
		String title = args.getString(KEY_TITLE);
		String text = args.getString(KEY_TEXT);

		Dialog dialog = new MaterialDialog.Builder(getActivity())
				.title(title)
				.content(text)
				.contentColor(Color.RED)
				.positiveText(R.string.ok)
				.neutralText(R.string.edit_profile)
				.callback(new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						finishDialog(ACTION_POSITIVE, null);
					}

					@Override
					public void onNeutral(MaterialDialog dialog) {
						finishDialog(ACTION_EDIT_PROFILE, null);
						super.onNeutral(dialog);
					}
				})
				.build();
		return dialog;
	}
}
