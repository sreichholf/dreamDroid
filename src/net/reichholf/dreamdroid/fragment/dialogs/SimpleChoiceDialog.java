/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import net.reichholf.dreamdroid.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * @author sre
 * 
 */
public class SimpleChoiceDialog extends PrimitiveDialog {
	private int[] mActionIds;
	private CharSequence[] mActions;

	public static SimpleChoiceDialog newInstance(String title, CharSequence[] actions, int[] actionIds) {
		SimpleChoiceDialog fragment = new SimpleChoiceDialog(actions, actionIds);
		Bundle args = new Bundle();
		args.putString("title", title);
		fragment.setArguments(args);
		return fragment;
	}

	public SimpleChoiceDialog(CharSequence[] actions, int[] actionIds) {
		mActions = actions;
		mActionIds = actionIds;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setRetainInstance(true);
		AlertDialog.Builder adBuilder = new AlertDialog.Builder(getActivity());
		adBuilder.setTitle(getText(R.string.pick_action));
		adBuilder.setItems(mActions, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				finishDialog(mActionIds[which]);
			}
		});

		return adBuilder.create();
	}
}
