/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import net.reichholf.dreamdroid.DreamDroid;

/**
 * @author sre
 * 
 */
public class AbstractDialog extends DialogFragment {
	protected boolean mNoTheming;

	public AbstractDialog() {
		super();
		mNoTheming = Build.VERSION.SDK_INT < 21; //Only for Lollipop
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if(!mNoTheming) {
			DreamDroid.setDialogTheme(getActivity(), this);
		}
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onDestroyView() {
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		super.onSaveInstanceState(outState);
	}
}
