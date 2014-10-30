/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.helper.DreamDroidFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.MutliPaneContent;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.view.EnhancedFloatingActionButton;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Toast;

import java.util.zip.Inflater;


/**
 * @author sre
 * 
 */
public abstract class DreamDroidFragment extends Fragment implements ActivityCallbackHandler, MutliPaneContent {
	private DreamDroidFragmentHelper mHelper = null;
	protected boolean mShouldRetainInstance = true;

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
		if(mShouldRetainInstance)
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

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		super.onCreateOptionsMenu(menu, inflater);
		if(!getMultiPaneHandler().isDrawerOpen())
			createOptionsMenu(menu, inflater);
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater)
	{
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
	
	protected ActionBarActivity getActionBarActivity() {
		return (ActionBarActivity) getActivity();
	}

	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getActionBarActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(getActionBarActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	protected void registerFab(int id, View view, View.OnClickListener onClickListener){
		EnhancedFloatingActionButton fab = (EnhancedFloatingActionButton) view.findViewById(id);
		if (fab == null)
			return;

		fab.setOnClickListener(onClickListener);
		fab.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				Toast.makeText(getActionBarActivity(), v.getContentDescription(), Toast.LENGTH_SHORT).show();
				return true;
			}
		});
	}
}
