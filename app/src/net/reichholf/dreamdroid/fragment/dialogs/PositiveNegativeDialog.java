/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;

import net.reichholf.dreamdroid.DreamDroid;

/**
 * @author sre
 * 
 */
public class PositiveNegativeDialog extends ActionDialog {
	private int mMessageId;
	private int mPositiveText;
	private int mPositiveId;
	private int mNegativeText;
	private int mNegativeId;

	private static String KEY_TITLE = "title";
	private static String KEY_MESSAGE_ID = "messageId";
	private static String KEY_POSITIVE_TEXT = "positiveText";
	private static String KEY_POSITIVE_ID = "positiveId";
	private static String KEY_NEGATIVE_TEXT = "negativeText";
	private static String KEY_NEGATIVE_ID = "negativeId";

	public static PositiveNegativeDialog newInstance(String title, int messageId, int positiveText, int positiveId,
			int negativeText, int negativeId) {

		PositiveNegativeDialog fragment = new PositiveNegativeDialog();
		Bundle args = new Bundle();
		args.putString(KEY_TITLE, title);
		args.putInt(KEY_MESSAGE_ID, messageId);
		args.putInt(KEY_POSITIVE_TEXT, positiveText);
		args.putInt(KEY_POSITIVE_ID, positiveId);
		args.putInt(KEY_NEGATIVE_TEXT, negativeText);
		args.putInt(KEY_NEGATIVE_ID, negativeId);
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mIsThemeSet = Build.VERSION.SDK_INT < 21 || false; //TODO FIXME PLEASE this is bad!
	}

	private void init() {
		Bundle args = getArguments();
		mMessageId = args.getInt(KEY_MESSAGE_ID);
		mPositiveText = args.getInt(KEY_POSITIVE_TEXT);
		mPositiveId = args.getInt(KEY_POSITIVE_ID);
		mNegativeText = args.getInt(KEY_NEGATIVE_TEXT);
		mNegativeId = args.getInt(KEY_NEGATIVE_ID);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setRetainInstance(true);
		init();
		AlertDialog.Builder builder;
		ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), DreamDroid.getDialogTheme(getActivity()));
		if(!mIsThemeSet)
			builder = new AlertDialog.Builder(getActivity(), DreamDroid.getDialogTheme(getActivity()));
		else
			builder = new AlertDialog.Builder(getActivity());

		if (mMessageId != -1)
			builder.setMessage(getString(mMessageId));
		builder.setTitle(getArguments().getString("title")).setCancelable(false)
				.setPositiveButton(getText(mPositiveText), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						finishDialog(mPositiveId, null);
						dialog.dismiss();
					}
				}).setNegativeButton(getText(mNegativeText), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
						finishDialog(mNegativeId, null);
					}
				});
		return builder.create();
	}
}
