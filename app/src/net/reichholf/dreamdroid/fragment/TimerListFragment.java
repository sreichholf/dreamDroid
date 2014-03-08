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
import android.support.v4.content.Loader;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.TimerListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerCleanupRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;

import java.util.ArrayList;

/**
 * Activity to show a List of all existing timers of the target device
 *
 * @author sreichholf
 */
public class TimerListFragment extends AbstractHttpListFragment implements ActionDialog.DialogActionListener {
	protected boolean mIsActionMode;
	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.timerlist_context, menu);
			mIsActionMode = true;
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			return true;
		}

		// Called each time the action mode is shown. Always called after onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false; // Return false if nothing is done
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
				case R.id.menu_delete:
					deleteTimerConfirm();
					mode.finish(); // Action picked, so close the CAB
					return true;
				default:
					return false;
			}
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			final ListView lv = getListView();
			lv.setItemChecked(lv.getCheckedItemPosition(), false);
			getListView().post(new Runnable() {
				@Override
				public void run() {
					lv.setChoiceMode(ListView.CHOICE_MODE_NONE);
				}
			});
			mIsActionMode = false;
		}
	};
	private ExtendedHashMap mTimer;
	private ProgressDialog mProgress;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.timer));
		setAdapter();
		mIsActionMode = false;
		if (savedInstanceState != null) {
			mTimer = (ExtendedHashMap) savedInstanceState.getParcelable("timer");
		} else {
			mReload = true;
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				getActionBarActivity().startSupportActionMode(mActionModeCallback);
				mTimer = mMapList.get(position);
				getListView().setItemChecked(position, true);
				return true;
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("timer", mTimer);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mTimer = mMapList.get(position);
		if (mIsActionMode) {
			getListView().setItemChecked(position, true);
			return;
		}
		editTimer(mTimer, false);
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
				if (getActionBarActivity() != null) // we're somewhere active!
					// reload();
					Log.w(DreamDroid.LOG_TAG, "TIMER SAVED!");
			}
		}
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.reload, menu);
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
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new TimerListAdapter(getActionBarActivity(), R.layout.timer_list_item, mMapList);
		setListAdapter(mAdapter);
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
		mProgress = ProgressDialog.show(getActionBarActivity(), "", getText(R.string.deleting), true);
		execSimpleResultTask(new TimerDeleteRequestHandler(), params);
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

		mProgress = ProgressDialog.show(getActionBarActivity(), "", getText(R.string.cleaning_timerlist), true);
		execSimpleResultTask(new TimerCleanupRequestHandler(), new ArrayList<NameValuePair>());
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
			reload();
		}
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getActionBarActivity(), new TimerListRequestHandler(), false, args);
		return loader;
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
}
