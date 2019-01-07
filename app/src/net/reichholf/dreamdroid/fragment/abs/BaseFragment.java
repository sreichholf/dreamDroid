/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.livefront.bridge.Bridge;

import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Toast;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.MainActivity;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.helper.FragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment;
import net.reichholf.dreamdroid.fragment.interfaces.IMutliPaneContent;
import net.reichholf.dreamdroid.helpers.Statics;


/**
 * @author sre
 */

public abstract class BaseFragment extends Fragment implements ActivityCallbackHandler, IMutliPaneContent, IBaseFragment, ActionDialog.DialogActionListener {
	private FragmentHelper mHelper = null;
	protected boolean mShouldRetainInstance = true;
	protected boolean mHasFabReload = false;
	protected boolean mHasFabMain = false;

	public BaseFragment() {
		super();
		mHelper = new FragmentHelper();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bridge.restoreInstanceState(this, savedInstanceState);
		if (mHelper == null)
			mHelper = new FragmentHelper(this);
		else
			mHelper.bindToFragment(this);
		mHelper.onCreate(savedInstanceState);
		if (mShouldRetainInstance)
			setRetainInstance(true);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setFabEnabled(R.id.fab_reload, mHasFabReload);
		setFabEnabled(R.id.fab_main, mHasFabMain);
	}

	protected void setFabEnabled(int id, boolean enabled) {
		FloatingActionButton fab = getAppCompatActivity().findViewById(id);
		if(fab == null)
			return;
		fab.setTag(R.id.fab_scrolling_view_behavior_enabled, enabled);
		if(enabled) {
			fab.show();
		} else {
			fab.hide();
			((MainActivity)getAppCompatActivity()).unregisterFab(id);
		}
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
	public void onSaveInstanceState(@NonNull Bundle outState) {
		mHelper.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
		Bridge.saveInstanceState(this, outState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Bridge.clear(this);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		MultiPaneHandler mph = getMultiPaneHandler(); //TODO how do i reproduce this?
		if (mph == null || !mph.isDrawerOpen())
			createOptionsMenu(menu, inflater);
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
	}

	@Override
	public void onDrawerOpened() {
	}

	@Override
	public void onDrawerClosed() {
	}

	public boolean hasHeader () {
		return false;
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

	protected AppCompatActivity getAppCompatActivity() {
		return (AppCompatActivity) getActivity();
	}

	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	protected void registerFab(int id, int descriptionId, int backgroundResId, View.OnClickListener onClickListener) {
		FloatingActionButton fab = getAppCompatActivity().findViewById(id);
		if (fab == null)
			return;

		fab.setTag(R.id.fab_scrolling_view_behavior_enabled, true);
		fab.show();
		fab.setContentDescription(getString(descriptionId));
		fab.setImageResource(backgroundResId);
		fab.setOnClickListener(onClickListener);
		fab.setOnLongClickListener(v -> {
			Toast.makeText(getAppCompatActivity(), v.getContentDescription(), Toast.LENGTH_SHORT).show();
			return true;
		});
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
	}
}
