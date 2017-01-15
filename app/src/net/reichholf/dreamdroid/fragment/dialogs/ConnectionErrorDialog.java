package net.reichholf.dreamdroid.fragment.dialogs;


import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

import net.reichholf.dreamdroid.R;

/**
 * Created by Reichi on 19.04.2015.
 */
public class ConnectionErrorDialog extends ActionDialog {
	private static String KEY_TITLE = "title";
	private static String KEY_TEXT = "text";

	public static int ACTION_POSITIVE = 0x00;
	public static int ACTION_EDIT_PROFILE = 0x01;

	public static ConnectionErrorDialog newInstance(String title, String text) {
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

		return new AlertDialog.Builder(getContext())
				.setTitle(title)
				.setMessage(text)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finishDialog(ACTION_POSITIVE, null);
					}
				})
				.setNeutralButton(R.string.edit_profile, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finishDialog(ACTION_EDIT_PROFILE, null);
					}
				}).create();
	}
}
