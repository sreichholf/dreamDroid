/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.helper.DreamDroidFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.MutliPaneContent;
import net.reichholf.dreamdroid.helpers.Statics;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragment;

/**
 * @author sre
 * 
 */
public abstract class DreamDroidFragment extends SherlockFragment implements ActivityCallbackHandler, MutliPaneContent {
	private DreamDroidFragmentHelper mHelper = null;

	public DreamDroidFragment() {
		super();
		mHelper = new DreamDroidFragmentHelper();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mHelper.onAttach(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (mHelper == null)
			mHelper = new DreamDroidFragmentHelper(this);
		else
			mHelper.bindToFragment(this);
		mHelper.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mHelper.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		mHelper.onResume();
	}

	@Override
	public void onPause() {
		mHelper.onPause();
		super.onPause();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		mHelper.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
	}

	public String getBaseTitle() {
		return mHelper.getBaseTitle();
	}

	public void setBaseTitle(String baseTitle) {
		mHelper.setBaseTitle(baseTitle);
	}

	public String getCurrentTitle() {
		return mHelper.getCurrenTtitle();
	}

	public void setCurrentTitle(String currentTitle) {
		mHelper.setCurrentTitle(currentTitle);
	}

	public void initTitles(String title) {
		mHelper.setBaseTitle(title);
		mHelper.setCurrentTitle(title);
	}

	public MultiPaneHandler getMultiPaneHandler() {
		return mHelper.getMultiPaneHandler();
	}

	protected void finish() {
		finish(Statics.RESULT_NONE, null);
	}

	protected void finish(int resultCode) {
		finish(resultCode, null);
	}

	protected void finish(int resultCode, Intent data) {
		mHelper.finish(resultCode, data);
	}
}
