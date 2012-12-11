/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

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
	
	public static PositiveNegativeDialog newInstance(String title, int messageId, int positiveText, int positiveId,
			int negativeString, int negativeId) {
		PositiveNegativeDialog fragment = new PositiveNegativeDialog(messageId, positiveText, positiveId, negativeString, negativeId);
		Bundle args = new Bundle();
		args.putString("title", title);
		fragment.setArguments(args);
		return fragment;
	}

	private PositiveNegativeDialog(int messageId, int positiveText, int positiveId, int negativeText, int negativeId){
		mMessageId = messageId;
		mPositiveText = positiveText;
		mPositiveId = positiveId;
		mNegativeText = negativeText;
		mNegativeId = negativeId;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setRetainInstance(true);
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getArguments().getString("title")).setMessage(getString(mMessageId))
				.setCancelable(false)
				.setPositiveButton(getText(mPositiveText), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						finishDialog(mPositiveId);
						dialog.dismiss();
					}
				}).setNegativeButton(getText(mNegativeText), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
						finishDialog(mNegativeId);
					}
				});
		return builder.create();
	}
}
