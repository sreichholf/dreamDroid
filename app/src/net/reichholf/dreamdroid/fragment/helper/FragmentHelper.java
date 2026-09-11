/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.helper;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment;
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment;


public class FragmentHelper {
	private Fragment mFragment;
	protected String mCurrentTitle;
	protected String mBaseTitle;

	public FragmentHelper() {

	}

	public FragmentHelper(Fragment fragment) {
		mFragment = fragment;
	}

	public void bindToFragment(Fragment fragment) {
		mFragment = fragment;
	}

	@Nullable
	public AppCompatActivity getAppCompatActivity() {
		return (AppCompatActivity) mFragment.getActivity();
	}

	public void onCreate(Bundle savedInstanceState) {
		mBaseTitle = mCurrentTitle = mFragment.getString(R.string.app_name_release);
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		getAppCompatActivity().setTitle(mCurrentTitle);
		View header = getAppCompatActivity().findViewById(R.id.content_header);
		boolean hasHeader = ((IBaseFragment) mFragment).hasHeader();
		if (header == null)
			return;
		if(hasHeader)
			header.setVisibility(View.VISIBLE);
		else
			header.setVisibility(View.GONE);
	}

	public void onResume() {
		getMultiPaneHandler().onFragmentResume(mFragment);
	}

	public void onPause() {
		MultiPaneHandler mph = getMultiPaneHandler();
		if (mph != null)
			mph.onFragmentPause(mFragment);
	}

	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
	}

	@Nullable
	public MultiPaneHandler getMultiPaneHandler() {
		return (MultiPaneHandler) getAppCompatActivity();
	}

	public String getBaseTitle() {
		return mBaseTitle;
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

	public void finish(int resultCode, @Nullable Intent data) {
		Fragment walker = mFragment.getParentFragment();
		while (walker != null) {
			if (walker instanceof PhoneNavHostFragment) {
				((PhoneNavHostFragment) walker).deliverPickResult(resultCode, data);
				return;
			}
			walker = walker.getParentFragment();
		}
		// Nested leaves finish only under PhoneNavHost; remaining hosts close themselves.
		getAppCompatActivity().setResult(resultCode, data);
		getAppCompatActivity().finish();
	}
}
