/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import net.reichholf.dreamdroid.DreamDroid;

/**
 * @author sre
 * 
 */
public class AbstractDialog extends DialogFragment {
	protected boolean mIsThemeSet;

	public AbstractDialog() {
		super();
		mIsThemeSet = Build.VERSION.SDK_INT < 21; //Only for Lollipop
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if(!mIsThemeSet) {
			DreamDroid.setDialogTheme(getActivity(), this);
			mIsThemeSet = true;
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
