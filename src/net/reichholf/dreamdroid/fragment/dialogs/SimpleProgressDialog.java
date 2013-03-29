/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;

/**
 * @author sre
 * 
 */
public class SimpleProgressDialog extends ActionDialog {
	private static final String KEY_TITLE = "title";
	private static final String KEY_MESSAGE = "message";

	String mTitle;
	String mMessage;

	public static SimpleProgressDialog newInstance(String title, String message) {
		SimpleProgressDialog fragment = new SimpleProgressDialog();
		Bundle args = new Bundle();
		args.putString("message", message);
		fragment.setArguments(args);
		return fragment;
	}

	private void init() {
		Bundle args = getArguments();
		mTitle = args.getString(KEY_TITLE);
		mMessage = args.getString(KEY_MESSAGE);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setRetainInstance(true);
		init();
		final ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setTitle(mTitle);
		dialog.setMessage(mMessage);
		dialog.setIndeterminate(true);
		dialog.setCancelable(false);

		return dialog;
	}
}
