/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.helper;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.interfaces.MutliPaneContent;
import net.reichholf.dreamdroid.helpers.Statics;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.actionbarsherlock.app.SherlockFragmentActivity;

public class DreamDroidFragmentHelper {
	private Fragment mFragment;
	protected String mCurrentTitle;
	protected String mBaseTitle;

	public DreamDroidFragmentHelper() {

	}

	public DreamDroidFragmentHelper(Fragment fragment) {
		mFragment = fragment;
	}

	public void bindToFragment(Fragment fragment) {
		mFragment = fragment;
	}

	public SherlockFragmentActivity getSherlockActivity() {
		return (SherlockFragmentActivity) mFragment.getActivity();
	}

	public void onCreate(Bundle savedInstanceState) {
		mBaseTitle = mCurrentTitle = mFragment.getString(R.string.app_name);
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		getSherlockActivity().setTitle(mCurrentTitle);
	}

	public void onResume() {
		getMultiPaneHandler().onFragmentResume(mFragment);
	}

	public void onPause() {
		MultiPaneHandler mph = getMultiPaneHandler();
		if (mph != null)
			mph.onFragmentPause(mFragment);
	}

	public void onSaveInstanceState(Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
	}

	public MultiPaneHandler getMultiPaneHandler() {
		return (MultiPaneHandler) getSherlockActivity();
	}

	public String getBaseTitle() {
		// return mBaseTitle;
		return "";
	}

	public void setBaseTitle(String baseTitle) {
		mBaseTitle = baseTitle;
	}

	public String getCurrenTtitle() {
		return mCurrentTitle;
	}

	public void setCurrentTitle(String currentTitle) {
		mCurrentTitle = currentTitle;
	}

	public void finish(int resultCode, Intent data) {
		MultiPaneHandler mph = ((MutliPaneContent) mFragment).getMultiPaneHandler();
		if (mph.isMultiPane()) {
			boolean explicitShow = false;
			FragmentManager fm = getSherlockActivity().getSupportFragmentManager();
			if (fm.getBackStackEntryCount() > 0) {
				fm.popBackStackImmediate();
			} else {
				explicitShow = true;
			}
			Fragment target = mFragment.getTargetFragment();
			FragmentTransaction ft = getSherlockActivity().getSupportFragmentManager().beginTransaction();
			ft.remove(mFragment);
			ft.commit();

			if (target != null) {
				if (resultCode != Statics.RESULT_NONE || data != null) {
					if (explicitShow)
						mph.showDetails(target);
					target.onActivityResult(mFragment.getTargetRequestCode(), resultCode, data);
				}
			}
		} else {
			getSherlockActivity().setResult(resultCode, data);
			getSherlockActivity().finish();
		}
	}
}
