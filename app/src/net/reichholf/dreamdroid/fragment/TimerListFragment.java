/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.loader.content.Loader;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.TimerAdapter;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerCleanupRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.widget.helper.ItemSelectionSupport;

import java.util.ArrayList;

/**
 * Activity to show a List of all existing timers of the target device
 *
 * @author sreichholf
 */
public class TimerListFragment extends BaseHttpRecyclerFragment {
	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.timerlist_context, menu);
			mIsActionMode = true;
			mSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE);
			return true;
		}

		// Called each time the action mode is shown. Always called after onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			MenuItem toggle = menu.findItem(R.id.menu_toggle_enabled);
			if (mTimer.getString(Timer.KEY_DISABLED).equals("0"))
				toggle.setTitle(R.string.disable);
			else
				toggle.setTitle(R.string.enable);
			return true;
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			mode.finish(); // Action picked, so close the CAB
			return onItemSelected(item.getItemId());
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode) {

			mIsActionMode = false;
			if (mIsActionModeRequired)
				return;
			final RecyclerView rv = getRecyclerView();
			mSelectionSupport.setItemChecked(mSelectionSupport.getCheckedItemPosition(), false);
			getRecyclerView().post(() -> mSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE));
		}
	};
	@State public ExtendedHashMap mTimer;
	private ProgressDialog mProgress;
	protected int mCurrentPos;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		mEnableReload = true;
		mHasFabMain = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.timer));

		mCurrentPos = -1;
		mIsActionMode = false;
		mReload = true;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.card_recycler_content, container, false);
		registerFab(R.id.fab_main, R.string.new_timer, R.drawable.ic_action_fab_add, v -> onItemSelected(Statics.ITEM_NEW_TIMER));
		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setAdapter();
	}

	protected void startActionMode() {
		mTimer = mMapList.get(mCurrentPos);
		mActionMode = getAppCompatActivity().startSupportActionMode(mActionModeCallback);
		mSelectionSupport.setItemChecked(mCurrentPos, true);
	}

	@Override
	public void onDestroyView() {
		endActionMode();
		super.onDestroyView();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.support.v4.app.Fragment#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_EDIT_TIMER) {
			if (resultCode == Activity.RESULT_OK) {
				if (getAppCompatActivity() != null) // we're somewhere active!
					// reload();
					Log.w(DreamDroid.LOG_TAG, "TIMER SAVED!");
			}
		}
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.timerlist, menu);
	}

	@Override
	public boolean onItemSelected(int id) {
		switch (id) {
			case (Statics.ITEM_NEW_TIMER):
				mTimer = Timer.getInitialTimer();
				editTimer(mTimer, true);
				return true;
			case (Statics.ITEM_CLEANUP):
				cleanupTimerList();
				return true;
			case Statics.ITEM_TOGGLE_ENABLED:
				toggleTimerEnabled(mTimer);
				return true;
			case Statics.ITEM_DELETE:
				deleteTimerConfirm();
				return true;
			default:
				return super.onItemSelected(id);
		}
	}

	/**
	 * Open a <code>TimerEditActivity</code> for timer editing
	 *
	 * @param timer The timer to be edited
	 */
	private void editTimer(ExtendedHashMap timer, boolean create) {
		Timer.edit(getMultiPaneHandler(), timer, this, create);
	}

	/**
	 * Initializes the <code>SimpleTextAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new TimerAdapter(getAppCompatActivity(), mMapList);
		getRecyclerView().setAdapter(mAdapter);
	}

	/**
	 * Confirmation dialog before timer deletion
	 */
	private void deleteTimerConfirm() {
		PositiveNegativeDialog dia = PositiveNegativeDialog.newInstance(mTimer.getString(Timer.KEY_NAME),
				R.string.delete_confirm, android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
				Statics.ACTION_NONE);

		getMultiPaneHandler().showDialogFragment(dia, "dialog_delete_timer_confirm");
	}

	/**
	 * Delete a timer by creating an <code>DeleteTimerTask</code>
	 *
	 * @param timer The Timer to delete as <code>ExtendedHashMap</code>
	 */
	private void deleteTimer(ExtendedHashMap timer) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		ArrayList<NameValuePair> params = Timer.getDeleteParams(timer);
		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.deleting), true);
		execSimpleResultTask(new TimerDeleteRequestHandler(), params);
	}

	private void toggleTimerEnabled(ExtendedHashMap timer) {
		ExtendedHashMap timerNew = timer.clone();

		if (timerNew.getString(Timer.KEY_DISABLED).equals("1"))
			timerNew.put(Timer.KEY_DISABLED, "0");
		else
			timerNew.put(Timer.KEY_DISABLED, "1");

		ArrayList<NameValuePair> params = Timer.getSaveParams(timerNew, timer);
		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerChangeRequestHandler(), params);
	}

	/**
	 * CleanUp timer list by creating an <code>CleanupTimerListTask</code>
	 */
	private void cleanupTimerList() {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.cleaning_timerlist), true);
		execSimpleResultTask(new TimerCleanupRequestHandler(), new ArrayList<>());
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		reload();
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		return new AsyncListLoader(getAppCompatActivity(), new TimerListRequestHandler(), false, args);
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		switch (action) {
			case Statics.ACTION_EDIT:
				editTimer(mTimer, false);
				break;
			case Statics.ACTION_DELETE:
				deleteTimerConfirm();
				break;
			case Statics.ACTION_DELETE_CONFIRMED:
				deleteTimer(mTimer);
				break;
			default:
				break;
		}
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		mTimer = mMapList.get(position);
		if (mIsActionMode) {
			mSelectionSupport.setItemChecked(position, true);
			return;
		}
		editTimer(mTimer, false);
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, View view, int position, long id) {
		mTimer = mMapList.get(position);
		getAppCompatActivity().startSupportActionMode(mActionModeCallback);
		mSelectionSupport.setItemChecked(position, true);
		return true;
	}
}
