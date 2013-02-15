/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.helpers.Statics;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.actionbarsherlock.app.SherlockListFragment;

/**
 * @author sre
 * 
 */
public abstract class DreamDroidListFragment extends SherlockListFragment implements ActivityCallbackHandler {
	protected String mCurrentTitle;
	protected String mBaseTitle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		mBaseTitle = mCurrentTitle = getString(R.string.app_name);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getSherlockActivity().setTitle(mCurrentTitle);
	}

	@Override
	public void onResume() {
		super.onResume();
		getMultiPaneHandler().onDetailFragmentResume(this);
	}

	@Override
	public void onPause() {
		MultiPaneHandler mph = getMultiPaneHandler();
		if (mph != null)
			mph.onDetailFragmentPause(this);
		super.onPause();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		super.onSaveInstanceState(outState);
	}

	protected MultiPaneHandler getMultiPaneHandler() {
		return (MultiPaneHandler) getSherlockActivity();
	}

	protected void finish(int resultCode) {
		finish(resultCode, null);
	}

	protected void finish(int resultCode, Intent data) {
		MultiPaneHandler mph = getMultiPaneHandler();
		if (mph.isMultiPane()) {
			Fragment f = getTargetFragment();
			if (f != null) {
				mph.showDetails(f);
				if (resultCode != Statics.RESULT_NONE || data != null)
					f.onActivityResult(getTargetRequestCode(), resultCode, data);
			}
		} else {
			Activity a = getSherlockActivity();
			a.setResult(resultCode, data);
			a.finish();
		}
	}
}
